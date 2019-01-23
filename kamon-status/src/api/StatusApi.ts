import axios, { AxiosResponse } from 'axios'

export interface Environment {
  service: string
  host: string
  instance: string
  tags: { [key: string]: string }
}

export interface BaseInfo {
  version: string
  environment: Environment
  config: any
}

export interface Module {
  name: string
  description: string
  enabled: boolean
  started: boolean
}

export interface ModuleRegistryStatus {
  modules: Array<Module>
}


export class StatusApi {

  public baseInfo(): Promise<BaseInfo> {
    return axios.get("/status/base-info").then(response => {
      return response.data as BaseInfo
    })
  }

  public moduleRegistryStatus(): Promise<ModuleRegistryStatus> {
    return axios.get("/status/modules").then(response => {
      return response.data as ModuleRegistryStatus
    })
  }
}