<template>
  <status-section title="Environment">
    <card>
      <div class="row py-2 no-gutters">
        <div class="col-auto py-2 px-3">
          <div class="text-uppercase text-label pb-1">Service</div>
          <h6>{{ service }}</h6>
        </div>
        <div class="col-auto py-2 px-3">
          <div class="text-uppercase text-label pb-1">Host</div>
          <h6>{{ host }}</h6>
        </div>
        <div class="col-auto py-2 px-3">
          <div class="text-uppercase text-label pb-1">instance</div>
          <h6>{{instance}}</h6>
        </div>
        <div class="col-12 col-md-3 py-2 px-3">
          <div class="text-uppercase text-label pb-1">tags</div>
          <div class="tag-container" v-if="Object.keys(environmentTags).length > 0">
            <span class="tag" v-for="tag in Object.keys(environmentTags)" :key="tag">
              {{ tag }}=<span class="tag-value">{{ environmentTags[tag] }}</span>
              </span>
          </div>
          <div v-else>
            <h6>None</h6>
          </div>
        </div>
      </div>
    </card>
  </status-section>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'
import {Environment} from '../api/StatusApi'
import Card from './Card.vue'
import StatusSection from '../components/StatusSection.vue'
import {Option, none} from 'ts-option'


@Component({
  components: {
    'card': Card,
    'status-section': StatusSection
  }
})
export default class EnvironmentCard extends Vue {
  @Prop() private environment: Option<Environment> = none

  get instance(): string {
    return this.environment.map(e => e.instance).getOrElse('Unknown')
  }

  get host(): string {
    return this.environment.map(e => e.host).getOrElse('Unknown')
  }

  get service(): string {
    return this.environment.map(e => e.service).getOrElse('Unknown')
  }

  get environmentTags(): { [key: string]: string } {
    return this.environment.map(e => e.tags).getOrElse({})
  }
}
</script>