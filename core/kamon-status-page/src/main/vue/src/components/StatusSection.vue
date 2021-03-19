<template>
  <v-row no-gutters>
    <v-col cols="12" class="d-flex align-center justify-space-between py-0 dark1--text">
      <slot name="title" :title="title">
        <h3>{{title}}</h3>
      </slot>
      <slot name="actions"></slot>
    </v-col>
    <v-col cols="12" class="my-2">
      <slot name="default" />
    </v-col>
  </v-row>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'

@Component
export default class StatusSection extends Vue {
  @Prop() private title!: string
}
</script>
