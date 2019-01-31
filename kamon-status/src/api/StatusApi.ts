import axios, { AxiosResponse } from 'axios'

export interface Environment {
  service: string
  host: string
  instance: string
  tags: { [key: string]: string }
}

export interface Config {
  version: string
  environment: Environment
  config: any
}

export enum ModuleKind {
  Combined = "combined",
  Metric = "metric",
  Span = "span",
  Plain = "plain",
  Unknown = "unknown"
}

export interface Module {
  name: string
  description: string
  kind: ModuleKind
  enabled: boolean
  started: boolean
}

export interface MetricInfo {
  name: string
  type: string
  tags: { [key: string ]: string }
}

export interface ModuleRegistryStatus {
  modules: Array<Module>
}

export interface MetricRegistryStatus {
  metrics: Array<MetricInfo>
}


export class StatusApi {

  public static configStatus(): Promise<Config> {
    return axios.get("/status/config").then(response => {
      const config = JSON.parse(response.data.config)
      return {
        version: response.data.version,
        environment: response.data.environment,
        config
      }
    })
  }

  public static moduleRegistryStatus(): Promise<ModuleRegistryStatus> {
    return axios.get("/status/modules").then(response => {
      return response.data as ModuleRegistryStatus
    })
  }

  public static metricRegistryStatus(): Promise<MetricRegistryStatus> {
    return axios.get("/status/metrics").then(response => {
      return response.data as MetricRegistryStatus
    })
  }
}