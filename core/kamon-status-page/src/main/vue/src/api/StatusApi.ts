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
  programmaticallyRegistered: boolean
  enabled: boolean
  started: boolean
}

export interface Metric {
  name: string
  description: string
  type: string
  unitDimension: string
  unitMagnitude: string
  instrumentType: string
  instruments: Array<{ [key: string ]: string }>
  search: string
}

export interface ModuleRegistry {
  modules: Module[]
}

export interface MetricRegistry {
  metrics: Metric[]
}

export interface InstrumentationModule {
  path: string
  name: string
  description: string
  enabled: boolean
  active: boolean
}

export interface Instrumentation {
  present: boolean
  modules: InstrumentationModule[]
  errors: { [key: string]: InstrumentationError[]}
}

export interface InstrumentationError {
  message: string
  stacktrace: string
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
        if (metric.type === 'rangeSampler') {
          metric.type = 'Range Sampler'
        }


        // Calculate the "search" string, which contains all tags from all instruments
        let tagsSearch = ''
        metric.instruments.forEach(instrument => {
          Object.keys(instrument).forEach(tag => {
            tagsSearch += pair(tag, instrument[tag])
          })
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
        present: response.data.present as boolean,
        modules: [],
        errors: response.data.errors
      }

      const rawModules = response.data.modules
      Object.keys(rawModules).forEach(key => {
        const rawModule = rawModules[key]
        instrumentation.modules.push({
          name: key,
          ...rawModule
        })
      })

      return instrumentation
    })
  }
}
