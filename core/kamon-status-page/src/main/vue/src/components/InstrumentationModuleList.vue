<template>
  <status-section v-if="modules.length > 0" title="Instrumentation Modules">
    <module-status-card v-for="module in sortedModules" :key="module.name" :module="module" />
  </status-section>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'

import {InstrumentationModule} from '../api/StatusApi'
import ModuleStatusCard from './ModuleStatusCard.vue'
import StatusSection from './StatusSection.vue'

@Component({
  components: {
    StatusSection,
    ModuleStatusCard,
  },
})
export default class ModuleList extends Vue {
  @Prop() private modules!: InstrumentationModule[]

  get sortedModules(): InstrumentationModule[] {
    return this.modules.sort((left, right) => {
      if (left.active === right.active) {
        return left.name.localeCompare(right.name)
      } else {
        return left.active ? -1 : 1
      }
    })
  }
}
</script>
