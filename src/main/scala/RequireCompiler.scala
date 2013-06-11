package org.github.tgambet

import java.io._
import sbt.PlayExceptions.AssetCompilationException

object RequireCompiler {

  // Simply copy resources in the general case. There's probably a better way to do this included in Play.
  def compile(file: File, opts: Seq[String]): (String, Option[String], Seq[File]) = {
    try {
      val source = scala.io.Source.fromFile(file)
      val lines = source.mkString
      source.close()
      (lines, None, Seq.empty)
    } catch {
      case e: Exception => {
        throw AssetCompilationException(Some(file), "Exception: " + e.getMessage, None, None)
      }
    }
  }

  // The actual require.js compilation happens as a side-effect on start and stage.
  def compile(buildFile: File) {
    import org.mozilla.javascript._
    import org.mozilla.javascript.tools.shell._

    val ctx = {
      val c = Context.enter
      c.setOptimizationLevel(-1)
      c
    }
    val scope = {
      val global = new Global
      global.init(ctx)
      ctx.initStandardObjects(global)
    }
    try {
      val args = ctx.newArray(scope, Array[Object]("-o", buildFile.getAbsolutePath))
      scope.put("arguments", scope, args)
      val ir = new java.io.InputStreamReader(this.getClass.getClassLoader.getResource("r.js").openConnection().getInputStream())
      ctx.evaluateReader(scope, ir, "r.js", 1, null)
    } catch { case e: Exception =>
      throw e
    } finally {
      Context.exit()
    }
  }
}
