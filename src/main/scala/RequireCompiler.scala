package net.tgambet

import sbt._
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import util.matching.Regex
import java.nio.file.{Path, Paths}
import javax.script._
import java.io.PrintWriter
import org.json4s.JsonAST.JValue

object RequireCompiler {

  object Defaults {
    val source = "javascripts"
    val target = "javascripts-build"
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

  def compile(buildFile: File): Unit = {
    val mgr: ScriptEngineManager = new ScriptEngineManager()
    val engine: ScriptEngine = {
      val engine = mgr.getEngineByName("JavaScript")
      engine.getBindings(ScriptContext.GLOBAL_SCOPE).put("utils", new JSUtils(engine))
      engine.eval(JSUtils.addToScopeScript)
      engine
    }
    val stream = new java.io.InputStreamReader(this.getClass.getClassLoader.getResource("r.js").openConnection().getInputStream())
    val args = Array[String]("-o", buildFile.toString)
    engine.put("arguments", args)
    engine.put("engine", engine)
    engine.getContext.setWriter(new RequirePrintWriter)
    engine.getContext.setErrorWriter(new RequirePrintWriter)
    engine.eval(stream)
  }

  def parseReport(report: File): Relation[Path, Path] = {
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

  private object JSUtils {

    val addToScopeScript =
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

  }

  private class JSUtils(engine: ScriptEngine) {
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

  private class RequirePrintWriter extends PrintWriter(new java.io.StringWriter()) {
    override def print(s: String) {
      s.split("\n").foreach{line => scala.Console.println("[require] " + line)}
    }
    override def println(s: String) { print(s) }
  }

  object PathUtils {
    def path(s: String) = Paths.get(s)
    def path(s: String, ss: String*) = Paths.get(s, ss: _*)
    case class PathW(path: Path) {
      def / (other: Path) = path.resolve(other)
      //def / (other: String) = path.resolve(other)
      def /~ (other: Path) = path.resolveSibling(other)
      def /~ (other: String) = path.resolveSibling(other)
    }
    implicit def toPath(file: File): Path = file.toPath
    implicit def toFile(path: Path): File = path.toFile
    implicit def toPathW(file: File): PathW = PathW(file.toPath)
    implicit def toPathW1(path: Path): PathW = PathW(path)
  }

}

/**
 *
 * @param sourceDir An optional directory containing the JavaScript assets.
 * @param targetDir An optional directory where to compile assets.
 * @param buildFile An optional require.js buildFile.
 * @param baseDir An optional directory to resolve relative paths in the build file against.
 * @param cacheFile An optional cacheFile. If defined the compiler will only recompile modules that have changed since last build.
 * @param logger A logger. Will log r.js output.
 * @return
 */
class RequireCompiler(
   sourceDir: Option[File] = None,
   targetDir: Option[File] = None,
   buildFile: Option[File] = None,
   baseDir: Option[File] = None,
   cacheFile: Option[File] = None,
   logger: Logger = null) {

  import RequireCompiler._
  import RequireCompiler.PathUtils._

  val base = (baseDir map toPath) orElse (buildFile map (_ /~ path("."))) getOrElse path(".")

  val (source, target) = {
    import RequireCompiler.Defaults
    val config = loadConfig
//    def resolve(f: File, base: Path): File = {
//      (base.resolve(f.toPath)).normalize.toFile
//    }
    val source: File =
      base / ((sourceDir map toPath orElse asPath(config \ "appDir") getOrElse path(Defaults.source))) normalize()
    val target: File =
      base / ((targetDir map toPath orElse asPath(config \ "dir") getOrElse file(Defaults.target))) normalize()
    println("source: " + source)
    println("target: " + target)
    (source, target)
  }

  def compile(): Relation[File, File] = compile(None)

  def compile(modules: Set[String]): Relation[File, File] = compile(Some(modules))

  def compile(modules: Option[Set[String]] = None): Relation[File, File] = {

    //assert baseConfig is not singleFile optimization

    val config = (buildFile map load) getOrElse JObject()

    lazy val baseModules: Option[JArray] = {
      (config \ "modules") match {
        case a: JArray => Some(a)
        case _ => None
      }
    }

    lazy val currentInfo: Map[File, ModifiedFileInfo] = {
      val assets = (source.toFile ** "*") --- (target.toFile ** "*") filter (_.isFile)
      assets.get.map(f => f.getAbsoluteFile -> FileInfo.lastModified(f)).toMap
    }

    val modulesNamesFromCache = cacheFile flatMap (c => newModulesFromCache(c, currentInfo))

    val newModules: JArray =
      (modules orElse modulesNamesFromCache) map (m => getModulesFromConfig(m, config)) orElse baseModules getOrElse JArray(List.empty)

    val buildDir = path(".require").toAbsolutePath
    val newBuild = (buildDir resolve "build.js").toAbsolutePath

    val newJson = {
      // Fields to remove
      val keys = List("dir", "appDir", "keepBuildDir", "logLevel", "modules", "optimize")

      config.removeField{ case JField(name, _) => keys.contains(name)}.asInstanceOf[JObject] ~
        ("dir"    -> (buildDir relativize target.toAbsolutePath).toString) ~
        ("appDir" -> (buildDir relativize source.toAbsolutePath).toString) ~
        ("baseUrl" -> "./") ~
        ("keepBuildDir" -> true) ~
        ("modules" -> newModules) ~
        ("optimize" -> "none") ~
        ("logLevel" -> 1)
    }

    IO.write(newBuild, pretty(render(newJson)))

    RequireCompiler.compile(newBuild)

    val report = target / "build.txt"
    val res = Relation.empty[File, File] ++ (report.exists() match {
      case true =>  parseReport(report).all.map{
        case (a, b) => ((source / a).normalize.toFile, (target / b).normalize.toFile)
      }
      case _ => Set.empty
    })

    cacheFile foreach {cache => Sync.writeInfo(cache, res, currentInfo)(FileInfo.lastModified.format)}
    res
  }

  private def loadConfig = buildFile map load getOrElse JObject()

  private def asPath: JValue => Option[Path] = {
    value => value match {
      case JString(p) => Some(path(p))
      case _ => None
    }
  }

  private def newModulesFromCache(cacheFile: File, currentInfo: Map[File, ModifiedFileInfo]): Option[Set[String]] = {
    val (previousRelation, previousInfo) = Sync.readInfo(cacheFile)(FileInfo.lastModified.format)
    if (!previousInfo.isEmpty) {
      //  inspired by Play's AssetCompiler.scala
      val changedFiles: Seq[File] =
        currentInfo.filter(e => !previousInfo.get(e._1).isDefined || previousInfo(e._1).lastModified < e._2.lastModified).map(_._1).toSeq ++
          previousInfo.filter(e => !currentInfo.get(e._1).isDefined).map(_._1).toSeq
      // find modules dependent on modified files
      val modulesFiles = previousRelation.all.collect {
        case (sourceF, module) if changedFiles.contains(sourceF) => module
      }.toSet
      val modules = modulesFiles.map{f => target.toAbsolutePath.relativize(f).toString.replaceAll("""\.js$""", "")}.toSet
      Some(modules)
    } else {
      None
    }
  }

  private def getModulesFromConfig(names: Set[String], config: JObject): JArray = {
    def findModule(name: String, config: List[JValue]): Option[JObject] =
      config.collect{ case module: JObject if (module \ "name") == JString(name) => module }.headOption
    (config \ "modules") match {
      case JArray(modules) => names.map { name => findModule(name, modules) getOrElse JArray(List(("name" -> name))) }
      case _ => JArray(names.map { name => JObject() ~ ("name" -> name) }.toList)
    }
  }

}
