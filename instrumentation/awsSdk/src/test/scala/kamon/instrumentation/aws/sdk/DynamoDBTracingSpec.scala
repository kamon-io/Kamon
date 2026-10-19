/*
 *  ==========================================================================================
 *  Copyright © 2013-2022 The Kamon Project <https://kamon.io/>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 *  except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the
 *  License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied. See the License for the specific language governing permissions
 *  and limitations under the License.
 *  ==========================================================================================
 */

package kamon.instrumentation.aws.sdk

import kamon.tag.Lookups.plain
import kamon.testkit.{InitAndStopKamonAfterAll, TestSpanReporter}
import org.scalatest.OptionValues
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.testcontainers.containers.GenericContainer
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.{
  AttributeDefinition,
  CreateTableRequest,
  KeySchemaElement,
  KeyType,
  ProvisionedThroughput
}

import scala.concurrent.duration._
import java.net.URI

class DynamoDBTracingSpec extends AnyWordSpec with Matchers with OptionValues with InitAndStopKamonAfterAll
    with Eventually with TestSpanReporter {
  val dynamoPort = 8000
  val dynamoContainer: GenericContainer[_] = new GenericContainer("amazon/dynamodb-local:latest")
    .withExposedPorts(dynamoPort)

  lazy val client = DynamoDbClient.builder()
    .endpointOverride(URI.create(s"http://${dynamoContainer.getHost}:${dynamoContainer.getMappedPort(dynamoPort)}"))
    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy")))
    .region(Region.US_EAST_1)
    .build()

  "the DynamoDB instrumentation on the SDK" should {
    "create a Span for simple calls" in {
      client.createTable(CreateTableRequest.builder()
        .tableName("people")
        .attributeDefinitions(AttributeDefinition.builder().attributeName("customerId").attributeType("S").build())
        .keySchema(KeySchemaElement.builder().attributeName("customerId").keyType(KeyType.HASH).build())
        .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build())
        .build())

      eventually(timeout(5 seconds)) {
        val span = testSpanReporter().nextSpan().value
        span.operationName shouldBe "CreateTable"
        span.metricTags.get(plain("component")) shouldBe "DynamoDb"
      }
    }
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    dynamoContainer.start()
  }
}
