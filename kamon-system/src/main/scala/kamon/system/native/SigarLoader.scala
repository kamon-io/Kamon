package kamon.system.native

import org.hyperic.sigar.Sigar
import org.hyperic.sigar.SigarProxy
import java.io._
import scalax.io.JavaConverters._
import scalax.io._

import Resource._

import scalax.file.Path
import java.util
import scala.collection.JavaConversions._

object SigarLoader {
  val Version = "1.6.4"
  val JavaLibraryPath = "java.library.path"
  val TmpDir = "java.io.tmpdir"
  val IndexFile = "/kamon/system/native/index"
  val UsrPathField = "usr_paths"

  def init: SigarProxy = init(new File(System.getProperty(TmpDir)))

  private def init(baseTmp: File): SigarProxy = {
    val tmpDir = createTmpDir(baseTmp)
    for (lib ← loadIndex) copy(lib, tmpDir)

    attachToLibraryPath(tmpDir)

    try {
      val sigar = new Sigar
      sigar.getPid
      sigar
    } catch {
      case t: Throwable ⇒
        throw new RuntimeException("Failed to load sigar", t)
    }
  }

  private val usrPathField = {
    val usrPathField = classOf[ClassLoader].getDeclaredField(UsrPathField)
    usrPathField.setAccessible(true)
    usrPathField
  }

  private def attachToLibraryPath(dir: File): Unit = {
    val dirAbsolute = dir.getAbsolutePath
    System.setProperty(JavaLibraryPath, newLibraryPath(dirAbsolute))
    var paths = usrPathField.get(null).asInstanceOf[Array[String]]
    if (paths == null) paths = new Array[String](0)
    for (path ← paths) if (path == dirAbsolute) return
    val newPaths = util.Arrays.copyOf(paths, paths.length + 1)
    newPaths(newPaths.length - 1) = dirAbsolute
    usrPathField.set(null, newPaths)
  }

  private def newLibraryPath(dirAbsolutePath: String): String = {
    Option(System.getProperty(JavaLibraryPath)).fold(dirAbsolutePath)(oldValue ⇒ s"$dirAbsolutePath${File.pathSeparator}$oldValue")
  }

  private def copy(lib: String, tmpDir: File) {
    val dest: Path = Path(new File(tmpDir, lib))
    if (dest.exists) return
    val currentFile = Resource.fromInputStream(classOf[Loader].getResourceAsStream("" + lib))
    currentFile.acquireFor(current ⇒ dest.doCopyFrom(current.asInput))
  }

  private def createTmpDir(baseTmp: File): File = {
    val path = Path(new File(baseTmp, s"sigar-$Version"))
    path.createDirectory(failIfExists = false)
    path.jfile
  }

  private def loadIndex: util.List[String] = {
    val libs = new util.ArrayList[String]
    val input = fromInputStream(classOf[Loader].getResourceAsStream(IndexFile))
    input.lines().foreach(libs.add)
    libs
  }
}

class Loader