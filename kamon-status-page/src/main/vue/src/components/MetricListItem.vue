<template>
  <status-card indicator-background-color="#7ade94" class="metric-list-item c-pointer my-1" :class="{ 'my-4': expanded }">

    <div slot="status-indicator" @click="onCardClick">
      <div class="metric-count">
        {{ group.metrics.length }}
      </div>
      <div>SERIES</div>
    </div>

    <div slot="default" @click="onCardClick">
      <div class="row no-gutters">
        <div class="col">
          <div class="py-3 pl-4">
            <h5>{{ group.name }}</h5>
            <div class="text-label">
              <span>{{group.type}}</span>
              <span v-if="group.unitDimension !== 'none'"> - {{ group.unitDimension }}</span>
              <span v-if="group.unitMagnitude !== 'none'"> - {{ group.unitMagnitude }}</span>
            </div>
          </div>
        </div>
        <div class="col-auto expansion-icon px-5">
          <i class="fas fa-fw" :class="expansionIcon"></i>
        </div>
        <div class="col-12 series-container" v-if="expanded">
          <div v-for="(metric, index) in group.metrics" :key="index">
            <div class="p-3">
              <h6>Incarnation #{{ index + 1 }}</h6>
              <div class="tag-container">
                <span class="tag" v-for="tag in Object.keys(metric.tags)" :key="tag">
                  {{ tag }}=<span class="tag-value">{{ metric.tags[tag] }}</span>
                </span>
                <span v-if="Object.keys(metric.tags).length === 0" class="pl-2">Base Metric - No Tags</span>
              </div>
            </div>
            <hr v-if="index < (group.metrics.length - 1)" class="w-100 incarnation-hr">
          </div>
        </div>
      </div>
    </div>


  </status-card>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'
import {Metric} from '../api/StatusApi'
import Card from './Card.vue'
import StatusCard from './StatusCard.vue'
import _ from 'underscore'

export interface MetricGroup {
  name: string
  type: string
  unitDimension: string
  unitMagnitude: string
  metrics: Metric[]
}

@Component({
  components: {
    'status-card': StatusCard
  }
})
export default class MetricListItem extends Vue {
  @Prop( { default: [] }) private group!: MetricGroup
  private expanded: boolean = false

  get expansionIcon(): string {
    return this.expanded ? 'fa-angle-up' : 'fa-angle-down'
  }

  private onCardClick() {
    this.expanded = !this.expanded
  }
}
</script>

<style lang="scss">

.metric-count {
  font-size: 2rem;
  font-weight: 700;
  line-height: 4rem;
}

.expansion-icon {
  line-height: 6rem;
  color: #d0d0d0;
  font-size: 2rem;
}

.series-container {
  background-color: #f7f7f7;
}

.incarnation-hr {
  border-color: #e2e2e2;
}

.metric-list-item {
  .tag {
    background-color: #e6e6e6;
  }
}
</style>
