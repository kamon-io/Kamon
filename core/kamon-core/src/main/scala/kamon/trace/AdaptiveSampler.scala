/*
 * Copyright 2013-2021 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon
package trace

import java.util.concurrent.ThreadLocalRandom

import com.typesafe.config.Config
import kamon.jsr166.LongAdder
import kamon.trace.AdaptiveSampler.{Allocation, OperationSampler, Settings}
import kamon.trace.Trace.SamplingDecision
import kamon.util.EWMA

import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.collection.concurrent.TrieMap

/**
  * An adaptive sampler tries to balance a global throughput goal across all operations in the current application,
  * making the best possible effort to provide sampled traces from all operations and to satisfy all configured rules.
  *
  * This sampler divides the task of balancing the load into two phases: rebalancing and adapting. Rebalancing happens
  * every time a new operation is seen by the sampler and splits the overall throughput allocation across all operations
  * seen so far, only taking the configured throughput goal and rules into account. Adapting happens every second and
  * tries to adjust the sampling rate for each individual operation based on the historical behavior of the operation,
  * the target throughput set during rebalance and take advantage of any "unused" throughput by distributing across more
  * active operations.
  */
class AdaptiveSampler extends Sampler {
  @volatile private var _settings = AdaptiveSampler.Settings.from(Kamon.config())
  private val _samplers = TrieMap.empty[String, AdaptiveSampler.OperationSampler]
  private val _affirmativeDecisionCounter = Sampler.Metrics.samplingDecisions("adaptive", SamplingDecision.Sample)
  private val _negativeDecisionCounter = Sampler.Metrics.samplingDecisions("adaptive", SamplingDecision.DoNotSample)


  override def decide(operation: Sampler.Operation): SamplingDecision = {
    val operationName = operation.operationName()
    val operationSampler = _samplers.getOrElse(operationName, {
      // It might happen that the first time we see an operation under high concurrent throughput we will reach this
      // block more than once, but worse case effect is that we will rebalance the operation samplers more than once.
      val sampler = _samplers.getOrElseUpdate(operationName, buildOperationSampler(operationName))
      rebalance()
      sampler
    })

    val decision = operationSampler.decide()
    if(decision == SamplingDecision.Sample)
      _affirmativeDecisionCounter.increment()
    else
      _negativeDecisionCounter.increment()

    decision
  }

  private def buildOperationSampler(operationName: String): AdaptiveSampler.OperationSampler = synchronized {
    _settings.groups
      .find(g => g.operations.contains(operationName))
      .map(group => {
        group.rules.sample
          .map(fixedSamplingDecision => new OperationSampler.Constant(operationName, fixedSamplingDecision))
          .getOrElse(new OperationSampler.Random(operationName, group.rules))
      }).getOrElse(new OperationSampler.Random(operationName, Settings.Rules(None, None, None)))
  }

  /**
    * Makes the sampler update its internal state and reassign probabilities to all known operations. Rebalancing only
    * happens when new operations appear and does not take the actual operation throughput into account to assign the
    * base throughput to each operation.
    */
  def rebalance(): Unit = synchronized {

    // Step 1: Exclude all operations that have a fixed sampling decision. The global throughput goal is then split
    //         across all dynamically sampled operations, taking into account minimum/maximum throughput rules.
    //
    val dynamicallySampledOperations = randomOperationSamplers()
    val throughputGoal = _settings.throughput
    val basePerOperationThroughput = throughputGoal / dynamicallySampledOperations.size.toDouble

    var allocationDelta = 0D
    var allocationsWithCustomThroughput = 0L
    val allocations = dynamicallySampledOperations.map { operationSampler => {
      import operationSampler.rules

      // On the first round we only assign throughput values to operations that require modifications to their default
      // allocation in order to meet their rules.
      var hasCustomThroughput = false
      var operationThroughput = basePerOperationThroughput
      val minimumCap = rules.minimumThroughput.getOrElse(0D)
      val maximumCap = rules.maximumThroughput.getOrElse(throughputGoal)

      if(operationThroughput < minimumCap) {
        operationThroughput = minimumCap
        hasCustomThroughput = true
      }

      if(operationThroughput > maximumCap) {
        operationThroughput = maximumCap
        hasCustomThroughput = true
      }

      if(hasCustomThroughput) {
        allocationDelta += basePerOperationThroughput - operationThroughput
        allocationsWithCustomThroughput += 1
      }

      Allocation(operationThroughput, hasCustomThroughput, operationSampler)
    }}


    // Step 2: Adjust the per-operation throughput allocation for all operations that do not have any custom throughput
    //         based on configured rules. This section compensates for any deficit or surplus that might arise from
    //         trying to satisfy the configured rules and distributes that difference across all operations that can be
    //         dynamically adjusted without affecting compliance with their rules.
    //
    val correctedAllocations =
      if(allocationsWithCustomThroughput == 0) allocations else {
        val perOperationDelta = allocationDelta / (allocations.size - allocationsWithCustomThroughput).toDouble
        allocations.map(a => if (a.hasFixedThroughput) a else a.copy(throughput = a.throughput + perOperationDelta))
      }


    // Step 3: Apply the base throughput allocations to all operations samplers.
    //
    correctedAllocations.foreach(a => a.operationSampler.updateThroughput(a.throughput))

  }

  /**
    * Uses the throughput information from all operations to update their sampling probability and optionally boost
    * operations if there is any throughput leftover.
    */
  def adapt(): Unit = synchronized {
    val randomSamplers = randomOperationSamplers()
    val operationCount = randomSamplers.size
    val randomSamplersWithStats = randomSamplers
      .map(s => (s, s.throughputAverage()))
      .sortBy { case (sampler, throughputAverage) => Math.min(sampler.throughputCap(), throughputAverage) }


    // Step 1: Go through all operations and try to find chunks of unused throughput that will be later distributed
    //         across operations that could make us of that throughput, if any.
    //
    var totalUnusedThroughput = 0D
    randomSamplersWithStats.foreach { p => totalUnusedThroughput += calculateUnusedThroughput(p) }


    // Step 2: Figure out which operations can make use of additional throughput and boost them accordingly. Since the
    //         samplers are ordered by throughput any unused shares will be accumulated, giving bigger shares to the
    //         operations with more throughput.
    //
    var boostedOperationCount = 0
    randomSamplersWithStats.foreach { case (sampler, throughputAverage) => {
      val canBoost =
        sampler.probability() > 0D &&   // Skips boosting on the first round of each sampler
        totalUnusedThroughput > 0D      // Only boost if there is actually some leftover to boost

      val boost = if(canBoost) {
        val boostShare = totalUnusedThroughput / (operationCount - boostedOperationCount).toDouble
        val proposedThroughput = sampler.throughput() + boostShare

        // Figure out how much we can boost this operation without breaking the expected maximum throughput, and taking
        // into account that even though an operation could reach higher throughput from the limits perspective, its
        // historical throughput is what really tells us what throughput we can expect.
        val maximumAllowedThroughput = Math.min(proposedThroughput, sampler.throughputCap())
        val maximumPossibleThroughput = Math.min(maximumAllowedThroughput, throughputAverage)
        val usableBoost = maximumPossibleThroughput - sampler.throughput()
        boostedOperationCount += 1

        if(usableBoost >= boostShare) {
          totalUnusedThroughput -= boostShare
          boostShare
        } else if(usableBoost > 0D) {
          totalUnusedThroughput -= usableBoost
          boostShare
        } else 0D

      } else 0D

      val probability = (sampler.throughput() + boost) / throughputAverage
      sampler.updateProbability(probability)
    }}
  }

  def reconfigure(newConfig: Config): Unit = {
    _settings = AdaptiveSampler.Settings.from(newConfig)
    _samplers.clear()
  }

  private def randomOperationSamplers(): Seq[OperationSampler.Random] =
    _samplers.collect { case (_, v: OperationSampler.Random) => v } toSeq

  private def calculateUnusedThroughput(pair: (OperationSampler.Random, Double)): Double = {
    val (sampler, throughputAverage) = pair
    val throughputDelta = sampler.throughput() - throughputAverage
    if (throughputDelta > 0D) throughputDelta else 0D
  }

}

object AdaptiveSampler {

  /**
    * Creates a new AdaptiveSampler instance
    */
  def apply(): AdaptiveSampler =
    new AdaptiveSampler()

  /**
    * Creates a new AdaptiveSampler instance
    */
  def create(): AdaptiveSampler =
    apply()

  /**
    * Settings for an adaptive sampler instance. Take a look at the "kamon.trace.adaptive-sampler" section on the
    * configuration file for more details.
    */
  case class Settings (
    throughput: Double,
    groups: Seq[Settings.Group]
  )

  object Settings {

    /**
      * Describes a group of named operations and the rules that apply to them.
      *
      * @param name Identifier for the group.
      * @param operations Names of all operations that will be part of the group.
      * @param rules Rules to should be applied to all operations in this group.
      */
    case class Group (
      name: String,
      operations: Seq[String],
      rules: Rules
    )

    /**
      * Describes the rules that must be applied to certain operation.
      *
      * @param sample Fixed sampled decision. If this parameter is provided any other rules will be ignored.
      * @param minimumThroughput Minimum throughput that the sampler will try to guarantee for a matched operation.
      * @param maximumThroughput Maximum throughput that the sampler will try to guarantee for a matched operation.
      */
    case class Rules (
      sample: Option[SamplingDecision],
      minimumThroughput: Option[Double],
      maximumThroughput: Option[Double]
    )

    /**
      * Constructs an adaptive sampler settings instance from the provided global config object. All relevant
      * configuration settings are take from the "kamon.trace.adaptive-sampler" path.
      */
    def from(config: Config): Settings = {
      val samplerConfig = config.getConfig("kamon.trace.adaptive-sampler")
      val throughput = samplerConfig.getDouble("throughput")
      val groupsConfig = samplerConfig.getConfig("groups")

      val groups = groupsConfig.topLevelKeys.map { groupName =>
        val groupConfig = groupsConfig.getConfig(groupName)

        Settings.Group(
          name = groupName,
          operations = groupConfig.getStringList("operations").asScala.toSeq,
          rules = readRules(groupConfig.getConfig("rules"))
        )
      }.toSeq

      Settings(throughput, groups)
    }

    private def readRules(config: Config): Settings.Rules =
      Settings.Rules(
        sample = ifExists(config, "sample", c => p => toSamplingDecision(c.getString(p))),
        minimumThroughput = ifExists(config, "minimum-throughput", _.getDouble),
        maximumThroughput = ifExists(config, "maximum-throughput", _.getDouble)
      )

    private def toSamplingDecision(text: String): SamplingDecision =
      if (text.equalsIgnoreCase("always")) SamplingDecision.Sample else SamplingDecision.DoNotSample

    private def ifExists[T](config: Config, path: String, extract: Config => String => T): Option[T] =
      if (config.hasPath(path)) Option(extract(config)(path)) else None

  }

  /** Encapsulates throughput allocation information for the rebalancing phase of the adaptive sampler */
  private case class Allocation (
    throughput: Double,
    hasFixedThroughput: Boolean,
    operationSampler: OperationSampler.Random
  )

  /**
    * Sampler that uses internal state to decide whether a given operation should be sampled or not.
    */
  private trait OperationSampler {

    /** Returns a sampling decision. */
    def decide(): SamplingDecision
  }

  private object OperationSampler {

    /** Operation sampler that always returns the same sampling decision. */
    class Constant(operationName: String, val decision: SamplingDecision) extends OperationSampler {
      override def decide(): SamplingDecision =
        decision

      override def toString: String =
        s"Constant{operation=$operationName, decision=$decision}"
    }

    /** Operation sampler that uses random numbers and a continuously updated probability to return a sampling decision */
    class Random(operationName: String, val rules: Settings.Rules) extends OperationSampler {
      @volatile private var _lowerBoundary = 0L
      @volatile private var _upperBoundary = 0L
      @volatile private var _throughput = 0D
      @volatile private var _probability = 0D

      private val _decisions = new LongAdder()
      private val _throughputAverage = EWMA.create()

      private val _decisionsHistorySize = 60
      private val _decisionsPerTick = Array.ofDim[Long](_decisionsHistorySize)
      private var _tickCount = 0L
      private var _decisionsPerTickPos = 0

      def decide(): SamplingDecision = {
        _decisions.increment()

        val random = ThreadLocalRandom.current().nextLong()
        if (random >= _lowerBoundary && random <= _upperBoundary)
          SamplingDecision.Sample
        else
          SamplingDecision.DoNotSample
      }

      def throughputAverage(): Double = synchronized {
        _throughputAverage.add(decisionHistory())
        _throughputAverage.average()
      }

      /**
        * Calculates how many decisions have been made during the last 60 intervals. If less than 60 intervals have
        * passed since the sampler was created, uses the average of the available values to fill in the blanks. This
        * special logic is used to smooth the process during startup of each individual operation.
        */
      private def decisionHistory(): Long = {
        val decisions = _decisions.sumAndReset()
        _decisionsPerTickPos = if(_decisionsPerTickPos == (_decisionsHistorySize - 1)) 0 else _decisionsPerTickPos + 1
        _decisionsPerTick.update(_decisionsPerTickPos, decisions)
        _tickCount += 1

        if(_tickCount < _decisionsHistorySize) {
          val currentSum = _decisionsPerTick.sum
          val currentAverage = Math.floorDiv(currentSum, _tickCount)
          currentSum + (currentAverage * _decisionsHistorySize - _tickCount)

        } else _decisionsPerTick.sum
      }

      def throughput(): Double  =
        _throughput

      def throughputCap(): Double =
        rules.maximumThroughput.getOrElse(Double.MaxValue)

      def probability(): Double =
        _probability

      def updateThroughput(throughput: Double): Unit =
        _throughput = throughput

      def updateProbability(probability: Double): Unit = synchronized {
        val actualProbability = if(probability > 1D) 1D else if (probability < 0D) 0D else probability
        _probability = actualProbability
        _upperBoundary = (Long.MaxValue * actualProbability).toLong
        _lowerBoundary = -_upperBoundary
      }

      override def toString: String =
        s"Constant{operation=$operationName, probability=${_probability}"
    }
  }
}