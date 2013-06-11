package org.github.tgambet

import sbt._
import sbt.Keys._
import play.Project._

object RequirePlugin extends Plugin {

  val requireEntryPoint = SettingKey[PathFinder]("play-require-entry-point")
  val requireOptions = SettingKey[Seq[String]]("play-require-options")
  val requireWatcher = AssetsCompiler("require",
      { file => (file ** "*.js") },
      requireEntryPoint,
      { (name, min) => name.replace(".js", if (min) ".min.js" else ".js") },
      { (file, options) => RequireCompiler.compile(file, options) },
      requireOptions
  )

  val requireSettings = Seq(
      requireEntryPoint <<= (sourceDirectory in Compile)(base => ((base / "assets" / "javascripts" ** "*.js"))),
      requireOptions := Seq.empty[String],
      resourceGenerators in Compile <+= requireWatcher,
      packageBin in Compile <<= (packageBin in Compile).dependsOn(requireTask)
  )

  val requireTask = (copyResources in Compile, baseDirectory) map { (cr, baseDirectory) =>
    val buildFile = baseDirectory / "project" / "build.js"
    RequireCompiler.compile(buildFile)
    //cr
  }

}

