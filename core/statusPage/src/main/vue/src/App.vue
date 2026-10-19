<script setup lang="ts">
import TelemetryLogo from './components/TelemetryLogo.vue'
import { h, resolveComponent, type Ref } from 'vue'
import { useFetch } from '@vueuse/core'
import { ref, computed } from 'vue'
import type { TabsItem } from '@nuxt/ui'
import type { TableColumn } from '@nuxt/ui'

declare module '@nuxt/ui' {
  interface TabItem {
    count: Ref<number>
  }
}
interface InstrumentationModule {
  name: string
  description: string
  enabled: boolean
  active: boolean
}

interface InstrumentationModuleStatus {
  status: 'active' | 'enabled' | 'disabled'
}

interface Modules {
  modules: ExtensionModule[]
}

interface ExtensionModuleStatus {
  status: 'started' | 'stopped' | 'disabled'
}

interface ExtensionModule {
  name: string
  description: string
  kind: 'combined' | 'spans' | 'metrics' | 'scheduled'
  class: string
  programaticallyRegistered: boolean
  enabled: boolean
  started: boolean
}

type ModuleWithStatus = ExtensionModule & ExtensionModuleStatus

interface Instrumentation {
  present: boolean
  modules: Record<string, InstrumentationModule>
}

interface Metrics {
  metrics: Metric[]
}

interface Metric {
  name: string
  type: string
  description: string
  unitDimension: string
  unitMagnitude: string
  instruments: Record<string, string>[]
}

interface Instrument {
  metricName: string
  metricType: string
  tags: Record<string, string>
}

interface HealthCheck {
  healthy: boolean
  title: string
  description: string
}

const { data: metrics } = useFetch('/status/metrics').json<Metrics>()
const { data: modules } = useFetch('/status/modules').json<Modules>()
const { data: instrumentation } = useFetch('/status/instrumentation').json<Instrumentation>()

const isInstrumentationActive = computed<boolean>(() => {
  if (instrumentation.value && instrumentation.value.modules) {
    return Object.values(instrumentation.value.modules).some((module) => module.active)
  }
  return false
})

const modulesWithStatus = computed<ModuleWithStatus[]>(() => {
  if (modules.value?.modules) {
    return modules.value.modules.map((module) => ({
      ...module,
      status: module.enabled ? (module.started ? 'started' : 'stopped') : 'disabled',
    }))
  }
  return []
})

const reporters = computed<ModuleWithStatus[]>(() => {
  return modulesWithStatus.value.filter((module) => ['spans', 'metrics', 'combined'].includes(module.kind))
})

const otherModules = computed<ModuleWithStatus[]>(() => {
  return modulesWithStatus.value.filter((module) => ['spans', 'metrics', 'combined'].includes(module.kind) == false)
})

const startedExporters = computed<ExtensionModule[]>(() => {
  return reporters.value.filter((exporter) => exporter.started)
})

const isHealthy = computed<boolean>(() => {
  return isInstrumentationActive.value && startedExporters.value.length > 0
})

const healthChecks = computed<HealthCheck[]>(() => {
  const checks: HealthCheck[] = []
  const activeInstrumentation = isInstrumentationActive.value

  checks.push({
    healthy: activeInstrumentation,
    title: activeInstrumentation ? 'The instrumentation agent is installed' : 'The instrumentation agent is not active',
    description:
      'Your application needs the Kanela instrumentation agent for all automatic instrumentation to work properly.',
  })

  const reporterCount = startedExporters.value.length
  checks.push({
    healthy: reporterCount > 0,
    title: reporterCount > 0 ? `${reporterCount} reporters started` : 'No started reporters',
    description: 'Reporters send your telemetry data to external systems so you can visualize and analyze it.',
  })

  return checks
})

const instrumentationWithStatus = computed<(InstrumentationModule & InstrumentationModuleStatus)[]>(() => {
  if (instrumentation.value?.modules) {
    const allModules: (InstrumentationModule & InstrumentationModuleStatus)[] = Object.values(
      instrumentation.value.modules,
    ).map((module) => ({
      ...module,
      status: module.active ? 'active' : module.enabled ? 'enabled' : 'disabled',
    }))

    allModules.sort((a, b) => {
      if (a.status === b.status) {
        return a.name.localeCompare(b.name)
      }
      return a.status.localeCompare(b.status)
    })

    return allModules
  }
  return []
})

const metricCount = computed<number>(() => {
  return instruments.value.length
})

const instrumentationCount = computed<number>(() => {
  return instrumentationWithStatus.value.length
})

const reportersCount = computed<number>(() => {
  return reporters.value.length
})

const otherModulesCount = computed<number>(() => {
  return otherModules.value.length
})

const healthChecksCount = computed<number>(() => {
  return healthChecks.value.length
})

const informationTabs = computed<TabsItem[]>(() => {
  const tabs: TabsItem[] = []
  tabs.push({ label: 'Health Checks', slot: 'checks', count: healthChecksCount })
  tabs.push({ label: 'Instrumentation', slot: 'instrumentation', count: instrumentationCount })
  tabs.push({ label: 'Metrics', slot: 'metrics', count: metricCount })
  tabs.push({ label: 'Reporters', slot: 'reporters', count: reportersCount })
  tabs.push({ label: 'Modules', slot: 'otherModules', count: otherModulesCount })

  return tabs
})

const UBadge = resolveComponent('UBadge')

const modulesTableColumns: TableColumn<ExtensionModule & ExtensionModuleStatus>[] = [
  {
    accessorKey: 'status',
    header: 'Status',
    cell: ({ row }) => {
      const color = {
        started: 'success' as const,
        stopped: 'error' as const,
        disabled: 'neutral' as const,
      }[row.getValue('status') as string]

      return h(UBadge, { class: 'capitalize', variant: 'subtle', color }, () => row.getValue('status'))
    },
  },
  {
    accessorKey: 'name',
    header: 'Name',
  },
  {
    accessorKey: 'description',
    header: 'Description',
    meta: {
      class: {
        td: 'whitespace-normal wrap-break-word',
      },
    },
  },
  {
    accessorKey: 'kind',
    header: 'Kind',
  },
]

const instrumentationTableColumns: TableColumn<InstrumentationModule & InstrumentationModuleStatus>[] = [
  {
    accessorKey: 'status',
    header: 'Status',
    cell: ({ row }) => {
      const color = {
        active: 'success' as const,
        disabled: 'error' as const,
        enabled: 'neutral' as const,
      }[row.getValue('status') as string]

      return h(UBadge, { class: 'capitalize', variant: 'subtle', color }, () => row.getValue('status'))
    },
  },
  {
    accessorKey: 'name',
    header: 'Name',
  },
  {
    accessorKey: 'description',
    header: 'Description',
    meta: {
      class: {
        td: 'whitespace-normal wrap-break-word',
      },
    },
  },
]

const sortedMetrics = computed<Metric[]>(() => {
  const allMetrics = [...(metrics.value?.metrics ?? [])]
  allMetrics.sort((a, b) => {
    if (a.instruments.length == b.instruments.length) {
      return a.name.localeCompare(b.name)
    }

    return b.instruments.length - a.instruments.length
  })

  return allMetrics
})

const metricsTableColumns: TableColumn<Metric>[] = [
  {
    accessorKey: 'type',
    header: 'Type',
    cell: ({ row }) => {
      return h(UBadge, { class: 'capitalize', variant: 'subtle', color: 'neutral' }, () => row.getValue('type'))
    },
  },
  {
    accessorKey: 'instruments',
    header: 'Instruments',
    cell: ({ row }) => {
      return row.original.instruments.length
    },
  },
  {
    accessorKey: 'name',
    header: 'Name',
    meta: {
      class: {
        td: 'font-mono',
      },
    },
  },
  {
    accessorKey: 'description',
    header: 'Description',
    meta: {
      class: {
        td: 'whitespace-normal wrap-break-word',
      },
    },
  },
  {
    accessorKey: 'unitDimension',
    header: 'Unit',
  },
  {
    accessorKey: 'unitMagnitude',
    header: 'Magnitude',
  },
]

const instruments = computed<Instrument[]>(() => {
  const allInstruments: Instrument[] = []
  for (const metric of sortedMetrics.value) {
    for (const instrument of metric.instruments) {
      allInstruments.push({
        metricName: metric.name,
        metricType: metric.type,
        tags: instrument,
      })
    }
  }

  return allInstruments
})

const instrumentsTableColumns: TableColumn<Instrument>[] = [
  {
    accessorKey: 'metricType',
    header: 'Type',
    cell: ({ row }) => {
      return h(UBadge, { class: 'capitalize', variant: 'subtle', color: 'neutral' }, () => row.getValue('metricType'))
    },
  },
  {
    accessorKey: 'metricName',
    header: 'Metric',
    meta: {
      class: {
        td: 'font-mono',
      },
    },
  },
  {
    accessorKey: 'tags',
    header: 'Tags',
    cell: ({ row }) => {
      const tags = Object.entries(row.original.tags).map(([key, value]) => `${key}: ${value}`)
      const badges = tags.map((tag) => {
        return h(UBadge, { class: '', variant: 'subtle', color: 'neutral' }, () => tag)
      })

      return h('div', { class: 'flex flex-wrap gap-2' }, badges)
    },
  },
]

const metricsFilter = ref<string>('')
const showMetricInstruments = ref<boolean>(false)
</script>

<template>
  <UApp>
    <header>
      <div class="container mx-auto py-4 flex items-center justify-between">
        <TelemetryLogo class="h-12" />
        <div class="flex items-center space-x-4">
          <span>Need a home for your Telemetry data?</span>
          <UButton
            trailing-icon="i-hugeicons-link-square-02"
            href="https://kamon.io/apm/"
            target="_blank"
            size="lg"
            class="font-semibold">
            Try Kamon APM!
          </UButton>
        </div>
      </div>
    </header>

    <USeparator />

    <div class="container mx-auto flex flex-col w-full">
      <div class="flex flex-col w-full justify-center items-center py-16">
        <div
          class="rounded-full size-20 drop-shadow-lg flex justify-center items-center text-white"
          :class="isHealthy ? 'bg-primary' : 'bg-warning'">
          <UIcon :name="isHealthy ? 'i-hugeicons-checkmark-circle-03' : 'i-hugeicons-alert-02'" class="size-10"></UIcon>
        </div>

        <h1 class="text-2xl text-center text-balance font-semibold py-6">
          {{ isHealthy ? 'All good here!' : 'There are potential issues with your setup' }}
        </h1>
      </div>

      <UTabs :items="informationTabs" variant="link" size="xl">
        <template #trailing="{ item }">
          <UBadge color="primary" variant="solid">
            {{ item.count }}
          </UBadge>
        </template>
        <template #checks>
          <div class="pt-4 space-y-4">
            <div v-for="check in healthChecks" :key="check.title" class="flex space-x-4">
              <UIcon
                :name="check.healthy ? 'i-hugeicons-checkmark-circle-03' : 'i-hugeicons-alert-02'"
                :class="check.healthy ? 'bg-primary' : 'bg-warning'"
                class="size-8 text-white p-1.5 rounded-full shrink-0" />
              <div>
                <h2 class="leading-8 text-lg font-semibold">
                  {{ check.title }}
                </h2>
                <p class="text-muted">
                  {{ check.description }}
                </p>
              </div>
            </div>
          </div>
        </template>

        <template #reporters>
          <UTable :columns="modulesTableColumns" :data="reporters"></UTable>
        </template>

        <template #otherModules>
          <UTable :columns="modulesTableColumns" :data="otherModules"></UTable>
        </template>

        <template #instrumentation>
          <UTable :columns="instrumentationTableColumns" :data="instrumentationWithStatus"></UTable>
        </template>

        <template #metrics>
          <div class="flex py-4 justify-between items-center space-x-4">
            <UInput v-model="metricsFilter" placeholder="Filter..." class="w-full" @keyup.escape="metricsFilter = ''" />
            <USwitch v-model="showMetricInstruments" label="Show Instruments" size="lg" class="shrink-0" />
          </div>

          <UTable
            v-if="showMetricInstruments"
            :columns="instrumentsTableColumns"
            :data="instruments"
            v-model:global-filter="metricsFilter"></UTable>

          <UTable
            v-else
            :columns="metricsTableColumns"
            :data="sortedMetrics"
            v-model:global-filter="metricsFilter"></UTable>
        </template>
      </UTabs>
    </div>
  </UApp>
</template>
