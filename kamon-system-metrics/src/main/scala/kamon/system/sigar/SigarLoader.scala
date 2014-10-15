/*
 * =========================================================================================
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

package kamon.system.sigar

import java.io._
import java.text.SimpleDateFormat
import java.util
import java.util.logging.Logger
import java.util.{ ArrayList, Date, List }

import org.hyperic.sigar._

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.io.Source

object SigarHolder {
  private lazy val sigarProxy = SigarLoader.sigarProxy
  def instance() = sigarProxy
}

object SigarLoader {

  val Version = "1.6.4"
  val JavaLibraryPath = "java.library.path"
  val TmpDir = "java.io.tmpdir"
  val IndexFile = "/kamon/system/sigar/index"
  val UsrPathField = "usr_paths"

  private val log = Logger.getLogger("SigarLoader")

  def sigarProxy = init(new File(System.getProperty(TmpDir)))

  private[sigar] def init(baseTmp: File): SigarProxy = {
    val tmpDir = createTmpDir(baseTmp)
    for (lib ← loadIndex) copy(lib, tmpDir)

    attachToLibraryPath(tmpDir)

    try {
      val sigar = new Sigar()
      printBanner(sigar)
      sigar
    } catch {
      case t: Throwable ⇒ {
        log.severe("Failed to load sigar")
        throw new RuntimeException(t)
      }
    }
  }

  private[sigar] val usrPathField = {
    val usrPathField = classOf[ClassLoader].getDeclaredField(UsrPathField)
    usrPathField.setAccessible(true)
    usrPathField
  }

  private[sigar] def attachToLibraryPath(dir: File): Unit = {
    val dirAbsolute = dir.getAbsolutePath
    System.setProperty(JavaLibraryPath, newLibraryPath(dirAbsolute))
    var paths = usrPathField.get(null).asInstanceOf[Array[String]]
    if (paths == null) paths = new Array[String](0)
    for (path ← paths) if (path == dirAbsolute) return
    val newPaths = util.Arrays.copyOf(paths, paths.length + 1)
    newPaths(newPaths.length - 1) = dirAbsolute
    usrPathField.set(null, newPaths)
  }

  private[sigar] def newLibraryPath(dirAbsolutePath: String): String = {
    Option(System.getProperty(JavaLibraryPath)).fold(dirAbsolutePath)(oldValue ⇒ s"$dirAbsolutePath${File.pathSeparator}$oldValue")
  }

  private[sigar] def copy(lib: String, tmpDir: File) {
    val target = new File(tmpDir, lib)
    if (target.exists()) return
    write(classOf[Loader].getResourceAsStream(lib), target)
  }

  private[sigar] def createTmpDir(baseTmp: File): File = {
    val tmpDir = new File(baseTmp, s"sigar-$Version")
    if (!tmpDir.exists()) {
      if (!tmpDir.mkdirs()) throw new RuntimeException(s"Could not create temp sigar directory: ${tmpDir.getAbsolutePath}")
    }
    if (!tmpDir.isDirectory) throw new RuntimeException(s"sigar temp directory path is not a directory: ${tmpDir.getAbsolutePath}")
    if (!tmpDir.canWrite()) throw new RuntimeException(s"sigar temp directory not writeable: ${tmpDir.getAbsolutePath}")
    tmpDir
  }

  private[sigar] def loadIndex(): List[String] = {
    val libs = new ArrayList[String]()
    val is = classOf[Loader].getResourceAsStream(IndexFile)

    for (line ← Source.fromInputStream(is).getLines()) {
      val currentLine = line.trim()
      libs add currentLine
    }
    libs
  }

  private[sigar] def write(input: InputStream, to: File) {
    val out = new FileOutputStream(to)
    try {
      transfer(input, out)
    } finally {
      out.close()
    }
  }

  private[sigar] def transfer(input: InputStream, out: OutputStream) {
    val buffer = new Array[Byte](8192)

    @tailrec def transfer() {
      val read = input.read(buffer)
      if (read >= 0) {
        out.write(buffer, 0, read)
        transfer()
      }
    }
    transfer()
  }

  private[sigar] def printBanner(sigar: Sigar) = {
    val os = OperatingSystem.getInstance

    def loadAverage(sigar: Sigar) = {
      try {
        val average = sigar.getLoadAverage
        (average(0), average(1), average(2))
      } catch {
        case s: org.hyperic.sigar.SigarNotImplementedException ⇒ {
          (0d, 0d, 0d)
        }
      }
    }

    def uptime(sigar: Sigar) = {
      def formatUptime(uptime: Double): String = {
        var retval: String = ""
        val days: Int = uptime.toInt / (60 * 60 * 24)
        var minutes: Int = 0
        var hours: Int = 0

        if (days != 0) {
          retval += s"$days ${(if ((days > 1)) "days" else "day")}, "
        }

        minutes = uptime.toInt / 60
        hours = minutes / 60
        hours %= 24
        minutes %= 60

        if (hours != 0) {
          retval += hours + ":" + minutes
        } else {
          retval += minutes + " min"
        }
        retval
      }

      val uptime = sigar.getUptime
      val now = System.currentTimeMillis()

      s"up ${formatUptime(uptime.getUptime())}"
    }

    val message =
      """
        |
        |  _____           _                 __  __      _        _          _                     _          _
        | / ____|         | |               |  \/  |    | |      (_)        | |                   | |        | |
        || (___  _   _ ___| |_ ___ _ __ ___ | \  / | ___| |_ _ __ _  ___ ___| |     ___   __ _  __| | ___  __| |
        | \___ \| | | / __| __/ _ \ '_ ` _ \| |\/| |/ _ \ __| '__| |/ __/ __| |    / _ \ / _` |/ _` |/ _ \/ _` |
        | ____) | |_| \__ \ ||  __/ | | | | | |  | |  __/ |_| |  | | (__\__ \ |___| (_) | (_| | (_| |  __/ (_| |
        ||_____/ \__, |___/\__\___|_| |_| |_|_|  |_|\___|\__|_|  |_|\___|___/______\___/ \__,_|\__,_|\___|\__,_|
        |         __/ |
        |        |___/
        |
        |              [System Status]                                                    [OS Information]
        |     |--------------------------------|                             |----------------------------------------|
        |            Up Time: %-10s                                           Description: %s
        |       Load Average: %-16s                                            Name: %s
        |                                                                              Version: %s
        |                                                                                 Arch: %s
        |
      """.stripMargin.format(uptime(sigar), os.getDescription, loadAverage(sigar), os.getName, os.getVersion, os.getArch)
    log.info(message)
  }
  class Loader private[sigar]
}
