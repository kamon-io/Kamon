package kamon

import com.typesafe.config.Config
import kamon.context.HttpPropagation

trait ContextPropagation { self: Configuration with ClassLoading =>
  @volatile private var _propagationComponents: ContextPropagation.Components = _
  @volatile private var _defaultHttpPropagation: HttpPropagation = _

  // Initial configuration and reconfigures
  init(self.config)
  self.onReconfigure(newConfig => self.init(newConfig))


  /**
    * Retrieves the HTTP propagation channel with the supplied name. Propagation channels are configured on the
    * kamon.propagation.channels configuration setting.
    *
    * @param channelName Channel name to retrieve.
    * @return The HTTP propagation, if defined.
    */
  def httpPropagation(channelName: String): Option[HttpPropagation] =
    _propagationComponents.httpChannels.get(channelName)

  /**
    * Retrieves the default HTTP propagation channel. Configuration for this channel can be found under the
    * kamon.propagation.channels.http configuration setting.
    *
    * @return The default HTTP propagation.
    */
  def defaultHttpPropagation(): HttpPropagation =
    _defaultHttpPropagation



  private def init(config: Config): Unit = synchronized {
    _propagationComponents = ContextPropagation.Components.from(self.config, self)
    _defaultHttpPropagation = _propagationComponents.httpChannels(ContextPropagation.DefaultHttpChannel)
  }
}

object ContextPropagation {
  val DefaultHttpChannel = "default"
  val DefaultBinaryChannel = "default"

  case class Components(
    httpChannels: Map[String, HttpPropagation]
  )

  object Components {

    def from(config: Config, classLoading: ClassLoading): Components = {
      val propagationConfig = config.getConfig("kamon.propagation")
      val httpChannelsConfig = propagationConfig.getConfig("http").configurations
      val httpChannels = httpChannelsConfig.map {
        case (channelName, channelConfig) => (channelName -> HttpPropagation.from(channelConfig, classLoading))
      }

      Components(httpChannels)
    }
  }
}
