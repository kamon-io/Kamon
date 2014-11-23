/* =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

package kamon.jdbc.instrumentation

import java.sql.{SQLException, DriverManager}

import akka.actor.ActorSystem
import akka.testkit.TestKit
import kamon.trace.TraceRecorder
import org.scalatest.{Matchers, WordSpecLike}

class StatementInstrumentationSpec extends TestKit(ActorSystem("jdbc-spec")) with WordSpecLike with Matchers {

  val connection = DriverManager.getConnection("jdbc:h2:mem:test","SA", "")

  "the StatementInstrumentation" should {
    "bblabals" in {
      TraceRecorder.withNewTraceContext("jdbc-trace") {
        connection should not be null

        val create = "CREATE TABLE Address (Nr INTEGER, Name VARCHAR(128));"
        val createStatement = connection.createStatement()
        createStatement.executeUpdate(create)

        val insert  = "INSERT INTO Address (Nr, Name) VALUES(1, 'foo')"
        val insertStatement = connection.prepareStatement(insert)
        insertStatement.execute()

        val select  =
          """
            |/*this is a comment*/
            |SELECT * FROM Address""".stripMargin
        val selectStatement = connection.prepareCall(select)
        selectStatement.execute()

        val update  = "UPDATE Address SET Name = 'bar' where Nr = 1"
        val updateStatement = connection.createStatement()
        updateStatement.execute(update)

        val delete  = "DELETE FROM Address where Nr = 1"
        val deleteStatement = connection.createStatement()
        deleteStatement.execute(delete)

        intercept[SQLException] {
          val error = "SELECT * FROM NON_EXIST_TABLE"
          val errorStatement = connection.createStatement()
          errorStatement.execute(error)
        }

      }
    }
  }
}

