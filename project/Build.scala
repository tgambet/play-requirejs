import sbt._
import sbt.Keys._

object PluginBuild extends Build {

  lazy val playRequirejs = Project(
    id = "play-requirejs",
    base = file("."),
    settings = mainSettings
  )

  lazy val mainSettings: Seq[Project.Setting[_]] = Defaults.defaultSettings ++ Seq(
    sbtPlugin := true,
    organization := "org.github.tgambet",
    name := "play-requirejs",
    version := "0.1-SNAPSHOT",
    resolvers ++= Seq(
      Classpaths.typesafeReleases,
      Classpaths.typesafeSnapshots
    ),
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "1.7.1" % "test",
      "org.json4s" %% "json4s-native" % "3.2.4"
    ),
    scalacOptions += "-deprecation"
  ) ++ addSbtPlugin("play" % "sbt-plugin" % "2.1.0")

}
