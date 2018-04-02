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
      "com.typesafe.akka" %%  "akka-testkit"            % akkaVersion  % "test",
      "org.scalatest"     %%  "scalatest"               % "3.0.1"      % "test"
    ),
  ).enablePlugins(JavaAppPackaging)
