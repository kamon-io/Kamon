<template>
  <card>
    <div class="row module-card">
      <div class="col-auto">
        <div class="status-indicator h-100 text-center pt-3" :class="runStatus.class">
          <i class="fas fa-fw" :class="runStatus.icon"></i>
          <div>
            {{ runStatus.message }}
          </div>
        </div>
      </div>
      <div class="col">
        <div class="py-3">
          <h5 class="mb-0">{{ moduleStatus.name }} </h5>
          <div class="text-label">
            {{ moduleStatus.description }}
          </div>
        </div>
      </div>
    </div>
  </card>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'
import {Module} from '../api/StatusApi'
import Card from './Card.vue'


@Component({
  components: {
    card: Card
  }
})
export default class ModuleCard extends Vue {
  @Prop({ default: true }) private showStatusIndicators!: boolean
  @Prop({ default: false }) private isSuggestion!: boolean
  @Prop() private moduleStatus!: Module

  get discoveryStatus(): { message: string, class: string } {
    return this.moduleStatus.isProgrammaticallyRegistered ?
      { message: 'manual', class: '' } :
      { message: 'automatic', class: '' }
  }

  get runStatus(): { message: string, class: string, icon: string } {
    if (this.isSuggestion) {
      return { message: 'suggested', class: 'suggested', icon: 'fa-plug' }
    } else {
      return this.moduleStatus.isStarted ?
        { message: 'started', class: 'healthy', icon: 'fa-check' } :
        { message: 'disabled', class: 'critical', icon: 'fa-power-off' }
    }
  }
}
</script>

<style lang="scss">

$indicator-size: 6rem;
.module-card {
  min-height: $indicator-size;

  .status-indicator {
    text-transform: uppercase;
    min-width: $indicator-size;
    min-height: $indicator-size;
    line-height: 2rem;
    font-size: 0.9rem;
    font-weight: 600;
    color: white;
    background-color: #cccccc;

    i {
      font-size: 2.5rem;
    }
  }

  .critical {
    background-color: #dadada;
  }

  .healthy {
    background-color: #7ade94;
  }

  .suggested {
    background-color: #5fd7cc;
  }
}
</style>
