<template>
  <div class="container">
    <div class="row">
      <div class="col-12 pt-4 pb-2">
        <h3>Status</h3>
      </div>
      <div class="col-12">
        <card>
          <div class="row py-2 no-gutters">
            <div class="col-12 col-md-3 py-2 px-3">
              <div class="text-uppercase text-label pb-2">Instrumentation</div>
              <h5>Active</h5>
            </div>
            <div class="col-12 col-md-3 py-2 px-3">
              <div class="text-uppercase text-label pb-2">Reporters</div>
              <h5>{{ activeReporters.length }} Started</h5>
            </div>
            <div class="col-12 col-md-3 py-2 px-3">
              <div class="text-uppercase text-label pb-2">Metrics</div>
              <h5>{{metricsStatusMessage}}</h5>
            </div>
          </div>
        </card>
      </div>

      <div class="col-12 pt-4 pb-2">
        <h3>Reporters</h3>
      </div>
      <div class="col-12 py-1" v-for="reporter in reporterModules" :key="reporter.name">
        <module-status :moduleStatus="reporter" />
      </div>

      <div class="col-12 pt-4 pb-2" v-if="plainModules.length > 0">
        <h2>Modules</h2>
      </div>
      <div class="col-12 py-1" v-for="module in plainModules" :key="module.name">
        <module-status :moduleStatus="module" />
      </div>

      <div class="col-12 pt-4 pb-2" v-if="metrics.length > 0">
        <h2>Metrics</h2>
      </div>
      <div class="col-12 mb-5">
        <metric-list :metrics="metrics"/>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import {Option, none, some} from 'ts-option'
import ModuleStatus from '../components/ModuleStatus.vue'
import Card from '../components/Card.vue'
import MetricList from '../components/MetricList.vue'
import {StatusApi, Settings, ModuleRegistry, ModuleKind, MetricRegistry, Module, Metric} from '../api/StatusApi'

@Component({
  components: {
    'card': Card,
    'module-status': ModuleStatus,
    'metric-list': MetricList
  },
})
export default class Overview extends Vue {
  private settings: Option<Settings> = none
  private moduleRegistry: Option<ModuleRegistry> = none
  private metricsRegistry: Option<MetricRegistry> = none

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
    return this.metricsRegistry.map(metricRegistry => metricRegistry.metrics.length)
  }

  get metricsStatusMessage(): string {
    return this.trackedMetrics.map(mc => mc + ' Tracked').getOrElse('Unknown')
  }

  get metrics(): Metric[] {
    return this.metricsRegistry
      .map(mr => mr.metrics)
      .getOrElse([])
  }


  public mounted() {
    this.refreshData()
  }

  private refreshData(): void {
    StatusApi.settings().then(settings => { this.settings = some(settings) })
    StatusApi.metricRegistryStatus().then(metricsRegistry => { this.metricsRegistry = some(metricsRegistry) })
    StatusApi.moduleRegistryStatus().then(moduleRegistry => {this.moduleRegistry = some(moduleRegistry) })
  }

  private isReporter(module: Module): boolean {
      return [ModuleKind.Combined, ModuleKind.Span, ModuleKind.Metric].indexOf(module.kind) > 0
    }

  private isStarted(module: Module): boolean {
    return module.isStarted
  }
}
</script>
