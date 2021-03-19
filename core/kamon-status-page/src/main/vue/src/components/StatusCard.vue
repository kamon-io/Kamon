<template>
  <div>
    <v-card elevation="0" class="pa-2 mb-1" :class="contentClass">
      <v-row no-gutters align="center">
        <v-col cols="auto" class="mr-2">
          <slot name="status-indicator" v-bind="{ indicatorBackgroundColor, indicatorColor, indicatorIcon }">
            <v-avatar size="32" :color="indicatorBackgroundColor">
              <v-icon size="16" :color="indicatorColor">{{indicatorIcon}}</v-icon>
            </v-avatar>
          </slot>
        </v-col>
        <v-col cols="auto" class="mr-2" style="max-width: 70%">
          <slot name="default" />
        </v-col>
        <v-spacer />
        <v-col cols="auto">
          <slot name="status" />
        </v-col>
        <v-col cols="auto" class="ml-3" v-if="$scopedSlots.action">
          <slot name="action" />
        </v-col>
      </v-row>
    </v-card>

    <slot name="append" />
  </div>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'

@Component({})
export default class StatusCard extends Vue {
  @Prop({ default: 'white' }) private indicatorColor!: string
  @Prop({ default: '#989898' }) private indicatorBackgroundColor!: string
  @Prop({ default: 'fa-question' }) private indicatorIcon!: string
  @Prop({ default: 'Unknown' }) private indicatorText!: string
  @Prop() private contentClass!: string

  get indicatorStyle() {
    return {
      color: this.indicatorColor,
      backgroundColor: this.indicatorBackgroundColor
    }
  }
}

</script>
