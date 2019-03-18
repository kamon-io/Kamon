<template>
  <status-card :indicator-text="runStatus.message" :indicator-icon="runStatus.icon" :indicator-background-color="runStatus.color">
    <div slot="default" class="py-3 pl-4">
      <h5 class="mb-0 mr-3 d-inline-block">{{ module.name }}</h5>
      <div class="tag-container d-inline-block" v-if="!isSuggestion">
        <span class="tag">{{ module.kind }}</span>
        <span class="tag">{{ discoveryStatus }}</span>
      </div>

      <div class="text-label">
        {{ module.description }}
      </div>
    </div>
  </status-card>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'
import {Module} from '../api/StatusApi'
import StatusCard from './StatusCard.vue'


@Component({
  components: {
    'status-card': StatusCard
  }
})
export default class ModuleStatusCard extends Vue {
  @Prop({ default: false }) private isSuggestion!: boolean
  @Prop() private module!: Module

  get discoveryStatus(): string {
    return this.module.programmaticallyRegistered ? 'manual' : 'automatic'
  }

  get runStatus(): { message: string, color: string, icon: string } {
    if (this.isSuggestion) {
      return { message: 'suggested', color: '#5fd7cc', icon: 'fa-plug' }
    } else if (!this.module.enabled) {
      return { message: 'disabled', color: '#ff9898', icon: 'fa-stop-circle' }
    } else {
      return this.module.started ?
        { message: 'active', color: '#7ade94', icon: 'fa-check' } :
        { message: 'available', color: '#bbbbbb', icon: 'fa-check' }
    }
  }
}
</script>

<style lang="scss">

</style>
