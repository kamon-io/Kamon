<template>
  <status-card indicator-background-color="#7ade94" class="metric-list-item c-pointer my-1" :class="{ 'my-4': expanded }">

    <div slot="status-indicator" @click="onCardClick">
      <div class="metric-count">
        {{ metric.instruments.length }}
      </div>
      <div>INSTRUMENTS</div>
    </div>

    <div slot="default" @click="onCardClick">
      <div class="row no-gutters">
        <div class="col">
          <div class="py-3 pl-4">
            <h5>{{ metric.name }}</h5>
            <div class="text-label">
              <span>{{metric.instrumentType}}</span>
              <span v-if="metric.unitDimension !== 'none'"> - {{ metric.unitDimension }}</span>
              <span v-if="metric.unitMagnitude !== 'none'"> - {{ metric.unitMagnitude }}</span>
            </div>
          </div>
        </div>
        <div class="col-auto expansion-icon px-5">
          <i class="fas fa-fw" :class="expansionIcon"></i>
        </div>
        <div class="col-12 series-container" v-if="expanded">
          <div v-for="(instrument, index) in metric.instruments" :key="index">
            <div class="p-3">
              <h6>Incarnation #{{ index + 1 }}</h6>
              <div class="tag-container">
                <span class="tag" v-for="tag in Object.keys(instrument)" :key="tag">
                  {{ tag }}=<span class="tag-value">{{ instrument[tag] }}</span>
                </span>
                <span v-if="Object.keys(instrument).length === 0" class="pl-2">Base Metric - No Tags</span>
              </div>
            </div>
            <hr v-if="index < (metric.instruments.length - 1)" class="w-100 incarnation-hr">
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


@Component({
  components: {
    'status-card': StatusCard
  }
})
export default class MetricListItem extends Vue {
  @Prop( { default: null }) private metric!: Metric
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
