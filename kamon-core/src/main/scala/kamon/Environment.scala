package kamon

import java.util.concurrent.ScheduledExecutorService

import com.typesafe.config.Config

trait Environment {
  def instance: String
  def host: String
  def application: String
  def config: Config
  def scheduler: ScheduledExecutorService
}
