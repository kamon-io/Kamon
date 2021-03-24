<template>
  <status-card
    :indicator-text="runStatus.message"
    :indicator-icon="runStatus.icon"
    :indicator-background-color="runStatus.color"
    :indicator-color="runStatus.indicatorColor"
    :content-class="{ 'suggestion-card': isSuggestion, 'misconfigured-card': isMissingKey }"
  >
    <template #default>
      <div>
        <div class="text-label dark1--text">
          {{module.name}}
        </div>
        <div class="text-sublabel mt-1 dark3--text">
          <span v-if="isMissingKey">
            Setting <strong>kamon.apm.api-key</strong> in <strong>application.conf</strong> invalid or missing
          </span>
          <span v-else>
            {{module.description}}
          </span>
        </div>
      </div>
    </template>
    <template #status v-if="!isSuggestion && !isMissingKey">
      <div
        class="module-status text-indicator px-2 py-1 rounded"
        :class="chipClasses"
      >
        {{status}}
      </div>
    </template>
    <template #action v-if="isSuggestion">
      <v-btn depressed color="primary" class="px-4 font-weight-bold" @click>
        Connect APM
      </v-btn>
    </template>
    <template #action v-else-if="isMissingKey">
      <v-btn depressed color="warning" class="px-4 font-weight-bold" @click="showApmApiKey">
        Configure API Key
      </v-btn>
    </template>
  </status-card>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'
import {InstrumentationModule, Module} from '../api/StatusApi'
import StatusCard from './StatusCard.vue'

const isInstrumentationModule = (m: any): m is InstrumentationModule =>
  m != null && (m as any).active != null

@Component({
  components: {
    StatusCard,
  },
})
export default class ModuleStatusCard extends Vue {
  @Prop({ default: false }) private isSuggestion!: boolean
  @Prop({ default: false }) private isMissingKey!: boolean
  @Prop() private module!: Module | InstrumentationModule

  get started(): boolean {
    return isInstrumentationModule(this.module) ? this.module.active : this.module.started
  }

  get status(): string {
    if (this.isMissingKey) {
      return 'Not Configured'
    } else if (this.started) {
      return 'Enabled'
    } else if (this.module.enabled) {
      return 'Present'
    } else {
      return 'Disabled'
    }
  }

  get runStatus(): { message: string, color: string, icon: string, indicatorColor: string } {
    if (this.isSuggestion) {
      return { message: 'suggested', color: 'primary', icon: 'fa-plug', indicatorColor: 'white' }
    } else if (!this.module.enabled) {
      return { message: 'disabled', color: 'error', icon: 'fa-stop-circle', indicatorColor: 'white' }
    } else if (this.isMissingKey) {
      return { message: 'not configured', color: 'warning', icon: 'fa-plug', indicatorColor: 'white'}
    } else {
      return this.started ?
        { message: 'active', color: 'primary', icon: 'fa-check', indicatorColor: 'white' } :
        { message: 'available', color: 'light2', icon: 'fa-power-off', indicatorColor: 'dark4' }
    }
  }

  get chipClasses(): string {
    if (!this.started) {
      return 'light2 dark3--text'
    } else if (this.module.enabled) {
      return 'green4 primary--text'
    } else {
      return 'red4 error--text'
    }
  }

  public showApmApiKey() {
    window.open('https://apm.kamon.io?envinfo=show')
  }
}
</script>

<style lang="scss">
.suggestion-card {
  border: 1px solid #3BC882 !important;
  background-color: #E3FFF1 !important;
}

.misconfigured-card {
  border: 1px solid #FFCC00 !important;
  background-color: #FFF8E5 !important;
}
</style>
