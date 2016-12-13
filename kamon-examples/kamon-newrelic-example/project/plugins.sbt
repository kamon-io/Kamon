resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"

resolvers += "Kamon Releases" at "http://repo.kamon.io"

addSbtPlugin("com.ivantopo.sbt" % "sbt-newrelic" % "0.0.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-aspectj" %  "0.9.4")

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.5.2")