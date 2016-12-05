resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"

resolvers += "Kamon Releases" at "http://repo.kamon.io"

addSbtPlugin("com.typesafe.sbt" % "sbt-aspectj" % "0.10.6")
addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.8.2")
addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.6.0")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "0.2.1")
addSbtPlugin("com.typesafe.sbt" % "sbt-pgp" % "0.8.1")
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.1")
addSbtPlugin("pl.project13.scala"  % "sbt-jmh" % "0.2.18")


