resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"

resolvers += "Kamon Releases" at "http://repo.kamon.io"

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.6.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-aspectj" % "0.9.4")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.8.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.3.0")

