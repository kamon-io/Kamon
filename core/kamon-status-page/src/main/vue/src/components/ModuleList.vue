<template>
  <div class="w-100">
    <status-section title="Reporters">
      <template #title="{ title }">
        <div class="d-flex align-center">
          <h3>{{title}}</h3>
          <v-tooltip v-if="noReportersStarted" bottom content-class="white dark1--text">
            <template #activator="{ on, attrs }">
              <v-avatar class="ml-2 c-pointer" size="26" color="error" v-on="on" v-bind="attrs">
                <v-icon size="13" color="white">fa-times</v-icon>
              </v-avatar>
            </template>

            No reporters have been started yet!
          </v-tooltip>
        </div>
      </template>

      <module-status-card v-for="reporter in reporterModules" :key="reporter.name" :module="reporter" />
      <div v-if="!hasApmModule" class="apm-suggestion">
        <a href="https://kamon.io/apm/?utm_source=kamon&utm_medium=status-page&utm_campaign=kamon-status" target="_blank">
          <module-status-card is-suggestion :module="apmModuleSuggestion" />
        </a>
      </div>
    </status-section>

    <status-section title="Modules" v-if="plainModules.length > 0">
      <module-status-card v-for="reporter in plainModules" :key="reporter.name" :module="reporter" />
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
    StatusSection,
    ModuleStatusCard,
  },
})
export default class ModuleList extends Vue {
  @Prop() private modules!: Module[]
  private apmModuleSuggestion: Module = {
    name: 'Kamon APM Reporter',
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

  get noReportersStarted(): boolean {
    return this.reporterModules.every(r => !r.started)
  }

  get plainModules(): Module[] {
    return this.sortedModules.filter(m => !this.isReporter(m))
  }

  get hasApmModule(): boolean {
    const knownApmClasses = [
      'kamon.apm.KamonApm'
    ]

    return this.modules.find(m => knownApmClasses.includes(m.clazz)) !== undefined
  }

  private isReporter(module: Module): boolean {
    return [ModuleKind.Combined, ModuleKind.Span, ModuleKind.Metric].includes(module.kind)
  }
}
</script>
