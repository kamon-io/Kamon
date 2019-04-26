package kamon.bench

import java.util.concurrent.TimeUnit

import kamon.tag.TagSet
import org.openjdk.jmh.annotations._

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@State(Scope.Benchmark)
class TagSetCreationBenchmark {

  @Param(Array("1", "2", "3", "4", "5", "6"))
  var tagCount: Int = 1

  @Benchmark
  def createTagSetFromIndividualKeys(): TagSet = {
    var tags = TagSet.Empty
    tags = tags.withTag("http.method", "POST")
    if(tagCount > 1) tags = tags.withTag("http.url", "http://localhost:8080/test")
    if(tagCount > 2) tags = tags.withTag("http.status_code", 200L)
    if(tagCount > 3) tags = tags.withTag("error", false)
    if(tagCount > 4) tags = tags.withTag("userID", "abcdef")
    if(tagCount > 5) tags = tags.withTag("correlationID", "0123456")

    tags
  }

  @Benchmark
  def createTagSetFromBuilder(): TagSet = {
    val tags = TagSet.builder()
    tags.add("http.method", "POST")
    if(tagCount > 1) tags.add("http.url", "http://localhost:8080/test")
    if(tagCount > 2) tags.add("http.status_code", 200L)
    if(tagCount > 3) tags.add("error", false)
    if(tagCount > 4) tags.add("userID", "abcdef")
    if(tagCount > 5) tags.add("correlationID", "0123456")

    tags.build()
  }
}
