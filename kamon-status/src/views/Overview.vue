<template>
  <div class="container">
    <div class="row">
      <div class="col-12 pb-3">
        <h2>Overview</h2>
      </div>
      <div class="col-6 col-md-4 p-2">
        <status-card :statusInfo="instrumentationStatus"></status-card>
      </div>
      <div class="col-6 col-md-4 p-2">
        <status-card :statusInfo="reporterStatus"></status-card>
      </div>
      <div class="col-6 col-md-4 p-2">
        <status-card :statusInfo="metricsStatus"></status-card>
      </div>

    </div>
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'
import {Option, none, some} from 'ts-option'
import StatusCard, {StatusInfo, Status} from '../components/StatusCard.vue'
import {StatusApi, Config, ModuleRegistryStatus, ModuleKind, MetricRegistryStatus, Module} from '../api/StatusApi'

interface ComponentStatus {

}

@Component({
  components: {
    'status-card': StatusCard
  },
})
export default class Overview extends Vue {
  private config: Option<Config> = none
  private moduleRegistry: Option<ModuleRegistryStatus> = none
  private metricsRegistry: Option<MetricRegistryStatus> = none


  get reporterStatus(): StatusInfo {
    const status: StatusInfo = {
      heading: 'Reporters',
      message: 'Unknown',
      status: Status.Unknown
    }

    const isReporter = function(module: Module): boolean {
      return [ModuleKind.Combined, ModuleKind.Span, ModuleKind.Metric].indexOf(module.kind) > 0
    }

    this.moduleRegistry.forEach(moduleRegistry => {
      const reportingModules = moduleRegistry.modules.filter(isReporter)
      if(reportingModules.length > 0) {
        status.status = Status.Healthy
        status.message = reportingModules.length + " Active"
      }
    })

    return status
  }

  get metricsStatus(): StatusInfo {
    const status: StatusInfo = {
      heading: 'Metrics',
      message: 'Unknown',
      status: Status.Unknown
    }

    this.metricsRegistry.forEach(metricRegistry => {
      status.message = metricRegistry.metrics.length + " Metrics"
    })

    return status
  }

  get instrumentationStatus(): StatusInfo {
    return {
      heading: 'Instrumentation',
      message: 'Unknown',
      status: Status.Unknown
    }
  }


  public mounted() {
    this.refreshData()
  }

  private refreshData(): void {
    StatusApi.configStatus().then(config => { this.config = some(config) })
    StatusApi.metricRegistryStatus().then(metricsRegistry => { this.metricsRegistry = some(metricsRegistry) })
    StatusApi.moduleRegistryStatus().then(moduleRegistry => {this.moduleRegistry = some(moduleRegistry) })
  }
}
</script>
