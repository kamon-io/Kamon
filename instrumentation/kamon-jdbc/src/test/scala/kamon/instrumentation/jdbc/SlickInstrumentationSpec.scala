/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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

package kamon.instrumentation.jdbc

import kamon.Kamon
import kamon.tag.Lookups.plain
import kamon.testkit.{InitAndStopKamonAfterAll, TestSpanReporter}
import kamon.trace.Span
import org.scalatest.OptionValues
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import slick.jdbc.H2Profile.api._

import scala.concurrent.Await
import scala.concurrent.duration._

class SlickInstrumentationSpec extends AnyWordSpec with Matchers with Eventually with TestSpanReporter with OptionValues
    with InitAndStopKamonAfterAll {

  // NOTE: There is no need for dedicated AsyncExecutor instrumentation because the kamon-executors module
  //       instrumentation will pick up the runnables from Slick and get the job done. These tests are just for
  //       validating that everything is working as expected.

  "the Slick instrumentation" should {
    "propagate the current Context to the AsyncExecutor on Slick" in {
      applyConfig("kamon.instrumentation.jdbc.add-db-statement-as-span-tag=always")

      val db = setup(Database.forConfig("slick-h2"))
      val parent = Kamon.spanBuilder("parent").start()

      Kamon.runWithSpan(parent) {
        db.run(addresses += (5, "test"))
      }

      eventually(timeout(10 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.kind shouldBe Span.Kind.Client
        span.trace shouldBe parent.trace
        span.tags.get(plain("db.statement")) shouldBe """insert into "Address" ("Nr","Name")  values (?,?)"""
      }
    }

    "propagate the current Context to the AsyncExecutor on Slick when using a connection pool" in {
      val db = setup(Database.forConfig("slick-h2-with-pool"))
      val parent = Kamon.spanBuilder("parent").start()

      Kamon.runWithSpan(parent) {
        db.run(addresses += (5, "test"))
      }

      eventually(timeout(10 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.kind shouldBe Span.Kind.Client
        span.trace shouldBe parent.trace
        span.tags.get(plain("db.statement")) shouldBe """insert into "Address" ("Nr","Name")  values (?,?)"""
        span.metricTags.get(plain("jdbc.pool.name")) shouldBe "slick-h2-with-pool"
        span.metricTags.get(plain("jdbc.pool.vendor")) shouldBe "hikari"
      }
    }
  }

  class Addresses(tag: Tag) extends Table[(Int, String)](tag, "Address") {
    def id = column[Int]("Nr", O.PrimaryKey)
    def name = column[String]("Name")
    def * = (id, name)
  }

  val addresses = TableQuery[Addresses]

  def setup(db: Database): Database = {
    val ops = DBIO.seq(
      addresses.schema.create,
      addresses += (1, "hello"),
      addresses += (2, "world"),
      addresses += (3, "with"),
      addresses += (4, "kamon")
    )

    Await.result(db.run(ops), 10 seconds)
    db
  }

}
