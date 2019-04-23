<template>
  <card>
    <div class="row status-card no-gutters">
      <div class="col-auto">
        <div class="status-indicator-wrap text-center text-uppercase" :style="indicatorStyle">
          <slot name="status-indicator">
            <div class="status-indicator h-100">
              <i class="fas fa-fw" :class="indicatorIcon"></i>
            </div>
          </slot>
        </div>
      </div>
      <div class="col">
        <slot name="default">

        </slot>
      </div>
    </div>
  </card>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'
import Card from './Card.vue'

@Component({
  components: {
    card: Card
  }
})
export default class StatusCard extends Vue {
  @Prop({ default: 'white' }) private indicatorColor!: string
  @Prop({ default: '#989898' }) private indicatorBackgroundColor!: string
  @Prop({ default: 'fa-question' }) private indicatorIcon!: string
  @Prop({ default: 'Unknown' }) private indicatorText!: string

  get indicatorStyle() {
    return {
      color: this.indicatorColor,
      backgroundColor: this.indicatorBackgroundColor
    }
  }
}

</script>

<style lang="scss">

$indicator-size: 6rem;
.status-card {
  min-height: $indicator-size;

  .status-indicator-wrap {
    height: 100%;
    min-width: $indicator-size;
    max-width: $indicator-size;
    min-height: $indicator-size;
    font-size: 0.9rem;
    font-weight: 600;
  }

  .status-indicator {
    font-size: 3rem;
    line-height: $indicator-size;
  }

  .critical {
    background-color: #dadada;
  }

  .healthy {
    background-color: #7ade94;
  }

  .suggested {
    background-color: #5fd7cc;
  }
}
</style>
