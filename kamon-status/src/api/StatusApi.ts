import axios, { AxiosResponse } from 'axios'

export interface Environment {
  service: string
  host: string
  instance: string
  tags: { [key: string]: string }
}

export interface Settings {
  version: string
  environment: Environment
  config: any
}

export enum ModuleKind {
  Combined = 'combined',
  Metric = 'metric',
  Span = 'span',
  Plain = 'plain',
  Unknown = 'unknown'
}

export interface Module {
  name: string
  description: string
  clazz: string
  kind: ModuleKind
  isProgrammaticallyRegistered: boolean
  isStarted: boolean
}

export interface Metric {
  name: string
  type: string
  tags: { [key: string ]: string }
  search: string
}

export interface ModuleRegistry {
  modules: Module[]
}

export interface MetricRegistry {
  metrics: Metric[]
}

export interface InstrumentationModule {
  description: string
  isEnabled: boolean
  isActive: boolean
}

export interface Instrumentation {
  isActive: boolean
  modules: { [key: string]: InstrumentationModule }
  errors: { [key: string]: string[]}
}


export class StatusApi {

  public static settings(): Promise<Settings> {
    return axios.get('/status/settings').then(response => {
      const config = JSON.parse(response.data.config)
      return {
        version: response.data.version,
        environment: response.data.environment,
        config
      }
    })
  }

  public static moduleRegistryStatus(): Promise<ModuleRegistry> {
    return axios.get('/status/modules').then(response => {
      return response.data as ModuleRegistry
    })
  }

  public static metricRegistryStatus(): Promise<MetricRegistry> {
    return axios.get('/status/metrics').then(response => {
      const metricRegistry = response.data as MetricRegistry
      const pair = (key: string, value: string) => key + ':' + value + ' '

      metricRegistry.metrics.forEach(metric => {
        // Fixes the display name for range samplers
        if (metric.type === 'RangeSampler') {
          metric.type = 'Range Sampler'
        }


        // Calculate the "search" string and inject it in all metrics.
        let tagsSearch = ''
        Object.keys(metric.tags).forEach(tag => {
          tagsSearch += pair(tag, metric.tags[tag])
        })

        metric.search =
          pair('name', metric.name.toLowerCase()) +
          pair('type', metric.type.toLowerCase()) +
          tagsSearch
      })

      return metricRegistry
    })
  }

  public static instrumentationStatus(): Promise<Instrumentation> {
    return axios.get('/status/instrumentation').then(response => {
      const instrumentation: Instrumentation = {
        isActive: response.data.isActive as boolean,
        modules: {},
        errors: {}
      }

      const rawModules = response.data.modules
      Object.keys(rawModules).forEach(key => {
        instrumentation.modules[key] = JSON.parse(rawModules[key])
      })

      const rawErrors = response.data.errors
      Object.keys(rawErrors).forEach(key => {
        instrumentation.errors[key] = JSON.parse(rawErrors[key])
      })

      return instrumentation
    })
  }
}
