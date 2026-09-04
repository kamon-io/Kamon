package kamon.instrumentation.akka

import com.typesafe.config.{Config, ConfigFactory}
import kamon.Kamon
import kamon.instrumentation.akka.AkkaInstrumentation.AskPatternTimeoutWarningSetting.{Heavyweight, Lightweight, Off}
import kamon.util.Filter

import scala.collection.JavaConverters.{asScalaBufferConverter, asScalaSetConverter}

object AkkaInstrumentation {

  val TrackActorFilterName = "kamon.instrumentation.akka.filters.actors.track"
  val TraceActorFilterName = "kamon.instrumentation.akka.filters.actors.trace"
  val StartTraceActorFilterName = "kamon.instrumentation.akka.filters.actors.start-trace"
  val TrackAutoGroupFilterName = "kamon.instrumentation.akka.filters.groups.auto-grouping"
  val TrackRouterFilterName = "kamon.instrumentation.akka.filters.routers"
  val TrackDispatcherFilterName = "kamon.instrumentation.akka.filters.dispatchers"

  @volatile private var _settings = Settings.from(Kamon.config())
  @volatile private var _actorGroups = Map.empty[String, Filter]
  @volatile private var _configProvidedActorGroups = Map.empty[String, Filter]
  @volatile private var _codeProvidedActorGroups = Map.empty[String, Filter]

  loadConfiguration(Kamon.config())
  Kamon.onReconfigure(loadConfiguration(_))

  /**
    * Returns the current Akka Instrumentation settings.
    */
  def settings(): AkkaInstrumentation.Settings =
    _settings

  /**
    * Returns all Actor Group names that should contain an actor with the provided path.
    */
  def matchingActorGroups(path: String): Seq[String] = {
    _actorGroups.filter { case (_, v) => v.accept(path) }.keys.toSeq
  }

  /**
    * Creates a new Actor Group definition. Take into account that Actors are added to Actor Groups during their
    * initialization process only, which means that a newly defined Actor Group will only include matching actors
    * created after the definition succeeded.
    *
    * Returns true if the definition was successful and false if a group with the defined name is already available.
    */
  def defineActorGroup(groupName: String, filter: Filter): Boolean = synchronized {
    if(_codeProvidedActorGroups.get(groupName).isEmpty) {
      _codeProvidedActorGroups = _codeProvidedActorGroups + (groupName -> filter)
      _actorGroups = _codeProvidedActorGroups ++ _configProvidedActorGroups
      true
    } else false
  }

  /**
    * Removes a programmatically created Actor Group definition. This method can only remove definitions that were
    * created via the "defineActorGroup" method.
    */
  def removeActorGroup(groupName: String): Unit = synchronized {
    _codeProvidedActorGroups = _codeProvidedActorGroups - groupName
    _actorGroups = _codeProvidedActorGroups ++ _configProvidedActorGroups
  }

  private def loadConfiguration(config: Config): Unit = synchronized {
    val akkaConfig = config.getConfig("kamon.instrumentation.akka")
    val groupsConfig = akkaConfig.getConfig("filters.groups")

    _configProvidedActorGroups = groupsConfig.root.entrySet().asScala
      .filter(_.getKey != "auto-grouping")
      .map(entry => {
        val groupName = entry.getKey
        groupName -> Filter.from(groupsConfig.getConfig(groupName))
      }).toMap

    _actorGroups = _codeProvidedActorGroups ++ _configProvidedActorGroups
    _settings = Settings.from(config)
  }

  /**
    * Akka Instrumentation settings
    */
  case class Settings (
    askPatternWarning: AskPatternTimeoutWarningSetting,
    autoGrouping: Boolean,
    allowDoomsdayWildcards: Boolean,
    safeActorTrackFilter: Filter,
    safeActorStartTraceFilter: Filter,
    exposeClusterMetrics: Boolean
  )

  object Settings {

    def from(config: Config): Settings = {
      val akkaConfig = config.getConfig("kamon.instrumentation.akka")
      val allowDoomsdayWildcards = akkaConfig.getBoolean("filters.actors.doomsday-wildcard")
      val exposeClusterMetrics = akkaConfig.getBoolean("cluster.track-cluster-metrics")

      val askPatternWarning = akkaConfig.getString("ask-pattern-timeout-warning") match {
        case "off"          => Off
        case "lightweight"  => Lightweight
        case "heavyweight"  => Heavyweight
        case other => sys.error(s"Unrecognized option [$other] for the kamon.akka.ask-pattern-timeout-warning config.")
      }

      AkkaInstrumentation.Settings(
        askPatternWarning,
        akkaConfig.getBoolean("auto-grouping"),
        allowDoomsdayWildcards,
        safeFilter(config.getConfig(TrackActorFilterName), allowDoomsdayWildcards),
        safeFilter(config.getConfig(StartTraceActorFilterName), allowDoomsdayWildcards),
        exposeClusterMetrics
      )
    }

    private def safeFilter(config: Config, allowDoomsday: Boolean): Filter = {
      val includes = config.getStringList("includes").asScala
      if(!allowDoomsday && includes.contains("**")) {
        val newIncludes = "includes = " + includes.filterNot(_ == "**").map(s => s""""$s"""").mkString("[ ", ", ", " ]")
        val safeFilterConfig = ConfigFactory.parseString(newIncludes).withFallback(config)

        Filter.from(safeFilterConfig)

      } else Filter.from(config)
    }
  }

  sealed trait AskPatternTimeoutWarningSetting
  object AskPatternTimeoutWarningSetting {
    case object Off extends AskPatternTimeoutWarningSetting
    case object Lightweight extends AskPatternTimeoutWarningSetting
    case object Heavyweight extends AskPatternTimeoutWarningSetting
  }
}
