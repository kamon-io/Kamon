/*
 * =========================================================================================
 * Copyright © 2013-2016 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.akka.http

import akka.actor.ReflectiveDynamicAccess
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.Host
import com.typesafe.config.Config
import kamon.{Kamon, OnReconfigureHook}


object AkkaHttp {

  @volatile private var nameGenerator: OperationNameGenerator = nameGeneratorFromConfig(Kamon.config())

  def serverOperationName(request: HttpRequest): String =
    nameGenerator.serverOperationName(request)

  def clientOperationName(request: HttpRequest): String =
    nameGenerator.clientOperationName(request)


  trait OperationNameGenerator {
    def serverOperationName(request: HttpRequest): String
    def clientOperationName(request: HttpRequest): String
  }

  private def defaultOperationNameGenerator(): OperationNameGenerator = new OperationNameGenerator {

    def clientOperationName(request: HttpRequest): String = {
      val uriAddress = request.uri.authority.host.address
      if (uriAddress.isEmpty) hostFromHeaders(request).getOrElse("unknown-host") else uriAddress
    }

    def serverOperationName(request: HttpRequest): String =
      request.uri.path.toString()

    private def hostFromHeaders(request: HttpRequest): Option[String] =
      request.header[Host].map(_.host.toString())
  }

  private def nameGeneratorFromConfig(config: Config): OperationNameGenerator = {
    val nameGeneratorFQN = config.getString("kamon.akka-http.name-generator")
    if(nameGeneratorFQN == "default") defaultOperationNameGenerator() else {
      new ReflectiveDynamicAccess(getClass.getClassLoader)
        .createInstanceFor[OperationNameGenerator](nameGeneratorFQN, Nil).get // let's bubble up any problems.
    }
  }

  @volatile var addHttpStatusCodeAsMetricTag: Boolean = addHttpStatusCodeAsMetricTagFromConfig(Kamon.config())

  private def addHttpStatusCodeAsMetricTagFromConfig(config: Config): Boolean =
    Kamon.config.getBoolean("kamon.akka-http.add-http-status-code-as-metric-tag")

  Kamon.onReconfigure(new OnReconfigureHook {
    override def onReconfigure(newConfig: Config): Unit = {
      nameGenerator = nameGeneratorFromConfig(newConfig)
      addHttpStatusCodeAsMetricTag = addHttpStatusCodeAsMetricTagFromConfig(newConfig)
    }
  })
}
