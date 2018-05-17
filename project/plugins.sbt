resolvers += Classpaths.typesafeReleases
resolvers += Resolver.url("bintray-sbt-plugins", url("https://dl.bintray.com/sbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns)
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.4")
