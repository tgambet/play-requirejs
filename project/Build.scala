import sbt._
import sbt.Keys._

object PluginBuild extends Build {

  lazy val playRequire = Project(
    id = "play-require", base = file(".")
  )

}
