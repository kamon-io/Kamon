package kamon
package module

import java.time.{Duration, Instant}
import java.util.concurrent.{CountDownLatch, Executors, ScheduledFuture, TimeUnit}
import java.util.concurrent.atomic.AtomicReference

import com.typesafe.config.Config
import kamon.metric.{MetricsSnapshotGenerator, PeriodSnapshot}
import kamon.module.Module.Registration
import kamon.status.Status
import kamon.trace.Tracer.SpanBuffer
import kamon.util.Clock
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future, Promise}
import scala.util.Try
import scala.util.control.NonFatal



/**
  * Controls the lifecycle of all available modules.
  */
class ModuleRegistry(classLoading: ClassLoading, configuration: Configuration, clock: Clock, snapshotGenerator: MetricsSnapshotGenerator, spanBuffer: SpanBuffer) {
  private val _logger = LoggerFactory.getLogger(classOf[ModuleRegistry])
  private val _metricsTickerExecutor = Executors.newScheduledThreadPool(1, threadFactory("kamon-metrics-ticker", daemon = true))
  private val _spansTickerExecutor = Executors.newScheduledThreadPool(1, threadFactory("kamon-spans-ticker", daemon = true))

  private val _metricsTickerSchedule = new AtomicReference[ScheduledFuture[_]]()
  private val _spansTickerSchedule = new AtomicReference[ScheduledFuture[_]]()

  private var _registrySettings = readRegistrySettings(configuration.config())
  private var _registeredModules: Map[String, Entry[Module]] = Map.empty
  private var _metricReporterModules: Map[String, Entry[MetricReporter]] = Map.empty
  private var _spanReporterModules: Map[String, Entry[SpanReporter]] = Map.empty

  // Start ticking as soon as the registry is created.
  scheduleMetricsTicker()
  scheduleSpansTicker()


  /**
    * Registers a module that has already been instantiated by the user. The start callback will be executed as part
    * of the registration process. If a module with the specified name already exists the registration will fail. If
    * the registered module is a MetricReporter and/or SpanReporter it will also be configured to receive the metrics
    * and spans data upon every tick.
    *
    * @param name Desired module name.
    * @param module Module instance.
    * @return A registration that can be used to stop the module at any time.
    */
  def register(name: String, module: Module): Registration = synchronized {
    if(_registeredModules.get(name).isEmpty) {
      val inferredSettings = Module.Settings(
        name,
        module.getClass.getName,
        module.getClass,
        inferModuleKind(module.getClass),
        true
      )

      val moduleEntry = createEntry(inferredSettings, true, module)
      startModule(moduleEntry)
      registration(moduleEntry)

    } else {
      _logger.warn(s"Cannot register module [$name], a module with that name already exists.")
      noopRegistration(name)
    }
  }

  /**
    * Reads all available modules from the config and either starts, stops or reconfigures them in order to match the
    * configured modules state.
    */
  def load(config: Config): Unit = synchronized {
    val configuredModules = readModuleSettings(config)
    val automaticallyRegisteredModules = _registeredModules.filterNot { case (_, module) => module.programmaticallyAdded }

    // Start, reconfigure and stop modules that are still present but disabled.
    configuredModules.foreach { moduleSettings =>
      automaticallyRegisteredModules.get(moduleSettings.name).fold {
        // The module does not exist in the registry, the only possible action is starting it, if enabled.
        if(moduleSettings.enabled) {
          createModule(moduleSettings).foreach(entry => startModule(entry))
        }

      } { existentModuleSettings =>
        // When a module already exists it can either need to be stopped, or to be reconfigured.
        if(moduleSettings.enabled) {
          reconfigureModule(existentModuleSettings, config)
        } else {
          stopModule(existentModuleSettings)
        }
      }
    }

    // Remove all modules that no longer exist in the configuration.
    val missingModules = automaticallyRegisteredModules.filterKeys(moduleName => configuredModules.find(_.name == moduleName).isEmpty)
    missingModules.foreach {
      case (_, entry) => stopModule(entry)
    }
  }

  /**
    * Schedules the reconfigure hook on all registered modules and applies the latest configuration settings to the
    * registry.
    */
  def reconfigure(newConfig: Config): Unit = synchronized {
    _registrySettings = readRegistrySettings(configuration.config())
    _registeredModules.values.foreach(entry => reconfigureModule(entry, newConfig))
    scheduleMetricsTicker()
    scheduleSpansTicker()
  }

  /**
    * Stops all registered modules. As part of the stop process, all modules get a last chance to report metrics and
    * spans available until the call to stop.
    */
  def stop(): Future[Unit] = synchronized {
    implicit val cleanupExecutor = ExecutionContext.Implicits.global
    scheduleMetricsTicker(once = true)
    scheduleSpansTicker(once = true)

    val stopSignals =  _registeredModules.values.map(stopModule)
    val latch = new CountDownLatch(stopSignals.size)
    stopSignals.foreach(f => f.onComplete(_ => latch.countDown()))

    // There is a global 30 seconds limit to shutdown after which all executors will shut down.
    val stopCompletionFuture = Future(latch.await(30, TimeUnit.SECONDS))
    stopCompletionFuture.onComplete(_ => {
      _metricsTickerExecutor.shutdown()
      _spansTickerExecutor.shutdown()
    })

    stopCompletionFuture.map(_ => ())
  }


  /**
    * (Re)Schedules the metrics ticker that periodically takes snapshots from the metric registry and sends them to
    * all available metric reporting modules. If a ticker was already scheduled then that scheduled job will be
    * cancelled and scheduled again.
    */
  private def scheduleMetricsTicker(once: Boolean = false): Unit = {
    val currentMetricsTicker = _metricsTickerSchedule.get()
    if(currentMetricsTicker != null)
      currentMetricsTicker.cancel(false)

    _metricsTickerSchedule.set {
      val interval = _registrySettings.metricTickInterval.toMillis
      val initialDelay = if(_registrySettings.optimisticMetricTickAlignment) {
        val now = clock.instant()
        val nextTick = Clock.nextTick(now, _registrySettings.metricTickInterval)
        Duration.between(now, nextTick).toMillis
      } else _registrySettings.metricTickInterval.toMillis

      val ticker = new Runnable {
        var lastInstant = Instant.now(clock)

        override def run(): Unit = try {
          val currentInstant = Instant.now(clock)
          val periodSnapshot = PeriodSnapshot(
            from = lastInstant,
            to = currentInstant,
            metrics = snapshotGenerator.snapshot())

          metricReporterModules().foreach { entry =>
            Future {
              Try(entry.module.reportPeriodSnapshot(periodSnapshot)).failed.foreach { error =>
                _logger.error(s"Reporter [${entry.name}] failed to process a metrics tick.", error)
              }
            }(entry.executionContext)
          }

          lastInstant = currentInstant
        } catch {
          case NonFatal(t) => _logger.error("Failed to run a metrics tick", t)
        }
      }

      if(once)
        _metricsTickerExecutor.schedule(ticker, 0L, TimeUnit.MILLISECONDS)
      else
        _metricsTickerExecutor.scheduleAtFixedRate(ticker, initialDelay, interval, TimeUnit.MILLISECONDS)
    }
  }

  /**
    * (Re)Schedules the spans ticker that periodically takes the spans accumulated by the tracer and flushes them to
    * all available span reporting modules. If a ticker was already scheduled then that scheduled job will be
    * cancelled and scheduled again.
    */
  private def scheduleSpansTicker(once: Boolean = false): Unit = {
    val currentSpansTicker = _spansTickerSchedule.get()
    if(currentSpansTicker != null)
      currentSpansTicker.cancel(false)

    _spansTickerSchedule.set {
      val interval = _registrySettings.traceTickInterval.toMillis

      val ticker = new Runnable {
        override def run(): Unit = try {
          val spanBatch = spanBuffer.flush()

          spanReporterModules().foreach { entry =>
            Future {
              Try(entry.module.reportSpans(spanBatch)).failed.foreach { error =>
                _logger.error(s"Reporter [${entry.name}] failed to process a spans tick.", error)
              }
            }(entry.executionContext)
          }

        } catch {
          case NonFatal(t) => _logger.error("Failed to run a spans tick", t)
        }
      }

      if(once)
        _spansTickerExecutor.schedule(ticker, 0L, TimeUnit.MILLISECONDS)
      else
        _spansTickerExecutor.scheduleAtFixedRate(ticker, interval, interval, TimeUnit.MILLISECONDS)
    }
  }

  private def metricReporterModules(): Iterable[Entry[MetricReporter]] = synchronized {
    _metricReporterModules.values
  }

  private def spanReporterModules(): Iterable[Entry[SpanReporter]] = synchronized {
    _spanReporterModules.values
  }

  private def readModuleSettings(config: Config): Seq[Module.Settings] = {
    val moduleConfigs = config.getConfig("kamon.modules").configurations
    val moduleSettings = moduleConfigs.map {
      case (moduleName, moduleConfig) =>
        val moduleSettings = Try {
          Module.Settings(
            moduleName,
            moduleConfig.getString("description"),
            classLoading.resolveClass[Module](moduleConfig.getString("class")).get,
            parseModuleKind(moduleConfig.getString("kind")),
            moduleConfig.getBoolean("enabled")
          )
        }.map(ms => {
          val inferredModuleKind = inferModuleKind(ms.clazz)
          assert(inferredModuleKind == ms.kind,
            s"Module [${ms.name}] is configured as [${ms.kind}] but the actual type does not comply to the expected interface.")
          ms
        })


        moduleSettings.failed.foreach { t =>
          _logger.warn(s"Failed to read configuration for module [$moduleName]", t)

          if(moduleConfig.hasPath("requires-aspectj") || moduleConfig.hasPath("auto-start") || moduleConfig.hasPath("extension-class")) {
            _logger.warn(s"Module [$moduleName] contains legacy configuration settings, please ensure that no legacy configuration")
          }
        }

        moduleSettings

    } filter(_.isSuccess) map(_.get) toSeq


    // Load all modules that might have been configured using the legacy "kamon.reporters" setting from <1.2.0
    // versions. This little hack should be removed by the time we release 2.0.
    //
    if(config.hasPath("kamon.reporters")) {
      val legacyModuleSettings = config.getStringList("kamon.reporters").asScala
        .map(moduleClass => {
          val moduleSettings = Try {
            val moduleClazz = classLoading.resolveClass[Module](moduleClass).get
            val inferredModuleKind = inferModuleKind(moduleClazz)
            val name = moduleClazz.getName()
            val description = "Module detected from the legacy kamon.reporters configuration."

            Module.Settings(name, description, moduleClazz, inferredModuleKind, true)
          }

          moduleSettings.failed.foreach(t => _logger.error(s"Failed to load legacy reporter module [${moduleClass}]", t))
          moduleSettings
        })
        .filter(_.isSuccess)
        .map(_.get)


      val (repeatedLegacyModules, uniqueLegacyModules) = legacyModuleSettings
        .partition(lm => moduleSettings.find(_.clazz.getName == lm.clazz.getName).nonEmpty)

      repeatedLegacyModules.foreach(m =>
        _logger.warn(s"Module [${m.name}] is configured twice, please remove it from the deprecated kamon.reporters setting."))

      uniqueLegacyModules.foreach(m =>
        _logger.warn(s"Module [${m.name}] is configured in the deprecated kamon.reporters setting, please consider moving it to kamon.modules."))

      moduleSettings ++ uniqueLegacyModules

    } else moduleSettings
  }

  /**
    * Creates a module from the provided settings.
    */
  private def createModule(settings: Module.Settings): Option[Entry[Module]] = {
    val moduleInstance = classLoading.createInstance[Module](settings.clazz, Nil)
    val moduleEntry = moduleInstance.map(instance => createEntry(settings, false, instance))

    moduleEntry.failed.foreach(t => _logger.warn(s"Failed to create instance of module [${settings.name}]", t))
    moduleEntry.toOption
  }

  private def createEntry(settings: Module.Settings, programmaticallyAdded: Boolean, instance: Module): Entry[Module] = {
    val executor = Executors.newSingleThreadExecutor(threadFactory(settings.name))
    Entry(settings.name, ExecutionContext.fromExecutorService(executor), programmaticallyAdded, settings, instance)
  }

  private def inferModuleKind(clazz: Class[_ <: Module]): Module.Kind = {
    if(classOf[CombinedReporter].isAssignableFrom(clazz))
      Module.Kind.Combined
    else if(classOf[MetricReporter].isAssignableFrom(clazz))
      Module.Kind.Metric
    else if(classOf[SpanReporter].isAssignableFrom(clazz))
      Module.Kind.Span
    else
      Module.Kind.Plain
  }


  /**
    * Returns the current status of this module registry.
    */
  private[kamon] def status(): Status.ModuleRegistry = {
    val automaticallyAddedModules = readModuleSettings(configuration.config()).map(moduleSettings => {
      val isActive = _registeredModules.get(moduleSettings.name).nonEmpty

      Status.Module(
        moduleSettings.name,
        moduleSettings.description,
        moduleSettings.clazz.getCanonicalName,
        moduleSettings.kind,
        isProgrammaticallyRegistered = false,
        moduleSettings.enabled,
        isActive)
    })

    val programmaticallyAddedModules = _registeredModules
      .filter { case (_, entry) => entry.programmaticallyAdded }
      .map { case (name, entry) => Status.Module(name, entry.settings.description, entry.settings.clazz.getCanonicalName,
        entry.settings.kind, true, true, true) }

    val allModules = automaticallyAddedModules ++ programmaticallyAddedModules
    Status.ModuleRegistry(allModules)
  }


  /**
    * Registers a module and schedules execution of its start procedure.
    */
  private def startModule(entry: Entry[Module]): Unit = {
    registerModule(entry)

    // Schedule the start hook on the module
    entry.executionContext.execute(new Runnable {
      override def run(): Unit =
        Try(entry.module.start())
          .failed.foreach(t => _logger.warn(s"Failure occurred while starting module [${entry.name}]", t))
    })
  }

  private def registerModule(entry: Entry[Module]): Unit = {
    _registeredModules = _registeredModules + (entry.name -> entry)
    if(entry.module.isInstanceOf[MetricReporter])
      _metricReporterModules = _metricReporterModules + (entry.name -> entry.asInstanceOf[Entry[MetricReporter]])
    if(entry.module.isInstanceOf[SpanReporter])
      _spanReporterModules = _spanReporterModules + (entry.name -> entry.asInstanceOf[Entry[SpanReporter]])

  }

  /**
    * Removes the module from the registry and schedules a call to the stop lifecycle hook on the module's execution
    * context. The returned future completes when the module finishes its stop procedure.
    */
  private def stopModule(entry: Entry[Module]): Future[Unit] = synchronized {
    val cleanupExecutor = ExecutionContext.Implicits.global

    // Remove the module from all registries
    _registeredModules = _registeredModules - entry.name
    if(entry.module.isInstanceOf[MetricReporter])
      _metricReporterModules = _metricReporterModules - entry.name
    if(entry.module.isInstanceOf[SpanReporter])
      _spanReporterModules = _spanReporterModules - entry.name


    // Schedule a call to stop on the module
    val stopPromise = Promise[Unit]()
    entry.executionContext.execute(new Runnable {
      override def run(): Unit =
        stopPromise.complete {
          val stopResult = Try(entry.module.stop())
          stopResult.failed.foreach(t => _logger.warn(s"Failure occurred while stopping module [${entry.name}]", t))
          stopResult
        }

    })

    stopPromise.future.onComplete(_ => entry.executionContext.shutdown())(cleanupExecutor)
    stopPromise.future
  }

  /**
    * Schedules a call to reconfigure on the module's execution context.
    */
  private def reconfigureModule(entry: Entry[Module], config: Config): Unit = {
    entry.executionContext.execute(new Runnable {
      override def run(): Unit =
        Try(entry.module.reconfigure(config))
          .failed.foreach(t => _logger.warn(s"Failure occurred while reconfiguring module [${entry.name}]", t))
    })
  }

  private def noopRegistration(moduleName: String): Registration = new Registration {
    override def cancel(): Unit =
      _logger.warn(s"Cannot cancel registration on module [$moduleName] because the module was not added properly")
  }

  private def registration(entry: Entry[Module]): Registration = new Registration {
    override def cancel(): Unit = stopModule(entry)
  }

  private def parseModuleKind(kind: String): Module.Kind = kind.toLowerCase match {
    case "combined" => Module.Kind.Combined
    case "metric"   => Module.Kind.Metric
    case "span"     => Module.Kind.Span
    case "plain"    => Module.Kind.Plain
  }

  private def readRegistrySettings(config: Config): Settings =
    Settings(
      metricTickInterval = config.getDuration("kamon.metric.tick-interval"),
      optimisticMetricTickAlignment = config.getBoolean("kamon.metric.optimistic-tick-alignment"),
      traceTickInterval = config.getDuration("kamon.trace.tick-interval"),
      traceReporterQueueSize = config.getInt("kamon.trace.reporter-queue-size")
    )

  private case class Settings(
    metricTickInterval: Duration,
    optimisticMetricTickAlignment: Boolean,
    traceTickInterval: Duration,
    traceReporterQueueSize: Int
  )


  private case class Entry[T <: Module](
    name: String,
    executionContext: ExecutionContextExecutorService,
    programmaticallyAdded: Boolean,
    settings: Module.Settings,
    module: T
  )
}

