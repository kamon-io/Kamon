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
              <div class="text-uppercase text-label pb-1">Instrumentation</div>
              <h5>{{instrumentationStatusMessage}}</h5>
            </div>
            <div class="col-12 col-md-3 py-2 px-3">
              <div class="text-uppercase text-label pb-1">Reporters</div>
              <h5>{{ activeReporters.length }} Started</h5>
            </div>
            <div class="col-12 col-md-3 py-2 px-3">
              <div class="text-uppercase text-label pb-1">Metrics</div>
              <h5>{{metricsStatusMessage}}</h5>
            </div>
          </div>
        </card>
      </div>

      <div class="col-12 pt-4 pb-2">
        <h3>Environment</h3>
      </div>
      <div class="col-12">
        <card>
          <div class="row py-2 no-gutters">
            <div class="col-auto py-2 px-3">
              <div class="text-uppercase text-label pb-1">Service</div>
              <h6>{{ service }}</h6>
            </div>
            <div class="col-auto py-2 px-3">
              <div class="text-uppercase text-label pb-1">Host</div>
              <h6>{{ host }}</h6>
            </div>
            <div class="col-auto py-2 px-3">
              <div class="text-uppercase text-label pb-1">instance</div>
              <h6>{{instance}}</h6>
            </div>
            <div class="col-12 col-md-3 py-2 px-3">
              <div class="text-uppercase text-label pb-1">tags</div>
              <div class="tag-container" v-if="Object.keys(environmentTags).length > 0">
                <span class="tag" v-for="tag in Object.keys(environmentTags)" :key="tag">
                  {{ tag }}=<span class="tag-value">{{ environmentTags[tag] }}</span>
                  </span>
              </div>
              <div v-else>
                <h6>None</h6>
              </div>
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
import {StatusApi, Settings, ModuleRegistry, ModuleKind, MetricRegistry, Module, Metric, Instrumentation} from '../api/StatusApi'

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
    return this.metricsRegistry.map(metricRegistry => metricRegistry.metrics.length)
  }

  get instrumentationStatusMessage(): string {
    return this.instrumentation.map(i => (i.isActive ? 'Active' : 'Disabled') as string).getOrElse('Unknown')
  }

  get metricsStatusMessage(): string {
    return this.trackedMetrics.map(mc => mc + ' Tracked').getOrElse('Unknown')
  }

  get metrics(): Metric[] {
    return this.metricsRegistry
      .map(mr => mr.metrics)
      .getOrElse([])
  }

  get instance(): string {
    return this.settings.map(s => s.environment.instance).getOrElse('Unknown')
  }

  get host(): string {
    return this.settings.map(s => s.environment.host).getOrElse('Unknown')
  }

  get service(): string {
    return this.settings.map(s => s.environment.service).getOrElse('Unknown')
  }

  get environmentTags(): { [key: string]: string } {
    return this.settings.map(s => s.environment.tags).getOrElse({})
  }



  public mounted() {
    this.refreshData()
  }

  private refreshData(): void {
    StatusApi.settings().then(settings => { this.settings = some(settings) })
    StatusApi.metricRegistryStatus().then(metricsRegistry => { this.metricsRegistry = some(metricsRegistry) })
    StatusApi.moduleRegistryStatus().then(moduleRegistry => {this.moduleRegistry = some(moduleRegistry) })
    StatusApi.instrumentationStatus().then(instrumentation => {this.instrumentation = some(instrumentation) })
  }

  private isReporter(module: Module): boolean {
    return [ModuleKind.Combined, ModuleKind.Span, ModuleKind.Metric].indexOf(module.kind) > 0
  }

  private isStarted(module: Module): boolean {
    return module.isStarted
  }
}
</script>
