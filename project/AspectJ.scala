/* =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

import sbt._
import sbt.Keys._
import com.typesafe.sbt.SbtAspectj.{ Aspectj, defaultAspectjSettings }
import com.typesafe.sbt.SbtAspectj.AspectjKeys.{ aspectjVersion, compileOnly, lintProperties, weaverOptions }


object AspectJ {

  lazy val aspectJSettings = inConfig(Aspectj)(defaultAspectjSettings) ++ aspectjDependencySettings ++ Seq(
   aspectjVersion in Aspectj    :=  Dependencies.aspectjVersion,
      compileOnly in Aspectj    :=  true,
                fork in Test    :=  true,
         javaOptions in Test  <++=  weaverOptions in Aspectj,
          javaOptions in run  <++=  weaverOptions in Aspectj,
   lintProperties in Aspectj    +=  "invalidAbsoluteTypeName = ignore"
  )

  def aspectjDependencySettings = Seq(
    ivyConfigurations += Aspectj,
    libraryDependencies <++= (aspectjVersion in Aspectj) { version => Seq(
      "org.aspectj" % "aspectjtools" % version % Aspectj.name,
      "org.aspectj" % "aspectjweaver" % version % Aspectj.name,
      "org.aspectj" % "aspectjrt" % version % Aspectj.name
    )}
  )
}