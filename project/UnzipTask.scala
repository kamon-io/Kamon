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
import Keys._

/** Helper task for jar/zip artifact unpacking. */
object UnzipTask {

  /** Helper task for jar/zip artifact unpacking. */
  lazy val unzipTask = TaskKey[Unit]("unzip-task", "Unpack the jar/zip archive.")

  /** Find artifact path by the module and classifier. */
  def locateArtifact(report: UpdateReport, module: ModuleID,
    classifier: String = ""): File =
    {
      val mf = moduleFilter(
        organization = module.organization, name = module.name, revision = module.revision
      )
      val af = artifactFilter(
        classifier = classifier
      )
      val pathList = report.select(module = mf, artifact = af)
      require(pathList.size == 1, s"Wrong select: ${pathList}")
      pathList(0)
    }

  /** Extract zip/jar artifact content into a target dir. */
  def extractArtifact(zip: File, dir: File,
    filter: NameFilter = AllPassFilter, flatten: Boolean = false): Set[java.io.File] =
    {
      IO createDirectory dir
      if (flatten) {
        /** Remove original directory structure. */
        val tmp = IO createTemporaryDirectory
        val sourceList = IO unzip (zip, tmp, filter)
        val targetList = for {
          source <- sourceList
        } yield {
          val target = dir / source.name
          IO copyFile (source, target)
          target
        }
        IO delete tmp
        targetList
      } else {
        /** Prserve original directory structure. */
        IO unzip (zip, dir, filter)
      }
    }

}
