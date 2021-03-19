<template>
  <status-section title="Environment">
    <v-card elevation="0" class="pa-0">
      <div class="w-100 d-flex px-4 py-3">
        <div class="flex-grow-1">
          <div class="text-label text-uppercase dark1--text">
            Service
          </div>
          <div class="text-sublabel dark3--text mt-1">
            {{service}}
          </div>
        </div>
        <div class="flex-grow-1">
          <div class="text-label text-uppercase dark1--text">
            Host
          </div>
          <div class="text-sublabel dark3--text mt-1">
            {{host}}
          </div>
        </div>
        <div class="flex-grow-1">
          <div class="text-label text-uppercase dark1--text">
            Instance
          </div>
          <div class="text-sublabel dark3--text mt-1">
            {{instance}}
          </div>
        </div>
      </div>
      <div class="w-100 tags-container px-4 py-3">
        <div class="text-label text-uppercase dark1--text">
          Tags
        </div>
        <div class="tags">
          <template v-if="tags.length > 0">
            <div v-for="tag in tags" :key="tag" class="kamon-tag d-inline-block rounded mt-2 mr-2 pa-1 light2 dark3--text text-sublabel">
              {{tag}} <strong class="dark1--text">{{environmentTags[tag]}}</strong>
            </div>
          </template>
          <div v-else class="mt-1 dark3--text">
            None
          </div>
        </div>
      </div>
    </v-card>
  </status-section>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'
import {Environment} from '../api/StatusApi'
import StatusSection from '../components/StatusSection.vue'
import {Option} from 'ts-option'

@Component({
  components: {
    StatusSection,
  },
})
export default class EnvironmentCard extends Vue {
  @Prop({ required: true }) private environment!: Option<Environment>

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

  get tags(): string[] {
    return Object.keys(this.environmentTags)
  }
}
</script>

<style lang="scss" scoped>
.tags-container {
  border-top: 1px solid #E4E4EB;
}
</style>
