<template>
  <card>
    <div class="row">
      <div class="col-12">
        <div class="py-2 px-3 text-uppercase text-label">
          {{ moduleStatus.kind }}
        </div>
        <hr>
      </div>
      <div class="col">
        <div class="px-3 py-2">
          <h4>{{ moduleStatus.name }}</h4>
          <div class="text-label">
            {{ moduleStatus.description }}
          </div>
        </div>
      </div>
      <div class="col-auto">
        <div class="status-indicator text-center" :class="runStatus.class">{{ runStatus.message }}</div>
        <div class="status-indicator text-center">{{ discoveryStatus.message }}</div>
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
export default class ModuleStatus extends Vue {
  @Prop() private moduleStatus!: Module

  get discoveryStatus(): { message: string, class: string } {
    return this.moduleStatus.isProgrammaticallyRegistered ?
      { message: 'manual', class: '' } :
      { message: 'automatic', class: '' }
  }

  get runStatus(): { message: string, class: string } {
    return this.moduleStatus.isStarted ?
      { message: 'started', class: 'healthy' } :
      { message: 'disabled', class: 'critical' }
  }
}
</script>

<style scoped lang="scss">
.status-indicator {
  font-size: 0.9rem;
  border-radius: 0.3rem;
  color: white;
  margin: 0.5rem 1rem;
  padding: 0.1rem 1rem;
  background-color: #cccccc;
}

.critical {
  background-color: #ff6e6b;
}

.healthy {
  background-color: #6ada87;
}

hr {
  margin: 1px;
  border-color: #f3f3f3;
}
</style>
