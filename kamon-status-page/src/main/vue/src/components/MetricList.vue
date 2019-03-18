<template>
  <div class="row no-gutters">
    <div class="col-12">
      <div class="search-box mb-3">
        <span class="search-icon"><i class="fas fa-search fa-fw fa-flip-horizontal"></i></span>
        <input class="w-100" v-model="filterPattern" type="text">
        <span class="search-stats">{{ searchStats }}</span>
      </div>
    </div>

    <div class="col-12" v-if="matchedMetrics.length > 0">
      <div class="row no-gutters" v-for="(group, index) in groups" :key="group.name">
        <div class="col-12">
          <metric-list-item :group="group"/>
        </div>
        <hr v-if="index < (groups.length - 1)" class="w-100">
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'
import {Metric} from '../api/StatusApi'
import Card from './Card.vue'
import MetricListItem, {MetricGroup} from './MetricListItem.vue'
import StatusCard from './StatusCard.vue'
import _ from 'underscore'

@Component({
  components: {
    'card': Card,
    'status-card': StatusCard,
    'metric-list-item': MetricListItem
  }
})
export default class MetricList extends Vue {
  @Prop( { default: [] }) private metrics!: Metric[]
  private filterPattern: string = ''

  get totalMetrics(): number {
    return this.metrics.length
  }

  get groups(): MetricGroup[] {
    const gropedByName = _.groupBy(this.matchedMetrics, m => m.name)
    const metricGroups: MetricGroup[] = []

    Object.keys(gropedByName).forEach(metricName => {
      const metrics = gropedByName[metricName]

      // All metrics with the same name must have the same unit (constrained in Kamon) so
      // we can safely assume the first unit is the same for all.
      metricGroups.push({
        name: metricName,
        type: metrics[0].type,
        unitDimension: metrics[0].unitDimension,
        unitMagnitude: metrics[0].unitMagnitude,
        metrics
      })
    })

    return _.sortBy(metricGroups, mg => mg.metrics.length).reverse()
  }

  get filterRegex(): RegExp {
    return new RegExp(this.filterPattern)
  }

  get searchStats(): string {
    if (this.filterPattern.length > 0) {
      return 'showing ' + this.matchedMetrics.length + ' out of ' + this.totalMetrics + ' series'
    } else {
      return this.totalMetrics + ' series'
    }
  }

  get matchedMetrics(): Metric[] {
    if (this.filterPattern.length > 0) {
      return this.metrics.filter(m => m.search.match(this.filterRegex) != null)
    } else {
      return this.metrics
    }
  }
}
</script>

<style lang="scss">
.search-box {
  input {
    color: #676767;
    caret-color: #676767;
    height: 3rem;
    border: none;
    border-radius: 0.4rem;
    background-color: #efefef;
    padding-left: 3.5rem;
    font-size: 1.1rem;
    box-shadow: 0 2px 4px 1px rgba(0, 0, 0, 0.1);


    &:focus {
      outline: none;
    }
  }

  ::placeholder {
    color: #929292;
  }

  .search-icon {
    color: #c0c0c0;
    line-height: 3rem;
    font-size: 1.4rem;
    position: absolute;
    left: 1rem;
  }

  .search-stats {
    color: #a2a2a2;
    font-size: 1.1rem;
    position: absolute;
    line-height: 3rem;
    right: 0;
    padding-right: 1rem;
  }
}

</style>
