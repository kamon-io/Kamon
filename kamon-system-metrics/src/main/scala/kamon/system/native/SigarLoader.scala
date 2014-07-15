package kamon.system.native

import java.io._
import java.util
import java.util.{ ArrayList, List }

import org.hyperic.sigar.{ Sigar, SigarProxy }

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.io.Source

object SigarLoader {

  val Version = "1.6.4"
  val JavaLibraryPath = "java.library.path"
  val TmpDir = "java.io.tmpdir"
  val IndexFile = "/kamon/sigar/native/index"
  val UsrPathField = "usr_paths"

  def init(): SigarProxy = init(new File(System.getProperty(TmpDir)))

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
    val target = new File(tmpDir, lib)
    if (target.exists()) return
    write(classOf[Loader].getResourceAsStream(lib), target)
  }

  private def createTmpDir(baseTmp: File): File = {
    val tmpDir = new File(baseTmp, s"sigar-$Version")
    if (!tmpDir.exists()) {
      if (!tmpDir.mkdirs()) throw new RuntimeException(s"Could not create temp sigar directory: ${tmpDir.getAbsolutePath}")
    }
    if (!tmpDir.isDirectory) throw new RuntimeException(s"sigar temp directory path is not a directory: ${tmpDir.getAbsolutePath}")
    if (!tmpDir.canWrite()) throw new RuntimeException(s"sigar temp directory not writeable: ${tmpDir.getAbsolutePath}")
    tmpDir
  }

  private def loadIndex(): List[String] = {
    val libs = new ArrayList[String]()
    val is = classOf[Loader].getResourceAsStream(IndexFile)

    for (line ← Source.fromInputStream(is).getLines()) {
      val currentLine = line.trim()
      libs add currentLine
    }
    libs
  }

  private def write(input: InputStream, to: File) {
    val out = new FileOutputStream(to)
    try { transfer(input, out) }
    finally { out.close() }
  }

  private def transfer(input: InputStream, out: OutputStream) {
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
  class Loader private
}
