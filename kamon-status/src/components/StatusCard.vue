<template>
  <div class="outer">
    <div class="py-2 px-3 heading text-uppercase">
      {{ statusInfo.heading }}
    </div>
    <hr>
    <div class="px-3 py-2">
      <div class="message" :class="messageStatusClass">
        {{ statusInfo.message }}
      </div>
      <div class="caption">
        {{ statusInfo.caption }}
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'
import axios from 'axios'

export enum Status {
  Healthy,
  Critical,
  Unknown
}

export interface StatusInfo {
  heading: string
  message: string
  caption?: string
  status: Status
}

@Component
export default class StatusCard extends Vue {
  @Prop() private statusInfo!: StatusInfo

  get messageStatusClass(): string[] {
    if(this.statusInfo != null && this.statusInfo.status != Status.Unknown) {
      return this.statusInfo.status == Status.Healthy ? ['healthy'] : ['critical']
    } else {
      return []
    }
  }
}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped lang="scss">
.outer {
  background-color: white;
  box-shadow: 0 2px 9px 1px rgba(0, 0, 0, 0.1);

  .heading {
    font-size: 0.9rem;
    color: #a5a5a5;
  }

  .message {
    color: #868686;
    font-weight: 600;
    font-size: 1.5rem;
  }

  .caption {
    color: #a5a5a5;
  }

  .critical {
    color: #ff6e6b;
  }
}

hr {
  margin: 1px;
  border-color: #f3f3f3;
}
</style>
