<template>
  <div class="row">
    <div class="col-12 pt-4 pb-2">
      <h3>Reporters</h3>
    </div>
    <div class="col-12 py-1" v-for="reporter in reporterModules" :key="reporter.name">
      <module-status-card :module="reporter" />
    </div>
    <div v-if="!hasApmModule" class="col-12 py-1 apm-suggestion">
      <a href="https://kamon.io/" target="_blank">
        <module-status-card :is-suggestion="true" :module="apmModuleSuggestion" />
      </a>
    </div>

    <div class="col-12 pt-4 pb-2" v-if="plainModules.length > 0">
      <h2>Modules</h2>
    </div>
    <div class="col-12 py-1" v-for="module in plainModules" :key="module.name">
      <module-status-card :module="module"/>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'
import {Module, ModuleKind} from '../api/StatusApi'
import ModuleStatusCard from './ModuleStatusCard.vue'


@Component({
  components: {
    'module-status-card': ModuleStatusCard
  }
})
export default class ModuleList extends Vue {
  @Prop() private modules!: Module[]
  private apmModuleSuggestion: Module = {
    name: 'Kamon APM',
    description: 'See your metrics and trace data for free with a Starter account.',
    kind: ModuleKind.Combined,
    isProgrammaticallyRegistered: false,
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