package org.github.tgambet

import sbt._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.mozilla.javascript._
import org.mozilla.javascript.tools.shell._

object RequireCompiler {

  def jsonify(content: String) = {

    // TODO Seq + fold

    val comments = """(//.*)|(/\*.*?\*/)""".r
    val blanks = """(?m)^\s*\n""".r
    val singleQuotes = """'""".r
    val unquotedKeys = """(\w+):""".r
    val extParenthesis = """(?s)\A\((.*)\)\z""".r

    var r = content
    r = comments.replaceAllIn(r, "")
    r = blanks.replaceAllIn(r, "")
    r = singleQuotes.replaceAllIn(r, "\"")
    r = unquotedKeys.replaceAllIn(r, "\"$1\":")
    r = extParenthesis.replaceFirstIn(r.trim, "$1")
    r
  }

  def loadBuild(build: File): JObject = {
    try {
      parse(jsonify(IO.read(build))).asInstanceOf[JObject]
    } catch {
      case cause: Exception => throw new Exception("Error loading requirejs build file: " + build, cause)
    }
  }

  def createBuildFile(sourceDir: File, targetDir: File, sourceBuild: File, targetBuild: File = new File("build_managed.js")): File = {

    val sourceJson = loadBuild(sourceBuild)

    val dir = targetBuild.toPath.getParent.relativize(targetDir.toPath).toString
    val appDir = targetBuild.toPath.getParent.relativize(sourceDir.toPath).toString

    val defaults = ("optimize" -> "none") ~ ("baseUrl" -> ".")
    val overrides = ("dir" -> dir) ~ ("appDir" -> appDir)

    val targetJson = overrides merge sourceJson merge defaults

    IO.write(targetBuild, pretty(render(targetJson)))

    targetBuild
  }

  def compile(sourceDir: File, targetDir: File, sourceBuild: File, targetBuild: File = new File("build_managed.js")): Unit = {
    compile(createBuildFile(sourceDir, targetDir, sourceBuild, targetBuild))
  }

  def compile(buildFile: File): Unit = {
    ContextFactory.getGlobal.call(new ContextAction {
      def run(ctx: Context) = {
        val scope: Scriptable = {
          val global = new Global
          global.init(ctx)
          ctx.initStandardObjects(global)
        }
        val args = ctx.newArray(scope, Array[Object]("-o", buildFile.getAbsolutePath))
        scope.put("arguments", scope, args)
        val stream = new java.io.InputStreamReader(this.getClass.getClassLoader.getResource("r.js").openConnection().getInputStream())
        ctx.evaluateReader(scope, stream, "r.js", 1, null)
    }})
  }
}
