package kamon.trace

trait Sampler {
  def decide(spanID: Long): Boolean
}

object Sampler {
  val always = new Constant(true)
  val never = new Constant(false)

  def random(chance: Double): Sampler = {
    assert(chance >= 0D && chance <= 1.0D, "Change should be >= 0 and <= 1.0")

    chance match {
      case 0D       => never
      case 1.0D     => always
      case anyOther => new Random(anyOther)
    }
  }

  class Constant(decision: Boolean) extends Sampler {
    override def decide(spanID: Long): Boolean = decision

    override def toString: String =
      s"Sampler.Constant(decision = $decision)"
  }

  class Random(chance: Double) extends Sampler {
    val upperBoundary = Long.MaxValue * chance
    val lowerBoundary = -upperBoundary

    override def decide(spanID: Long): Boolean =
      spanID >= lowerBoundary && spanID <= upperBoundary

    override def toString: String =
      s"Sampler.Random(chance = $chance)"
  }
}
