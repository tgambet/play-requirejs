package net.tgambet

import sbt._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.mozilla.javascript._
import org.mozilla.javascript.tools.shell._
import util.matching.Regex
import java.nio.file.{Path, Paths}
import javax.script._
import java.io.PrintWriter
import java.util.Date
import java.text.SimpleDateFormat
import com.google.javascript.jscomp.{CheckLevel, JSSourceFile}
import com.google.javascript.jscomp.{Compiler, LoggerErrorManager, CheckLevel, JSError}
import collection.JavaConversions._
import collection.JavaConverters._
import scala.Some
import scala.Some
import org.json4s.JsonAST.JValue

object RequireCompiler {

  def path(s: String) = Paths.get(s)
  def path(s: String, ss: String*) = Paths.get(s, ss: _*)
  case class PathW(path: Path) {
    def / (other: Path) = path.resolve(other)
    def / (other: String) = path.resolve(other)
    def /~ (other: Path) = path.resolveSibling(other)
    def /~ (other: String) = path.resolveSibling(other)
  }
  implicit def toPath(file: File): Path = file.toPath
  implicit def toFile(path: Path): File = path.toFile
  implicit def toPathW(file: File): PathW = PathW(file.toPath)
  implicit def toPathW1(path: Path): PathW = PathW(path)

  /**
   *
   * @param baseDir Base directory to which relative paths in the build file will be resolved against.
   * @param sourceDir Directory containing the JavaScript assets. If not specified it defaults to the parent directory of the build
   *                  of the build file if any, or to the current working directory.
   * @param targetDir The target directory where assets will be compiled.
   * @param modules The modules to build.
   * @param buildFile
   * @param cacheFile
   * @param logger
   * @return
   */
  def compiler(
    baseDir: Option[File] = None,
    sourceDir: Option[File] = None,
    targetDir: Option[File] = None,
    modules: Option[Set[String]] = None,
    buildFile: Option[File] = None,
    cacheFile: Option[File] = None,
    logger: Logger = null): Relation[File, File] = {

    //assert baseConfig is not singleFile optimization

    val build = (buildFile map load) getOrElse JObject()

    val baseModules: Option[JArray] = {
      (build \ "modules") match {
        case a: JArray => Some(a)
        case _ => None
      }
    }

    def pathJ(value: JValue): Option[Path] = {
      value match {
        case JString(p) => Some(path(p))
        case _ => None
      }
    }

    val base   = (baseDir map toPath) orElse (buildFile map (_ /~ path("."))) getOrElse path(".")
    val source = (sourceDir map toPath) getOrElse (base /~ (pathJ(build \ "appDir") getOrElse path("."))) normalize
    val target = (targetDir map toPath) getOrElse (base /~ (pathJ(build \ "dir") getOrElse path("requirejs-build"))) normalize

    lazy val currentInfo: Map[File, ModifiedFileInfo] = {
      val assets = (source.toFile ** "*") --- (target.toFile ** "*") filter (_.isFile)
      assets.get.map(f => f.getAbsoluteFile -> FileInfo.lastModified(f)).toMap
    }

    def newModulesFromCache(cacheFile: File): Option[Set[String]] = {
      val (previousRelation, previousInfo) = Sync.readInfo(cacheFile)(FileInfo.lastModified.format)
      if (!previousInfo.isEmpty) {
        //  inspired by Play's AssetCompiler.scala
        val changedFiles: Seq[File] =
          currentInfo.filter(e => !previousInfo.get(e._1).isDefined || previousInfo(e._1).lastModified < e._2.lastModified).map(_._1).toSeq ++
          previousInfo.filter(e => !currentInfo.get(e._1).isDefined).map(_._1).toSeq
        // find modules dependent on modified files
        val modulesFiles: Traversable[File] = previousRelation.all.collect {
          case (sourceF, module) if changedFiles.contains(sourceF) => module
        }
        val modules = modulesFiles.map{f => target.toAbsolutePath.relativize(f).toString.replaceAll("""\.js$""", "")}.toSet
        Some(modules)
      } else {
        None
      }
    }

    def getModuleConfig(set: Set[String]): JArray = {
      def findModule(name: String): Option[JObject] = baseModules flatMap (
        _.arr.collect{ case module: JObject if (module \ "name") == JString(name) => module }.headOption
      )
      set.map { module => findModule(module) getOrElse JObject(List(JField("name", module))) }
    }

    val newModules: JArray = (modules orElse (cacheFile flatMap newModulesFromCache)) map getModuleConfig orElse baseModules getOrElse JArray(List.empty)

    val buildDir = target / ".require"
    val newBuild = buildDir / "build.js"

    val newJson = {
//    Fields to remove
      val keys = List("dir", "appDir", "keepBuildDir", "logLevel", "modules", "optimize")

      build.removeField{ case JField(name, _) => keys.contains(name)}.asInstanceOf[JObject] ~
        ("dir"    -> (buildDir relativize target).toString) ~
        ("appDir" -> (buildDir relativize source).toString) ~
        ("baseUrl" -> "./") ~
        ("keepBuildDir" -> true) ~
        ("modules" -> newModules) ~
        ("optimize" -> "none") ~
        ("logLevel" -> 1)
    }

    IO.write(newBuild, pretty(render(newJson)))

    native(newBuild)

    val report = target / "build.txt"
    val res = report.exists() match {
      case true => Relation.empty[File, File] ++ parseBuildReport(report).all.map{
        case (a, b) => ((source / a).normalize.toFile, (target / b).normalize.toFile)
      }
      case _ => Relation.empty[File, File]
    }

    cacheFile foreach {cache => Sync.writeInfo(cache, res, currentInfo)(FileInfo.lastModified.format)}

    res
  }

  def load(build: File): JObject = {

    def jsonify(jsContent: String): String = {
      val replacements: Seq[(Regex, String)] = Seq(
        ("""(//.*)""".r -> ""),             // remove single-line comments
        ("""(?s)(/\*.*?\*/)""".r -> ""),    // remove multi-line comments
        ("""(?m)^\s*\n""".r -> ""),         // remove empty lines
        ("""'""".r -> "\""),                // single to double quotes
        ("""(\w+):""".r -> "\"$1\":"),      // quote unquoted keys
        ("""(?s)\A\((.*)\)\z""".r -> "$1")  // remove wrapping parenthesis
      )
      replacements.foldLeft(jsContent){ case (content, (regex, replace)) =>
        regex.replaceAllIn(content.trim, replace)
      }
    }

    try {
      val content = IO.read(build)
      parse(if (build.getName.endsWith(".js")) {jsonify(content)} else {content}).asInstanceOf[JObject]
    } catch {
      case e: Exception => throw new Exception("Error loading buildFile: " + build, e)
    }

  }

  def parseBuildReport(report: File): Relation[Path, Path] = {
    val content = IO.read(report)
    val moduleDependencies = content.split("""\n\n""").map{case a =>
      val arr = a.split("\n----------------\n")
      if (arr.length == 2) {
        val module = Paths.get(arr(0).trim)
        val deps = arr(1).split("""\n""") map (s => Paths.get(s.trim))
        deps map ((_, module))
      } else {
        Array.empty[(Path, Path)]
      }
    }.flatten
    Relation.empty[Path, Path] ++ moduleDependencies
  }

  def native(buildFile: File): Unit = {
    val mgr: ScriptEngineManager = new ScriptEngineManager()
    val engine: ScriptEngine = mgr.getEngineByName("JavaScript")
    augmentEngine(engine)
    val stream = new java.io.InputStreamReader(this.getClass.getClassLoader.getResource("r.js").openConnection().getInputStream())
    val args = Array[String]("-o", buildFile.toString)
    engine.put("arguments", args)
    engine.put("engine", engine)
    engine.getContext.setWriter(new RequirePrintWriter)
    engine.getContext.setErrorWriter(new RequirePrintWriter)
    engine.eval(stream)
  }

  private class RequirePrintWriter extends PrintWriter(new java.io.StringWriter()) {
    override def print(s: String) { scala.Console.println("[require]" + s) }
    override def println(s: String) { print(s) }
  }

  def augmentEngine(engine: ScriptEngine) = {
    engine.getBindings(ScriptContext.GLOBAL_SCOPE).put("utils", new Utils(engine))
    // TODO implement a js Logger
    engine.eval(
      """
        |for(var fn in utils) {
        |  if(typeof utils[fn] === 'function') {
        |    this[fn] = (function() {
        |      var method = utils[fn];
        |      return function() {
        |        return method.apply(utils, arguments);
        |      };
        |    })();
        |  }
        |}
      """.stripMargin
    )
  }

  private class Utils(engine: ScriptEngine) {
    def readFile(fileName: String): String = {
      IO.read(new File(fileName))
    }
    def load(file: String) = {
      engine.eval(readFile(file))
    }
    def quit(i: Int) {
      // TODO quit(1) is error
      if (i == 1) throw new Exception("Error building")
    }
  }

}
