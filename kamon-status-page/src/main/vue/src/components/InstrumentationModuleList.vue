<template>
  <div class="row">
    <div class="col-12 pt-4 pb-2" v-if="modules.length > 0">
      <h2>Instrumentation Modules</h2>
    </div>
    <div class="col-12 py-1" v-for="module in sortedModules" :key="module.name">
      <instrumentation-module-status-card :module="module"/>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'
import {InstrumentationModule} from '../api/StatusApi'
import InstrumentationModuleStatusCard from './InstrumentationModuleStatusCard.vue'


@Component({
  components: {
    'instrumentation-module-status-card': InstrumentationModuleStatusCard
  }
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

<style lang="scss">
.apm-suggestion {
  .kind-label {
    background-color: #d0f3f0;
  }
}
</style>
