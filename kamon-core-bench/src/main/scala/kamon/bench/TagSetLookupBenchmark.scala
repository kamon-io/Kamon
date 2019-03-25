package kamon.bench

import java.util.concurrent.TimeUnit

import kamon.tag.TagSet
import org.openjdk.jmh.annotations._
import kamon.tag.Lookups.any

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@State(Scope.Benchmark)
class TagSetLookupBenchmark {

  def builderTags() = TagSet.builder()
    .add("http.url", "http://localhost:8080/test")
    .add("http.status_code", 200L)
    .add("error", false)
    .add("userID", "abcdef")
    .add("correlationID", "0123456")
    .create()

  def keyByKeyTags() = TagSet.Empty
    .withTag("http.url", "http://localhost:8080/test")
    .withTag("http.status_code", 200L)
    .withTag("error", false)
    .withTag("userID", "abcdef")
    .withTag("correlationID", "0123456")


  val builderLeft = builderTags()
  val builderRight = builderTags()
  val keyByKeyLeft = keyByKeyTags()
  val keyByKeyRight = keyByKeyTags()

  @Benchmark
  def equalityOnBuilderTagSets(): Boolean = {
    builderLeft == builderRight
  }

  @Benchmark
  def equalityOnKeyByKeyTagSets(): Boolean = {
    keyByKeyLeft == keyByKeyRight
  }

  @Benchmark
  def anyLookupOnBuilderTagSet(): Any = {
    builderLeft.get(any("userID"))
  }

  @Benchmark
  def anyLookupOnKeyByKeyTagSet(): Any = {
    keyByKeyLeft.get(any("userID"))
  }
}
