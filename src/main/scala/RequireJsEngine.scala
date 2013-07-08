package net.tgambet.requirejs

import sbt.IO
import java.io.File
import javax.script._
import org.slf4j.LoggerFactory

/**
 * A RequireJS engine is an interface to require.js optimizer, r.js. The optimizer is run as a pre-compiled script
 * through a javax.script.Engine, resulting in exceptions being adequately thrown when the build fails.
 * The internal logger of r.js is mapped to an slf4j logger of this class name (nothing is directly printed on the
 * console unless you configure it).
 */
class RequireJsEngine {

  import RequireJsEngine.logger

  /**
   * A javax.script.ScriptEngine configured to be compatible with r.js. Specifically it completes the global scope with
   * methods that are expected by r.js when running in a Rhino shell, but do not exist when running through a ScriptEngine.
   * It also adds to the scope a logger in the format expected by r.js.
   */
  val engine: ScriptEngine = {
    logger.debug("Initializing r.js engine")

    val engine = new ScriptEngineManager().getEngineByName("JavaScript")
    val globals = engine.createBindings()

    // make the engine itself available in the scope
    globals.put("engine", engine)

    // Missing rhino methods
    // https://developer.mozilla.org/en/docs/Rhino/Shell#readFile.28path_.5B.2C_characterCoding.29
    // https://developer.mozilla.org/en/docs/Rhino/Shell#load.28.5Bfilename.2C_....5D.29
    val engineFix = new Object {
      def load(file: String) = engine.eval(readFile(file))
      def readFile(fileName: String) = IO.read(new File(fileName))
      def quit(code: Int) {}                  // necessary to compile but not called
      def print(s: String) { logger.info(s) } // overwrite to avoid setting a custom Writer
    }
    globals.put("engineFix", engineFix)

    // The javascript logger mapped to ours. `trace` is used as `debug` by r.js.
    val jLogger = new Object {
      def info(s: String)  { s.split("\n").foreach( s => logger.info(s) ) }
      def warn(s: String)  { s.split("\n").foreach( s => logger.warn(s) ) }
      def error(s: String) { s.split("\n").foreach( s => logger.error(s) ) }
      def trace(s: String) { s.split("\n").foreach( s => logger.debug(s) ) }
    }
    globals.put("jlogger", jLogger)

    engine.setBindings(globals, ScriptContext.GLOBAL_SCOPE)
    engine
  }

  /**
   * The compiled optimizer script. Some modifications were made to the source of r.js:
   * - always throw an exception when failing, instead of throwing only if the logLevel is set to silent.
   * - use a logger present in the scope instead of indiscriminately calling print() for all levels.
   * - use the engine passed in the scope instead of loading a rhino engine.
   */
  val rjs = {
    logger.debug("Compiling r.js")

    // Copy the method of the engineFix object to the root scope
    val addMethodsToScope =
      """
        |for(var fn in <name>) {
        |  if(typeof <name>[fn] === 'function') {
        |    this[fn] = (function() {
        |      var method = <name>[fn];
        |      return function() {
        |        return method.apply(<name>, arguments);
        |      };
        |    })();
        |  }
        |};
      """.stripMargin.replaceAll("<name>", "engineFix").replaceAll("\n"," ")

    val stream = this.getClass.getClassLoader.getResource("r-mod.js").openConnection().getInputStream()
    val rjs = IO.readStream(stream)

    engine match {
      case engine: Compilable => engine.compile(addMethodsToScope + rjs)
      case _ => sys.error("Engine is not a Compilable")
    }
  }

  /**
   * Call r.js with a list of arguments. Expected arguments are the same as when using r.js on the command-line.
   * @param args arguments to pass to r.js
   */
  def build(args: Array[String]) {
    logger.debug("Calling r.js with arguments: " + args.mkString(" "))
    val bindings = engine.createBindings()
    bindings.put("arguments", args)
    rjs.eval(bindings)
  }

  /**
   * Call r.js only passing a build file. Equivalent to calling build(Array("-o", buildFile.toString)).
   * @param buildFile a require.js build file
   */
  def build(buildFile: File) { build(Array[String]("-o", buildFile.toString)) }

}

object RequireJsEngine {

  val logger = LoggerFactory.getLogger(classOf[RequireJsEngine])

}
