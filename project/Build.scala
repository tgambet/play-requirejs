import sbt._
import sbt.Defaults.sbtPluginExtra
import sbt.Keys._

object PluginBuild extends Build {

  lazy val playRequirejs = Project(
    id = "play-requirejs",
    base = file("."),
    settings = mainSettings
  )

  lazy val mainSettings: Seq[Project.Setting[_]] = Defaults.defaultSettings ++ Seq(
    sbtPlugin := true,
    parallelExecution in Test := false,
    organization := "net.tgambet",
    name := "play-requirejs",
    version := "0.1-SNAPSHOT",
    resolvers ++= Seq(
      Classpaths.typesafeReleases,
      Classpaths.typesafeSnapshots
    ),
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "2.0.M5b" % "test",
      "org.json4s" %% "json4s-native" % "3.2.4",
      //"play" %% "play-json" % "2.2-SNAPSHOT", // need scala 2.10, hence sbt 0.13
      "com.google.protobuf" % "protobuf-java" % "2.5.0",
      "org.slf4j" % "slf4j-api" % "1.7.5",
      "ch.qos.logback" % "logback-classic" % "1.0.13" % "test"
    ),
    scalacOptions ++= Seq("-deprecation", "-unchecked")
  )// ++ addSbtPlugin("play" % "sbt-plugin" % "2.1.0")



}
