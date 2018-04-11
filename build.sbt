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
      "com.typesafe.akka" %%  "akka-testkit"            % akkaVersion  % "test"
      
    ),
    libraryDependencies ++= Seq(
      "io.kamon" %% "kamon-core" % "1.1.0",
      "io.kamon" %% "kamon-logback" % "1.0.0",
      "io.kamon" %% "kamon-akka-2.5" % "1.0.1",
      "io.kamon" %% "kamon-prometheus" % "1.0.0",
      "io.kamon" %% "kamon-zipkin" % "1.0.0",
      "io.kamon" %% "kamon-jaeger" % "1.0.1",
      "io.kamon" %% "kamino-reporter" % "1.0.0"
    )
  ).enablePlugins(JavaAppPackaging)
