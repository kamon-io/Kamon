<template>
  <div class="row no-gutters">
    <div class="col-12">
      <div class="search-box mb-3">
        <input class="w-100 px-3 py-2" v-model="filterPattern" type="text" placeholder="filter">
        <span class="search-stats">{{ searchStats }}</span>
      </div>
    </div>

    <div class="col-12">
      <card v-if="matchedMetrics.length > 0">
        <div class="row no-gutters" v-for="(metric, index) in matchedMetrics" :key="metric.search">
          <div class="col-12 px-3 pt-2 pb-3">
            <div class="text-uppercase text-label">{{ metric.type }}</div>
            <h5>{{ metric.name }}</h5>
            <div class="tag-container">
              <span class="tag" v-for="tag in Object.keys(metric.tags)" :key="tag">
                {{ tag }}=<span class="tag-value">{{ metric.tags[tag] }}</span>
                </span>
            </div>

          </div>
          <hr v-if="index < (matchedMetrics.length - 1)" class="w-100">
        </div>
      </card>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'
import {Metric} from '../api/StatusApi'
import Card from './Card.vue'


@Component({
  components: {
    card: Card
  }
})
export default class MetricList extends Vue {
  @Prop() private metrics!: Metric[]
  private filterPattern: string = ''

  get totalMetrics(): number {
    return this.metrics.length
  }

  get filterRegex(): RegExp {
    return new RegExp(this.filterPattern)
  }

  get searchStats(): string {
    if (this.filterPattern.length > 0) {
      return this.matchedMetrics.length + ' matched'
    } else {
      return this.totalMetrics + ' metrics'
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

<style scoped lang="scss">
.search-box {
  input {
    color: #676767;
    height: 2.5rem;
    border: none;
    border-radius: 0.3rem;
    background-color: #efefef;

    &:focus {
      outline: none;
    }
  }

  ::placeholder {
    color: #929292;
  }

  .search-stats {
    position: absolute;
    line-height: 2.5rem;
    right: 0;
    padding-right: 1rem;
  }
}
</style>
