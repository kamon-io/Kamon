package kamon

import com.typesafe.config.Config

trait Environment {
  def instance: String
  def host: String
  def application: String
  def config: Config
}
