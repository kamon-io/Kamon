<template>
  <v-container class="position-relative" style="width: 1080px">
    <overview-card
      class="overview-card"
      :module-registry="moduleRegistry"
      :metric-registry="metricRegistry"
      :instrumentation="instrumentation"
      @click:instrumentation="goToInstrumentation"
      @click:reporters="goToReporters"
      @click:metrics="goToMetrics"
    />

    <v-row>
      <v-col class="mt-12" cols="12">
        <environment-card :environment="environment"/>
      </v-col>

      <v-col cols="12" class="js-reporters">
        <module-list :config="config" :modules="modules" @show:api-key="showApmApiKey" />
      </v-col>

      <v-col cols="12" class="js-instrumentation">
        <instrumentation-module-list :modules="instrumentationModules"/>
      </v-col>

      <v-col cols="12" class="mb-5 js-metrics" v-if="metrics.length > 0">
        <metric-list :metrics="metrics"/>
      </v-col>

    </v-row>
  </v-container>
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
    return this.instrumentation.map(i => (i.present ? 'Active' : 'Disabled') as string).getOrElse('Unknown')
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

  get config(): Option<any> {
    return this.settings.map(s => s.config)
  }

  get redirectInfo() {
    const serviceName = this.config.map(c => c?.kamon?.environment?.service).orNull
    const usedInstrumentation = this.instrumentationModules.filter(m => m.enabled && m.active)
    const framework = (() => {
      if (usedInstrumentation.some(i => i.name.toLocaleLowerCase().includes('play'))) {
        return 'play'
      } else if (usedInstrumentation.some(i => i.name.toLocaleLowerCase().includes('akka'))) {
        return 'akka'
      } else if (usedInstrumentation.some(i => i.name.toLocaleLowerCase().includes('spring'))) {
        return 'spring'
      } else {
        return 'plain'
      }
    })()
    const query = new URLSearchParams()

    query.set('continueOnboarding', 'true')
    query.set('framework', framework)
    query.set('serviceName', serviceName)
    return `https://apm.kamon.io?${query.toString()}`
  }

  public mounted() {
    this.refreshData()
  }

  public goToInstrumentation(): void {
    this.$vuetify.goTo('.js-instrumentation', { offset: 80 })
  }

  public goToReporters(): void {
    this.$vuetify.goTo('.js-reporters', { offset: 80 })
  }

  public goToMetrics(): void {
    this.$vuetify.goTo('.js-metrics', { offset: 80 })
  }

  private showApmApiKey() {
    window.open(this.redirectInfo)
  }

  private refreshData(): void {
    StatusApi.settings().then(settings => { this.settings = some(settings) })
    StatusApi.metricRegistryStatus().then(metricRegistry => { this.metricRegistry = some(metricRegistry) })
    StatusApi.moduleRegistryStatus().then(moduleRegistry => {this.moduleRegistry = some(moduleRegistry) })
    StatusApi.instrumentationStatus().then(instrumentation => {this.instrumentation = some(instrumentation) })
  }

  private isReporter(module: Module): boolean {
    return [ModuleKind.Combined, ModuleKind.Span, ModuleKind.Metric].includes(module.kind)
  }

  private isStarted(module: Module): boolean {
    return module.started
  }
}
</script>

<style lang="scss">
.overview-card {
  position: fixed;
  top: 232px;
  left: 50%;
  width: 1080px;
  z-index: 100;
  transform: translateX(-50%);
  transition: box-shadow 200ms linear;
}
</style>
