<template>
  <div class="row">
    <div class="col-12 pt-4 pb-2">
      <h3>{{ title }}</h3>
    </div>
    <div class="col-12 py-1">
      <slot name="default"/>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'

@Component
export default class StatusSection extends Vue {
  @Prop() private title!: string
}
</script>