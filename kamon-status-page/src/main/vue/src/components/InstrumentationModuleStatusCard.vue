<template>
  <status-card :indicator-text="runStatus.message" :indicator-icon="runStatus.icon" :indicator-background-color="runStatus.color">
    <div slot="default" class="py-3 pl-4">
      <h5 class="mb-0 mr-3 d-inline-block">{{ module.name }}</h5>
      <div class="tag-container d-inline-block" v-if="!isSuggestion">
        <span class="tag">{{ status }}</span>
      </div>
      <div class="text-label">
        {{ module.description }}
      </div>
    </div>
  </status-card>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'
import {InstrumentationModule} from '../api/StatusApi'
import StatusCard from './StatusCard.vue'


@Component({
  components: {
    'status-card': StatusCard
  }
})
export default class InstrumentationModuleStatusCard extends Vue {
  @Prop() private module!: InstrumentationModule

  get status(): string {
    if (this.module.active) {
      return 'active'
    } else if (this.module.enabled) {
      return 'present'
    } else {
      return 'disabled'
    }
  }

  get runStatus(): { message: string, color: string, icon: string } {
    if (!this.module.enabled) {
      return { message: 'disabled', color: '#ff9898', icon: 'fa-stop-circle' }
    } else {
      return this.module.active ?
        { message: 'active', color: '#7ade94', icon: 'fa-check' } :
        { message: 'available', color: '#bbbbbb', icon: 'fa-power-off' }
    }
  }
}
</script>

<style lang="scss">

</style>
