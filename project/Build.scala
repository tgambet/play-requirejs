import sbt._
import sbt.Keys._

object PluginBuild extends Build {

  lazy val playRequirejs = Project(
    id = "play-requirejs", base = file(".")
  )

}
