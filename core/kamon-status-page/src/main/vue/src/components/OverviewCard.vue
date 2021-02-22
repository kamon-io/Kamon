<template>
  <v-card elevation="0" color="white">
    <v-row no-gutters class="text-center">
      <v-col cols="4" class="py-5 px-4 d-flex flex-column position-relative overview-col">
        <h2 class="dark1--text" v-if="enabledInstruments > 0">
          {{enabledInstruments}} Enabled <v-divider vertical class="mx-1" /> {{disabledInstruments}} Disabled
        </h2>
        <h2 class="dark1--text" v-else>
          Not Connected
        </h2>
        <div class="mt-1 subtitle dark3--text subtitle">Instrumentation</div>
        <v-avatar size="40" class="overview-status-indicator" :color="instrumentsOk ? 'primary' : 'error'">
          <v-icon size="18" color="white">{{instrumentsOk ? 'fa-check' : 'fa-times'}}</v-icon>
        </v-avatar>
      </v-col>
      <v-col cols="4" class="py-5 px-4 d-flex flex-column position-relative overview-col">
        <h2 class="dark1--text">{{ activeReporters.length }} Started</h2>
        <div class="mt-1 subtitle dark3--text subtitle">Reporters</div>
        <v-avatar size="40" class="overview-status-indicator" :color="reportersOk ? 'primary' : 'error'">
          <v-icon size="18" color="white">{{reportersOk ? 'fa-check' : 'fa-times'}}</v-icon>
        </v-avatar>
      </v-col>
      <v-col cols="4" class="py-5 px-4 d-flex flex-column position-relative overview-col">
        <h2 class="dark1--text">{{ trackedMetrics }} Metrics</h2>
        <div class="mt-1 subtitle dark3--text subtitle">Metrics</div>
        <v-avatar size="40" class="overview-status-indicator" :color="metricsOk ? 'primary' : 'warning'">
          <v-icon size="18" color="white">{{metricsOk ? 'fa-check' : 'fa-exclamation'}}</v-icon>
        </v-avatar>
      </v-col>
    </v-row>
  </v-card>
</template>


<script lang="ts">
import { Component, Vue, Prop } from 'vue-property-decorator'
import {Option, none} from 'ts-option'
import StatusSection from '../components/StatusSection.vue'
import {
  ModuleRegistry,
  ModuleKind,
  MetricRegistry,
  Module,
  Instrumentation,
  InstrumentationModule, Metric
} from '../api/StatusApi'

@Component({
  components: {
    StatusSection,
  },
})
export default class OverviewCard extends Vue {
  @Prop({ required: true }) private moduleRegistry!: Option<ModuleRegistry>
  @Prop({ required: true }) private metricRegistry!: Option<MetricRegistry>
  @Prop({ required: true }) private instrumentation!: Option<Instrumentation>

  get reporterModules(): Module[] {
    return this.moduleRegistry
      .map(moduleRegistry => moduleRegistry.modules.filter(this.isReporter))
      .getOrElse([])
  }

  get activeReporters(): Module[] {
    return this.reporterModules.filter(this.isStarted)
  }

  get reportersOk(): boolean {
    return this.moduleRegistry.isEmpty || this.activeReporters.length > 0
  }

  get trackedMetrics(): number {
    return this.metricRegistry.map(metricRegistry => metricRegistry.metrics.length).getOrElse(0)
  }

  get metricsOk(): boolean {
    if (this.metricRegistry.isEmpty) {
      return true
    }

    return this.metricRegistry
      .map((mr: MetricRegistry) => mr.metrics.length > 0)
      .getOrElse(false) &&

      this.metricRegistry
        .map((mr: MetricRegistry) => mr.metrics.every((m: Metric) => m.instruments.length < 300))
        .getOrElse(true)
  }

  get instrumentsOk(): boolean {
    return this.instrumentation
      .map((i: Instrumentation) => Object.keys(i.errors).length === 0)
      .getOrElse(true)
  }

  get enabledInstruments(): number {
    return this.instrumentation
      .map((i: Instrumentation) => i.modules.filter((m: InstrumentationModule) => m.enabled).length)
      .getOrElse(0)
  }

  get disabledInstruments(): number {
    return this.instrumentation
      .map((i: Instrumentation) => i.modules.filter((m: InstrumentationModule) => !m.enabled).length)
      .getOrElse(0)
  }

  get instrumentationStatusMessage(): string {
    return this.instrumentation.map(i => (i.present ? 'Active' : 'Disabled') as string).getOrElse('Unknown')
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
  .overview-col {
    &:not(:last-child) {
      border-right: 1px solid #E4E4EB;
    }

    .overview-status-indicator {
      position: absolute;
      bottom: -40px;
      left: calc(50% - 40px);
      transform: translate(50%, -50%);
    }
  }
</style>
