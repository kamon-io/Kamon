/*
 * Copyright 2013-2020 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon
package module

import java.time.{Duration, Instant}
import java.util.concurrent.{CountDownLatch, Executors, ScheduledFuture, TimeUnit}
import java.util.concurrent.atomic.AtomicReference

import com.typesafe.config.Config
import kamon.module.Module.Registration
import kamon.status.Status
import kamon.metric.{MetricRegistry, PeriodSnapshot}
import kamon.trace.{Span, Tracer}
import kamon.util.Clock
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future, Promise}
import scala.util.Try
import scala.util.control.NonFatal

/**
  * Controls the lifecycle of all available modules.
  */
class ModuleRegistry(configuration: Configuration, clock: Clock, metricRegistry: MetricRegistry, tracer: Tracer) {

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
    * Registers a module that has created programmatically. If a module with the specified name already exists the
    * registration will fail. If the registered module is a MetricReporter and/or SpanReporter it will also be
    * registered to receive the metrics and/or spans data upon every tick.
    */
  def register(name: String, description: Option[String], module: Module): Registration = synchronized {
    if(_registeredModules.get(name).isEmpty) {
      val inferredSettings = Module.Settings(
        name,
        description.getOrElse(module.getClass.getName),
        true,
        factory = None
      )

      val moduleEntry = Entry(name, createExecutor(inferredSettings), true, inferredSettings, module)
      registerModule(moduleEntry)
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
    val configuredModules = readModuleSettings(config, true)
    val automaticallyRegisteredModules = _registeredModules.filterNot { case (_, module) => module.programmaticallyAdded }

    // Start, reconfigure and stop modules that are still present but disabled.
    configuredModules.foreach { moduleSettings =>
      automaticallyRegisteredModules.get(moduleSettings.name).fold {
        // The module does not exist in the registry, the only possible action is starting it, if enabled.
        if(moduleSettings.enabled) {
          createModule(moduleSettings, false).foreach(entry => registerModule(entry))
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
    val missingModules = automaticallyRegisteredModules.filterKeys(moduleName => !configuredModules.exists(_.name == moduleName))
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
  def stopModules(): Future[Unit] = synchronized {
    implicit val cleanupExecutor = ExecutionContext.Implicits.global
    stopReporterTickers()

    var stoppedSignals: List[Future[Unit]] = Nil
    _registeredModules.dropWhile {
      case (_, entry) =>
        stoppedSignals = stopModule(entry) :: stoppedSignals
        true
    }

    val latch = new CountDownLatch(stoppedSignals.size)
    stoppedSignals.foreach(f => f.onComplete(_ => latch.countDown()))

    // TODO: Completely destroy modules that fail to stop within the 30 second timeout.
    Future(latch.await(30, TimeUnit.SECONDS))
  }


  /**
    * (Re)Schedules the metrics ticker that periodically takes snapshots from the metric registry and sends them to
    * all available metric reporting modules. If a ticker was already scheduled then that scheduled job will be
    * cancelled and scheduled again.
    */
  private def scheduleMetricsTicker(): Unit = {
    val currentMetricsTicker = _metricsTickerSchedule.get()
    if(currentMetricsTicker != null)
      currentMetricsTicker.cancel(false)

    _metricsTickerSchedule.set {
      val interval = _registrySettings.metricTickInterval.toMillis
      val initialDelay = if(_registrySettings.optimisticMetricTickAlignment) {
        val now = clock.instant()
        val nextTick = Clock.nextAlignedInstant(now, _registrySettings.metricTickInterval)
        Duration.between(now, nextTick).toMillis
      } else _registrySettings.metricTickInterval.toMillis

      val ticker = new Runnable {
        var lastInstant = Instant.now(clock)

        override def run(): Unit = try {
          val currentInstant = Instant.now(clock)
          val periodSnapshot = metricRegistry.snapshot(resetState = true)

          metricReporterModules().foreach(entry => scheduleMetricsTick(entry, periodSnapshot))
          lastInstant = currentInstant
        } catch {
          case NonFatal(t) => _logger.error("Failed to run a metrics tick", t)
        }
      }

      _metricsTickerExecutor.scheduleAtFixedRate(ticker, initialDelay, interval, TimeUnit.MILLISECONDS)
    }
  }

  /**
    * (Re)Schedules the spans ticker that periodically takes the spans accumulated by the tracer and flushes them to
    * all available span reporting modules. If a ticker was already scheduled then that scheduled job will be
    * cancelled and scheduled again.
    */
  private def scheduleSpansTicker(): Unit = {
    val currentSpansTicker = _spansTickerSchedule.get()
    if(currentSpansTicker != null)
      currentSpansTicker.cancel(false)

    _spansTickerSchedule.set {
      val interval = _registrySettings.traceTickInterval.toMillis

      val ticker = new Runnable {
        override def run(): Unit = try {
          val spanBatch = tracer.spans()
          spanReporterModules().foreach(entry => scheduleSpansBatch(entry, spanBatch))

        } catch {
          case NonFatal(t) => _logger.error("Failed to run a spans tick", t)
        }
      }

      _spansTickerExecutor.scheduleAtFixedRate(ticker, interval, interval, TimeUnit.MILLISECONDS)
    }
  }

  private def scheduleMetricsTick(entry: Entry[MetricReporter], periodSnapshot: PeriodSnapshot): Unit = {
    Future {
      try entry.module.reportPeriodSnapshot(periodSnapshot) catch { case error: Throwable =>
        _logger.error(s"Reporter [${entry.name}] failed to process a metrics tick.", error)
      }
    }(entry.executionContext)
  }

  private def scheduleSpansBatch(entry: Entry[SpanReporter], spanBatch: Seq[Span.Finished]): Unit = {
    Future {
      try entry.module.reportSpans(spanBatch) catch { case error: Throwable =>
        _logger.error(s"Reporter [${entry.name}] failed to process a spans tick.", error)
      }
    }(entry.executionContext)
  }

  private def stopReporterTickers(): Unit = {
    val currentMetricsTicker = _metricsTickerSchedule.get()
    if(currentMetricsTicker != null)
      currentMetricsTicker.cancel(false)

    val currentSpansTicker = _spansTickerSchedule.get()
    if(currentSpansTicker != null)
      currentSpansTicker.cancel(false)
  }

  private def metricReporterModules(): Iterable[Entry[MetricReporter]] = synchronized {
    _metricReporterModules.values
  }

  private def spanReporterModules(): Iterable[Entry[SpanReporter]] = synchronized {
    _spanReporterModules.values
  }

  private def readModuleSettings(config: Config, emitConfigurationWarnings: Boolean): Seq[Module.Settings] = {
    val moduleConfigs = config.getConfig("kamon.modules").configurations

    moduleConfigs.map {
      case (modulePath, moduleConfig) =>
        val moduleSettings = Try {
          Module.Settings(
            moduleConfig.getString("name"),
            moduleConfig.getString("description"),
            moduleConfig.getBoolean("enabled"),
            Option(moduleConfig.getString("factory"))
          )
        }

        if(emitConfigurationWarnings) {
          moduleSettings.failed.foreach { t =>
            _logger.warn(s"Failed to read configuration for module [$modulePath]", t)

            val hasLegacySettings =
              moduleConfig.hasPath("requires-aspectj") ||
              moduleConfig.hasPath("auto-start") ||
              moduleConfig.hasPath("extension-class")

            if (hasLegacySettings) {
              _logger.warn(s"Module [$modulePath] contains legacy configuration settings, please ensure that no legacy configuration")
            }
          }
        }

        moduleSettings

    } filter(_.isSuccess) map(_.get) toSeq
  }

  /**
    * Creates a module from the provided settings.
    */
  private def createModule(settings: Module.Settings, programmaticallyAdded: Boolean): Option[Entry[Module]] = {
    val moduleEC = createExecutor(settings)

    try {
      val factory = ClassLoading.createInstance[ModuleFactory](settings.factory.get, Nil)
      val instance = factory.create(ModuleFactory.Settings(Kamon.config(), moduleEC))

      Some(Entry(settings.name, moduleEC, programmaticallyAdded, settings, instance))

    } catch {
      case t: Throwable =>
        moduleEC.shutdown()
        _logger.warn(s"Failed to create instance of module [${settings.name}]", t)
        None
    }
  }

  private def createExecutor(settings: Module.Settings): ExecutionContextExecutorService = {
    val executor = Executors.newSingleThreadExecutor(threadFactory(settings.name))

    // Scheduling any task on the executor ensures that the underlying Thread is created and that the JVM will stay
    // alive until the modules are stopped.
    executor.submit(new Runnable {
      override def run(): Unit = {}
    })

    ExecutionContext.fromExecutorService(executor)
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
    val automaticallyAddedModules = readModuleSettings(configuration.config(), false).map(moduleSettings => {
      val instance = _registeredModules.get(moduleSettings.name)
      val isActive = instance.nonEmpty
      val className = instance.map(_.module.getClass.getCanonicalName).getOrElse("unknown")
      val moduleKind = instance.map(i => inferModuleKind(i.module.getClass)).getOrElse(Module.Kind.Unknown)

      Status.Module(
        moduleSettings.name,
        moduleSettings.description,
        className,
        moduleKind,
        programmaticallyRegistered = false,
        moduleSettings.enabled,
        isActive)
    })

    val programmaticallyAddedModules = _registeredModules
      .collect {
        case (name, entry) if entry.programmaticallyAdded =>
          val className = entry.module.getClass.getCanonicalName
          val moduleKind = inferModuleKind(entry.module.getClass)

          Status.Module(name, entry.settings.description, className, moduleKind, true, true, true)
      }

    val allModules = automaticallyAddedModules ++ programmaticallyAddedModules
    Status.ModuleRegistry(allModules)
  }

  /**
    * Adds a module to the registry and to metric and/or span reporting.
    */
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
    if(entry.module.isInstanceOf[MetricReporter]) {
      _metricReporterModules = _metricReporterModules - entry.name
      scheduleMetricsTick(entry.asInstanceOf[Entry[MetricReporter]], metricRegistry.snapshot(resetState = false))
    }
    if(entry.module.isInstanceOf[SpanReporter]) {
      _spanReporterModules = _spanReporterModules - entry.name
      scheduleSpansBatch(entry.asInstanceOf[Entry[SpanReporter]], tracer.spans())
    }


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
        Try {
          entry.module.reconfigure(config)
        }.failed.foreach(t => _logger.warn(s"Failure occurred while reconfiguring module [${entry.name}]", t))
    })
  }

  private def noopRegistration(moduleName: String): Registration = new Registration {
    override def cancel(): Unit =
      _logger.warn(s"Cannot cancel registration on module [$moduleName] because the module was not added properly")
  }

  private def registration(entry: Entry[Module]): Registration = new Registration {
    override def cancel(): Unit = stopModule(entry)
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

