<template>
  <div class="w-100">
    <status-section title="Reporters">
      <div class="row">
        <div class="col-12 py-1" v-for="reporter in reporterModules" :key="reporter.name">
          <module-status-card :module="reporter" />
        </div>
        <div v-if="!hasApmModule" class="col-12 py-1 apm-suggestion">
          <a href="https://kamon.io/" target="_blank">
            <module-status-card :is-suggestion="true" :module="apmModuleSuggestion" />
          </a>
        </div>
      </div>
    </status-section>

    <status-section title="Modules" v-if="plainModules.length > 0">
      <div class="row">
        <div class="col-12 py-1" v-for="module in plainModules" :key="module.name">
          <module-status-card :module="module"/>
        </div>
      </div>
    </status-section>
  </div>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'
import {Module, ModuleKind} from '../api/StatusApi'
import ModuleStatusCard from './ModuleStatusCard.vue'
import StatusSection from './StatusSection.vue'


@Component({
  components: {
    'status-section': StatusSection,
    'module-status-card': ModuleStatusCard
  }
})
export default class ModuleList extends Vue {
  @Prop() private modules!: Module[]
  private apmModuleSuggestion: Module = {
    name: 'Kamon APM',
    description: 'See your metrics and trace data for free with a Starter account.',
    kind: ModuleKind.Combined,
    programmaticallyRegistered: false,
    enabled: false,
    started: false,
    clazz: ''
  }

  get sortedModules(): Module[] {
    return this.modules.sort((left, right) => {
      if (left.started === right.started) {
        return left.name.localeCompare(right.name)
      } else {
        return left.started ? -1 : 1
      }
    })
  }


  get reporterModules(): Module[] {
    return this.sortedModules.filter(this.isReporter)
  }

  get plainModules(): Module[] {
    return this.sortedModules.filter(m => !this.isReporter(m))
  }

  get hasApmModule(): boolean {
    const knownApmClasses = [
      'kamon.kamino.KaminoReporter'
    ]

    return this.modules.find(m => knownApmClasses.indexOf(m.clazz) > 0) !== undefined
  }

  private isReporter(module: Module): boolean {
    return [ModuleKind.Combined, ModuleKind.Span, ModuleKind.Metric].indexOf(module.kind) > 0
  }

  private isStarted(module: Module): boolean {
    return module.started
  }
}
</script>