<template>
  <div class="row">
    <div class="col-12 pt-4 pb-2">
      <h3>Reporters</h3>
    </div>
    <div class="col-12 py-1" v-for="reporter in reporterModules" :key="reporter.name">
      <module-card :moduleStatus="reporter" />
    </div>
    <div v-if="!hasApmModule" class="col-12 py-1 apm-suggestion">
      <a href="https://kamon.io/" target="_blank">
        <module-card :is-suggestion="true" :show-status-indicators="false" :moduleStatus="apmModuleSuggestion" />
      </a>
    </div>


    <div class="col-12 pt-4 pb-2" v-if="plainModules.length > 0">
      <h2>Modules</h2>
    </div>
    <div class="col-12 py-1" v-for="module in plainModules" :key="module.name">
      <module-card :moduleStatus="module"/>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'
import {Module, ModuleKind} from '../api/StatusApi'
import ModuleCard from './ModuleCard.vue'


@Component({
  components: {
    'module-card': ModuleCard
  }
})
export default class ModuleList extends Vue {
  @Prop() private modules!: Module[]
  private apmModuleSuggestion: Module = {
    name: 'Kamon APM',
    description: 'See your metrics and trace data for free with a Starter account.',
    kind: ModuleKind.Combined,
    isProgrammaticallyRegistered: false,
    isStarted: false,
    clazz: ''
  }

  get sortedModules(): Module[] {
    return this.modules.sort((left, right) => {
      if (left.isStarted === right.isStarted) {
        return left.name.localeCompare(right.name)
      } else {
        return left.isStarted ? -1 : 1
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
    return module.isStarted
  }
}
</script>

<style lang="scss">
.apm-suggestion {
  .kind-label {
    background-color: #d0f3f0;
  }
}
</style>
