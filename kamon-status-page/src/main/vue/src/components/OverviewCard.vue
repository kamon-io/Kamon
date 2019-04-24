<template>
  <status-section title="Overview">
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
  </status-section>
</template>


<script lang="ts">
import { Component, Vue, Prop } from 'vue-property-decorator'
import {Option, none, some} from 'ts-option'
import Card from '../components/Card.vue'
import StatusSection from '../components/StatusSection.vue'
import {StatusApi, ModuleRegistry, ModuleKind, MetricRegistry, Module, Metric, Instrumentation} from '../api/StatusApi'

@Component({
  components: {
    'card': Card,
    'status-section': StatusSection
  },
})
export default class OverviewCard extends Vue {
  @Prop() private moduleRegistry: Option<ModuleRegistry> = none
  @Prop() private metricRegistry: Option<MetricRegistry> = none
  @Prop() private instrumentation: Option<Instrumentation> = none

  get reporterModules(): Module[] {
    return this.moduleRegistry
      .map(moduleRegistry => moduleRegistry.modules.filter(this.isReporter))
      .getOrElse([])
  }

  get activeReporters(): Module[] {
    return this.reporterModules.filter(this.isStarted)
  }

  get trackedMetrics(): Option<number> {
    return this.metricRegistry.map(metricRegistry => metricRegistry.metrics.length)
  }

  get instrumentationStatusMessage(): string {
    return this.instrumentation.map(i => (i.present ? 'Active' : 'Disabled') as string).getOrElse('Unknown')
  }

  get metricsStatusMessage(): string {
    return this.trackedMetrics.map(mc => mc + ' Metrics').getOrElse('Unknown')
  }

  private isReporter(module: Module): boolean {
    return [ModuleKind.Combined, ModuleKind.Span, ModuleKind.Metric].indexOf(module.kind) > 0
  }

  private isStarted(module: Module): boolean {
    return module.started
  }
}
</script>
