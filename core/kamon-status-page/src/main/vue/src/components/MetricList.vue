<template>
  <status-section title="Metrics">
    <template #title="{ title }">
      <div>
        <div class="d-flex align-center">
          <h3>{{title}}</h3>
          <v-tooltip v-if="hasPotentialCardinalityIssues" bottom content-class="white dark1--text">
            <template #activator="{ on, attrs }">
              <v-avatar class="ml-2 c-pointer" size="26" color="warning" v-on="on" v-bind="attrs">
                <v-icon size="13" color="white">fa-exclamation</v-icon>
              </v-avatar>
            </template>

            Some of your metrics may have a cardinality issue!
          </v-tooltip>
        </div>
        <div class="text-note dark3--text" style="margin-top: 4px">
          {{matchedMetrics.length}} metrics found
        </div>
      </div>
    </template>

    <template #actions>
      <div style="max-width: 240px">
        <v-text-field
          v-model="filterPattern"
          placeholder="Search..."
          hide-details
          flat
          solo
          background-color="white"
          color="dark3"
          class="search-field"
        >
          <template #prepend-inner>
            <v-icon color="dark3" size="20" class="mr-1">fa-search</v-icon>
          </template>
        </v-text-field>
      </div>
    </template>

    <metric-list-item
      v-for="metric in matchedMetrics"
      :key="metric.name"
      :metric="metric"
    />
  </status-section>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'
import {Metric} from '../api/StatusApi'
import MetricListItem from './MetricListItem.vue'
import StatusSection from '@/components/StatusSection.vue'

@Component({
  components: {
    StatusSection,
    MetricListItem,
  },
})
export default class MetricList extends Vue {
  @Prop( { default: [] }) private metrics!: Metric[]
  private filterPattern: string = ''

  get filterRegex(): RegExp {
    return new RegExp(this.filterPattern)
  }

  get sortedMetrics(): Metric[] {
    return this.metrics.sort((a, b) => {
      if (a.instruments.length === 0 && b.instruments.length > 0) {
        return 1
      } else if (a.instruments.length > 0 && b.instruments.length === 0) {
        return -1
      }
      return a.name.localeCompare(b.name)
    })
  }

  get hasPotentialCardinalityIssues(): boolean {
    return this.metrics.some((m: Metric) => m.instruments.length > 300)
  }

  get matchedMetrics(): Metric[] {
    if (this.filterPattern.length > 0) {
      return this.sortedMetrics.filter(m => m.search.match(this.filterRegex) != null)
    } else {
      return this.sortedMetrics
    }
  }
}
</script>

<style lang="scss">
.search-field {
  .v-input__control {
    min-height: 39px !important;
  }
}
</style>
