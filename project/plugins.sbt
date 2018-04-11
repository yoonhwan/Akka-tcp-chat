resolvers += Classpaths.typesafeReleases
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.2")


resolvers += Resolver.bintrayRepo("kamon-io", "snapshots")
resolvers += Resolver.bintrayRepo("kamon-io", "sbt-plugins")
addSbtPlugin("io.kamon" % "sbt-aspectj-runner" % "1.1.0")