<template>
  <div class="container">
    <div class="row">
      <div class="col-12 pt-4 pb-2">
        <h3>Overview</h3>
      </div>
      <div class="col-12">
        <overview-card :module-registry="moduleRegistry" :metric-registry="metricRegistry" :instrumentation="instrumentation"/>
      </div>

      <div class="col-12 pt-4 pb-2">
        <h3>Environment</h3>
      </div>
      <div class="col-12">
        <environment-card :environment="environment"/>
      </div>

      <div class="col-12">
        <module-list :modules="modules"/>
      </div>

      <div class="col-12 pt-4 pb-2" v-if="metrics.length > 0">
        <h2>Metrics</h2>
      </div>
      <div class="col-12">
        <metric-list :metrics="metrics"/>
      </div>
      <div class="col-12 mb-5">
        <instrumentation-module-list :modules="instrumentationModules"/>
      </div>

    </div>
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import {Option, none, some} from 'ts-option'
import ModuleList from '../components/ModuleList.vue'
import InstrumentationModuleList from '../components/InstrumentationModuleList.vue'
import MetricList from '../components/MetricList.vue'
import EnvironmentCard from '../components/EnvironmentCard.vue'
import OverviewCard from '../components/OverviewCard.vue'
import {StatusApi, Settings, ModuleRegistry, ModuleKind, MetricRegistry, Module, Metric,
  Instrumentation, Environment, InstrumentationModule} from '../api/StatusApi'

@Component({
  components: {
    'overview-card': OverviewCard,
    'module-list': ModuleList,
    'instrumentation-module-list': InstrumentationModuleList,
    'metric-list': MetricList,
    'environment-card': EnvironmentCard
  },
})
export default class Overview extends Vue {
  private settings: Option<Settings> = none
  private moduleRegistry: Option<ModuleRegistry> = none
  private metricRegistry: Option<MetricRegistry> = none
  private instrumentation: Option<Instrumentation> = none

  get reporterModules(): Module[] {
    return this.moduleRegistry
      .map(moduleRegistry => moduleRegistry.modules.filter(this.isReporter))
      .getOrElse([])
  }

  get activeReporters(): Module[] {
    return this.reporterModules.filter(this.isStarted)
  }

  get plainModules(): Module[] {
    return this.moduleRegistry
      .map(moduleRegistry => moduleRegistry.modules.filter(m => !this.isReporter(m)))
      .getOrElse([])
  }

  get trackedMetrics(): Option<number> {
    return this.metricRegistry.map(metricRegistry => metricRegistry.metrics.length)
  }

  get instrumentationStatusMessage(): string {
    return this.instrumentation.map(i => (i.isActive ? 'Active' : 'Disabled') as string).getOrElse('Unknown')
  }

  get metricsStatusMessage(): string {
    return this.trackedMetrics.map(mc => mc + ' Tracked').getOrElse('Unknown')
  }

  get metrics(): Metric[] {
    return this.metricRegistry
      .map(mr => mr.metrics)
      .getOrElse([])
  }

  get modules(): Module[] {
    return this.moduleRegistry
      .map(mr => mr.modules)
      .getOrElse([])
  }

  get instrumentationModules(): InstrumentationModule[] {
    return this.instrumentation
      .map(i => i.modules)
      .getOrElse([])
  }

  get environment(): Option<Environment> {
    return this.settings.map(s => s.environment)
  }

  public mounted() {
    this.refreshData()
  }

  private refreshData(): void {
    StatusApi.settings().then(settings => { this.settings = some(settings) })
    StatusApi.metricRegistryStatus().then(metricRegistry => { this.metricRegistry = some(metricRegistry) })
    StatusApi.moduleRegistryStatus().then(moduleRegistry => {this.moduleRegistry = some(moduleRegistry) })
    StatusApi.instrumentationStatus().then(instrumentation => {this.instrumentation = some(instrumentation) })
  }

  private isReporter(module: Module): boolean {
    return [ModuleKind.Combined, ModuleKind.Span, ModuleKind.Metric].indexOf(module.kind) > 0
  }

  private isStarted(module: Module): boolean {
    return module.started
  }
}
</script>
