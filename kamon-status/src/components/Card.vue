<template>
  <div class="card-wrapper">
    <slot/>
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'

@Component
export default class Card extends Vue {}
</script>


<style lang="scss">
.card-wrapper {
  background-color: white;
  box-shadow: 0 2px 9px 1px rgba(0, 0, 0, 0.1);
}
</style>
