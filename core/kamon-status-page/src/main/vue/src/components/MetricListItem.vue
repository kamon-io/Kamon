<template>
  <status-card
    :indicator-background-color="backgroundColor"
    :indicator-color="textColor"
    :class="{ 'c-pointer': metric.instruments.length > 0 }"
    @click.native="onCardClick"
  >
    <template #status-indicator="{ indicatorBackgroundColor, indicatorColor }">
      <v-tooltip bottom content-class="white dark1--text" :disabled="metric.instruments.length <= 300">
        <template #activator="{ on }">
          <v-avatar v-on="on" size="32" :color="indicatorBackgroundColor">
            <span v-on="on" class="text-indicator" :class="`${indicatorColor}--text`">
              {{metric.instruments.length}}
            </span>
          </v-avatar>
        </template>

        This metric might have cardinality issues
      </v-tooltip>
    </template>

    <template #default>
      <div>
        <div class="text-label dark1--text">
          {{metric.name}}
        </div>
        <div class="text-sublabel mt-1 dark3--text">
          {{metric.description}}
        </div>
      </div>
    </template>

    <template #status>
      <div class="d-flex">
        <div class="text-indicator light2 dark3--text px-2 py-1 rounded text-capitalized">
          {{metric.instrumentType}}
        </div>
        <div
          v-if="metric.unitDimension !== 'none'"
          class="text-indicator light2 dark3--text px-2 py-1 rounded ml-1 text-capitalized"
        >
          {{metric.unitMagnitude}}
        </div>
      </div>
    </template>

    <template #action>
      <v-icon color="dark4" size="16" class="metric-toggle" :class="{ disabled: metric.instruments.length === 0 }">{{expansionIcon}}</v-icon>
    </template>

    <template #append v-if="expanded">
      <div class="px-2" @click.stop>
        <v-card
          v-for="(instrument, index) in visibleInstruments"
          :key="instrument.key"
          elevation="0"
          class="pa-2 mb-1"
        >
          <v-row align="center">
            <v-col cols="3">
              <span class="text-label dark1--text">Instrument #{{index + 1}}</span>
            </v-col>
            <v-col cols="9" class="text-right d-flex justify-end flex-wrap">
              <div
                v-for="tag in instrument.tags"
                class="text-sublabel light2 dark3--text px-2 py-1 ml-1 mb rounded"
                style="margin-top: 2px; margin-bottom: 2px;"
              >
                {{tag[0]}} <strong class="dark1--text">{{tag[1]}}</strong>
              </div>
            </v-col>
          </v-row>
        </v-card>

        <div
          v-if="canShowMore"
          @click.stop="onShowMore"
          class="mb-2 text-decoration-underline text-sublabel primary--text font-weight-bold text-uppercase text-center w-100 pa-1"
        >
          View More
        </div>
      </div>
    </template>
  </status-card>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'
import {Metric} from '../api/StatusApi'
import Card from './Card.vue'
import StatusCard from './StatusCard.vue'
import _ from 'underscore'

interface InstrumentVM {
  key: string
  tags: Array<[string, string]>
}

const PAGE_SIZE = 25

@Component({
  components: {
    StatusCard,
  },
})
export default class MetricListItem extends Vue {
  @Prop( { default: null }) private metric!: Metric
  private expanded: boolean = false
  private visibleInstrumentIndex = PAGE_SIZE

  get backgroundColor(): string {
    if (this.metric.instruments.length > 300) {
      return 'warning'
    }

    if (this.metric.instruments.length === 0) {
      return 'light2'
    }

    return 'primary'
  }

  get textColor(): string {
    return this.metric.instruments.length > 0 ? 'white' : 'dark4'
  }

  get expansionIcon(): string {
    return this.expanded ? 'fa-chevron-up' : 'fa-chevron-down'
  }

  get visibleInstruments(): InstrumentVM[] {
    return this.metric.instruments.slice(0, this.visibleInstrumentIndex).map(inst => {
      const tags = Object.keys(inst).map((k: string): [string, string] => [k, inst[k]])
      const key = tags.map(p => p.join('-')).join(':')

      return { tags, key }
    })
  }

  get canShowMore(): boolean {
    return this.metric.instruments.length > this.visibleInstrumentIndex
  }

  private onShowMore(): void {
    this.visibleInstrumentIndex += PAGE_SIZE
  }

  private onCardClick() {
    if (this.metric.instruments.length === 0) {
      return
    }

    this.expanded = !this.expanded

    if (!this.expanded) {
      this.visibleInstrumentIndex = PAGE_SIZE
    }
  }
}
</script>

<style lang="scss">
.disabled.metric-toggle {
  opacity: 0.4;
}
</style>
