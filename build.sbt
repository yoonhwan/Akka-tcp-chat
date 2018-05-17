import Dependencies._
val akkaVersion = "2.5.0"
lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.example",
      scalaVersion := "2.12.4",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "chat",
    libraryDependencies += scalaTest % Test,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %%  "akka-actor"              % akkaVersion,
      "com.typesafe.akka" %%  "akka-slf4j"              % akkaVersion,
      "com.typesafe.akka" %%  "akka-remote"             % akkaVersion,
      "com.typesafe.akka" %%  "akka-multi-node-testkit" % akkaVersion,
      "com.typesafe.akka" %%  "akka-contrib"            % akkaVersion,
      "com.propensive" %% "rapture-base" % "2.0.0-M9",

      "com.github.etaty" %% "rediscala" % "1.8.0",

      "com.typesafe.akka" %%  "akka-testkit"            % akkaVersion  % "test",

    )
  ).enablePlugins(JavaAppPackaging)