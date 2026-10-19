/* =========================================================================================
 * Copyright © 2013-2019 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License") you may not use this file
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
package kamon.instrumentation.kafka.testutil

import java.io.{BufferedWriter, File, FileWriter, InputStream}
import java.time.{Duration, LocalDateTime}
import java.util.function.Consumer

import kamon.tag.Lookups.plain
import kamon.tag.Tag
import kamon.trace.Span

/**
  * Dump a list of Span.Finished to a file in dot notation and optionally invoke the dot processor.
  *
  * Behaviour is controlled by environment variables:
  * - DOTFILEGENERATOR_ENABLED
  *     if present the generator will run
  * - DOTFILEGENERATOR_ALLTAGS
  *     if present all tags and metricTags will be rendered for a span node
  * - DOTFILEGENERATOR_CMD
  *     if present should contains the shell command for invoking the dot processor after a dot file is created,
  *     e.g. DOTFILEGENERATOR_CMD=dot -Tsvg $FILENAME.dot -o$FILENAME.svg;
  *     The pattern $FILENAME will be replaced with the filename passed to DotFileGenerator.dumpToDotFile
  *
  * For more details see:
  *   https://graphviz.gitlab.io/documentation/
  *   http://viz-js.com/
  */
object DotFileGenerator {

  val StartNode = "start"

  def dumpToDotFile(filename: String, spans: List[Span.Finished]): Unit = {
    val dotText = toDotString(spans, filename, true)
    val file = new File(filename + ".dot")
    val bw = new BufferedWriter(new FileWriter(file))
    bw.write(dotText)
    bw.close()
    val cmd = "dot -Tjpeg $FILENAME.dot | feh -".replace("$FILENAME", filename)
    val rc = executeShellCommand("sh", "-c", cmd)
    if (rc == 0) {
      println(s"DotFileGenerator: wrote and processed file: ${file.getAbsolutePath}")
    } else {
      println(s"DOT generation failed! rc=$rc, cmd='$cmd'")
    }
  }

  private def executeShellCommand(cmd: String*): Int = {
    import java.io.{BufferedReader, InputStreamReader}
    class StreamGobbler(val inputStream: InputStream, val consumer: String => Unit) extends Runnable {
      override def run(): Unit = {
        new BufferedReader(new InputStreamReader(inputStream)).lines.forEach(new Consumer[String] {
          override def accept(t: String): Unit = println(t)
        })
      }
    }
    import java.util.concurrent.Executors
    val builder = new ProcessBuilder
    builder.command(cmd: _*)
    builder.directory(new File("."))
    val process = builder.start
    val streamGobbler = new StreamGobbler(process.getInputStream, println)
    Executors.newSingleThreadExecutor.submit(streamGobbler)
    process.waitFor
  }

  private def toDotString(spans: List[Span.Finished], name: String, allTags: Boolean = true): String = {
    def filterTags(span: Span.Finished): List[(String, String)] = {
      if (allTags) {
        (span.tags.iterator.map(t => t.key -> Tag.unwrapValue(t).toString) ++
        span.metricTags.iterator.map(t => s"metric - ${t.key}" -> Tag.unwrapValue(t).toString)).toList
      } else {
        val optionalMetricTags = List("kafka.topic", "kafka.sink.topic", "kafka.source.topic")
        val commonTags = List(
          "span.kind" -> span.tags.get(plain("span.kind")),
          "hasError" -> span.hasError.toString
        )
        commonTags ++ optionalMetricTags.map(t => t -> Option(span.tags.get(plain(t)))).filter(_._2.isDefined).map(t =>
          (t._1, t._2.get)
        )
      }
    }
    def getLabel(s: Span.Finished) = {
      s"""<
         | <table border="0" cellborder="0" cellspacing="0">
         | <tr><td><b>${s.operationName}</b></td></tr>
         | <tr><td>start: ${s.from}</td></tr>
         | <tr><td>duration: ${Duration.between(s.from, s.to).toMillis} ms</td></tr>
         | ${filterTags(s).map(t => s"<tr><td>${t._1}=${t._2}</td></tr>").mkString("\n")}
         | </table>
         |>""".stripMargin
    }

    def getShapeAndStyleForComponent(s: Span.Finished): (String, String) = {
      val comp = s.metricTags.get(plain("component"))
      val opName = s.operationName
      (comp, opName) match {
        case ("kafka.stream", _)      => ("box", "rounded,bold")
        case ("kafka.stream.node", _) => ("box", "rounded")
        case (_, "poll")              => ("box", "dotted")
        case (_, "send")              => ("box", "dotted")
        case _                        => ("oval", "solid")
      }
    }

    def createNode(s: Span.Finished) = {
      val (shape, style) = getShapeAndStyleForComponent(s)
      s""" "${s.id.string}" [shape="$shape"; label=""" + getLabel(s) + s"""; style="$style"];"""
    }

    def createNodes = {
      spans.map { s =>
        createNode(s)
      }.mkString("\n")
    }

    def getParent(s: Span.Finished) = {
      s.parentId.string match {
        case "" =>
          StartNode
        case s => s
      }
    }

    def createParentEdges = {

      spans.map(s => {
        val sourceNode = getParent(s)
        s""" "$sourceNode" -> "${s.id.string}" [style="${if (sourceNode == StartNode) "dotted" else "bold"}"];"""
      }).mkString("\n")
    }

    def createLinkEdges = {
      spans.flatMap(sTarget =>
        sTarget.links.filter(_.spanId.string != "").map(sSource =>
          s""" "${sSource.spanId.string}" -> "${sTarget.id.string}" [style="dashed"];"""
        )
      ).mkString("\n")
    }

    def createSubgraphsPerTrace: String = {
      spans.map(_.trace.id.string).distinct.map { traceId =>
        s"""
           |	subgraph cluster_$traceId {
           |		label="Trace $traceId";
           |    style="dashed";
           |    ${spans.filter(_.trace.id.string == traceId).map(createNode).mkString("\n")}
           |	}
           |""".stripMargin
      }.mkString("\n")
    }

    s"""
       |digraph G {
       | label=<
       |   <table border="0" cellborder="0" cellspacing="0">
       |    <tr><td><b>$name</b></td></tr>
       |    <tr><td>${LocalDateTime.now()}</td></tr>
       |   </table>
       | >
       | $createSubgraphsPerTrace
       | $createParentEdges
       | $createLinkEdges
       |}
       |""".stripMargin
  }
}
