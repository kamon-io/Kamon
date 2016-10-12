
name := "kamon-autoweave-example"

scalaVersion := "2.11.8"

libraryDependencies += "org.aspectj" % "aspectjweaver" % "1.8.9"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.5"

libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.4.5" % "test"

libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % "2.4.11"

libraryDependencies += "io.kamon" %% "kamon-core" % "0.6.3"

libraryDependencies += "io.kamon" %% "kamon-autoweave" % "0.6.3"

libraryDependencies += "io.kamon" %% "kamon-akka" % "0.6.3"

libraryDependencies += "io.kamon" %% "kamon-log-reporter" % "0.6.3"

libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.16"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.7"

fork in run := true

// Create a new MergeStrategy for aop.xml files
val aopMerge = new sbtassembly.MergeStrategy {
  val name = "aopMerge"
  import scala.xml._
  import scala.xml.dtd._

  def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] = {
    val dt = DocType("aspectj", PublicID("-//AspectJ//DTD//EN", "http://www.eclipse.org/aspectj/dtd/aspectj.dtd"), Nil)
    val file = MergeStrategy.createMergeTarget(tempDir, path)
    val xmls: Seq[Elem] = files.map(XML.loadFile)
    val aspectsChildren: Seq[Node] = xmls.flatMap(_ \\ "aspectj" \ "aspects" \ "_")
    val weaverChildren: Seq[Node] = xmls.flatMap(_ \\ "aspectj" \ "weaver" \ "_")
    val options: String = xmls.map(x => (x \\ "aspectj" \ "weaver" \ "@options").text).mkString(" ").trim
    val weaverAttr = if (options.isEmpty) Null else new UnprefixedAttribute("options", options, Null)
    val aspects = new Elem(null, "aspects", Null, TopScope, false, aspectsChildren: _*)
    val weaver = new Elem(null, "weaver", weaverAttr, TopScope, false, weaverChildren: _*)
    val aspectj = new Elem(null, "aspectj", Null, TopScope, false, aspects, weaver)
    XML.save(file.toString, aspectj, "UTF-8", xmlDecl = false, dt)
    IO.append(file, IO.Newline.getBytes(IO.defaultCharset))
    Right(Seq(file -> path))
  }
}

assemblyMergeStrategy in assembly := {
  case m if m.toLowerCase.endsWith("manifest.mf") => MergeStrategy.discard
  case m if m.toLowerCase.matches("meta-inf.*\\.sf$") => MergeStrategy.discard
  case m if m.toLowerCase.matches("meta-inf.*\\.properties") => MergeStrategy.discard
  case PathList("META-INF", "aop.xml") => aopMerge
  case PathList(ps @ _*) if ps.last endsWith ".txt.1" => MergeStrategy.first
  case "reference.conf"    => MergeStrategy.concat
  case "application.conf"  => MergeStrategy.concat
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
