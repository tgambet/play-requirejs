package org.github.tgambet

import sbt._
import util.parsing.json.{JSONObject, JSON}
import org.mozilla.javascript._
import org.mozilla.javascript.tools.shell._

object RequireCompiler {

  def jsonify(content: String) = {
    val comments = """(//.*)|(/\*.*?\*/)""".r
    val blanks = """(?m)^\s*\n""".r
    val quoteKeys = """(\w+):""".r
    val paren = """(?s)\A\((.*)\)\z""".r

    var r = content
    r = comments.replaceAllIn(r, "")
    r = blanks.replaceAllIn(r, "")
    r = quoteKeys.replaceAllIn(r, "\"$1\":")
    r = paren.replaceFirstIn(r.trim, "$1")
    r
  }

  def generateBuildConfig(buildFile: File, sourceDir: File, outputDir: File): JSONObject = {
    val content = IO.read(buildFile)
    val json = JSON.parseRaw(jsonify(content))
    if (!json.isDefined)
      throw new Exception("Error parsing requirejs build file: \n" + buildFile)
    json.get match {
      case json: JSONObject => {
        val obj = if (json.obj.isDefinedAt("mainConfigFile")) {
          val configFile = json.obj("mainConfigFile").asInstanceOf[String]
          json.obj + (("mainConfigFile", (sourceDir / configFile).getAbsolutePath))
        } else json.obj
        JSONObject(obj
            + (("dir", outputDir.getAbsolutePath))
            + (("appDir", sourceDir.getAbsolutePath)))
      }
      case _ => {
        throw new Exception("Not a valid requirejs build file: \n" + buildFile)
      }
    }
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
