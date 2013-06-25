package net.tgambet

import org.scalatest.FunSpec
import org.json4s._
import sbt._
import scala.Predef._
import scala.Predef.Function
import scala.Function
import scala.Some

class RequireCompilerSpec extends FunSpec {

  import RequireCompiler.PathUtils.path

  val target = new File("target/requirejs")
  val resources = new File("src/test/resources/")
  val buildFolder = "javascripts-build"

  def withUseCase(useCase: String)(f: Function[File, Any]) = {
    val srcDir = resources / useCase
    val newSrcDir = target / useCase
    IO.delete(newSrcDir)
    IO.copyDirectory(srcDir, newSrcDir)
    f(newSrcDir)
    //IO.delete(newSrcDir)
  }

  describe("RequireCompiler companion object") {

    it("should succesfully load require.js build files written in js or json syntax") {

      import RequireCompiler.load

      val buildJs   = resources / "builds" / "build.js"
      val buildJson = resources / "builds" / "build.json"

      assert(load(buildJs) === load(buildJson))

      assert {
        load(buildJs) \ "modules" match {
          case JArray(mods) => mods.length == 2
          case _ => false
        }
      }
    }

    it ("should succesfully parse a build report") {

      import RequireCompiler.PathUtils.path

      assert {
        RequireCompiler.parseReport( resources / "reports" / "build1.txt" ).all.toSet === Set(
          (path("lib/jquery.js") -> path("main.js")),
          (path("main.js") -> path("main.js"))
        )
      }

    }

    it ("should succesfully compile a project given a valid build file") {

      withUseCase("use_case_1"){ sources =>

        RequireCompiler.compile(sources / "build.js")

        assert((sources / "main-min.js").exists())
      }

      withUseCase("use_case_2"){ sources =>

        RequireCompiler.compile(sources / "build.js")

        assert((sources / "build" / "main.js").exists())

        assert{
          RequireCompiler.parseReport(sources / "build" / "build.txt").all.toSet === Set(
            (path("lib/jquery.js") -> path("main.js")),
            (path("main.js")       -> path("main.js"))
          )
        }
      }
    }
  }

  describe("A RequireCompiler instance") {

    import RequireCompiler.Defaults

    it("should build from './" + Defaults.source + "' to './" + Defaults.target + "' when using default parameters") {
      val compiler = new RequireCompiler()
      val defaultTarget = file(Defaults.target)
      val defaultSource = file(Defaults.source)

      assert(compiler.source === defaultSource)
      assert(compiler.target === defaultTarget)

      IO.write(defaultSource / "main.js", "")

      assert(compiler.compile().all.toSet === Set.empty)
      assert(compiler.compile(Set("main")).all.toSet === Set (
        (defaultSource / "main.js" -> defaultTarget / "main.js")
      ))
      assert((defaultTarget / "main.js").exists())
      assert((defaultTarget / "build.txt").exists())

      IO.delete(defaultTarget)
      IO.delete(defaultSource)
    }

    it("should build from '<base>/" + Defaults.source + "' to '<base>/" + Defaults.target + "' when baseDirectory is defined") {
      withUseCase("use_case_2") { base =>
        val compiler = new RequireCompiler(baseDir = Some(base))
        val source = base / Defaults.source
        val target = base / Defaults.target

        assert(compiler.source === source)
        assert(compiler.target === target)
        assert(compiler.compile().all.toSet === Set.empty)
        assert(compiler.compile(Set("main")).all.toSet === Set (
          (source / "main.js" -> target / "main.js"),
          (source / "lib" / "jquery.js" -> target / "main.js")
        ))
        assert((target / "main.js").exists())
        assert((target / "build.txt").exists())
      }
    }

    it("should build from the 'sourceDir' and 'targetDir' parameters when defined") {
      withUseCase("use_case_2") { source =>
        val target = source.getParentFile / "requireJs-target"
        val compiler = new RequireCompiler(
          sourceDir = Some(source),
          targetDir = Some(target)
        )
        assert(compiler.source === source)
        assert(compiler.target === target)
        assert(compiler.compile().all.toSet === Set.empty)
        assert((target / "build.txt").exists())
      }
    }

    it("should build from the 'sourceDir' and 'targetDir' parameters when defined, resolved against baseDir if defined") {
      withUseCase("use_case_3"){ base =>
        val source = file("js")
        val target = file("js-build")
        val compiler = new RequireCompiler(
          sourceDir = Some(source),
          targetDir = Some(target),
          baseDir = Some(base)
        )
        assert(compiler.source === base / source.toString)
        assert(compiler.target === base / target.toString)
        assert(compiler.compile().all.toSet === Set.empty)
        assert((base / target.toString / "build.txt").exists())
      }
    }

    it("should build from the 'appDir' to the 'dir' options of the buildFile when defined, resolved against its parent folder") {
      withUseCase("use_case_2") { base =>
        val buildFile = base / "build.js"
        val compiler = new RequireCompiler(buildFile = Some(buildFile))
        val source = base / "javascripts"
        val target = base / "build"
        assert(compiler.source === source)
        assert(compiler.target === target)
        assert(compiler.compile().all.toSet === Set (
          (source / "main.js" -> target / "main.js"),
          (source / "lib" / "jquery.js" -> target / "main.js")
        ))
        assert((target / "build.txt").exists())
      }
    }

    it("should build from the 'appDir' to the 'dir' options of the buildFile when defined, resolved against the baseDirectory if defined") {
      withUseCase("use_case_2") { base =>
        val buildFile = resources / "use_case_2" /  "build.js"
        val compiler = new RequireCompiler(
          buildFile = Some(buildFile),
          baseDir = Some(base)
        )
        val source = base / "javascripts"
        val target = base / "build"
        assert(compiler.source === source)
        assert(compiler.target === target)
        assert(compiler.compile().all.toSet === Set (
          (source / "main.js" -> target / "main.js"),
          (source / "lib" / "jquery.js" -> target / "main.js")
        ))
        assert(compiler.compile(Set.empty[String]).all.toSet === Set.empty)
        assert((target / "build.txt").exists())
      }
    }

    it("should give priority to the 'sourceDir' and 'targetDir' parameters over the buildFile information") {
      val buildFile = target / "use_case_2" / "build.js"
      val source = file("requireJs-src").getAbsoluteFile
      val target_ = file("requireJs-target").getAbsoluteFile
      val compiler = new RequireCompiler(
        buildFile = Some(buildFile),
        sourceDir = Some(source),
        targetDir = Some(target_)
      )
      assert(compiler.source === source)
      assert(compiler.target === target_)
    }

    it ("should compile a more complex project") {

      withUseCase("use_case_3"){base =>

        val source = (base / "js").getAbsoluteFile
        val target = (base / "js-build").getAbsoluteFile

        val require = new RequireCompiler(
          sourceDir = Some(source),
          targetDir = Some(target),
          buildFile = Some(base / "build.js")
        )

        assert {
          require.compile().all.toSet === Set(
            (source / "front.js"            -> target / "front.js"),
            (source / "app" / "app.js"      -> target / "front.js"),
            (source / "lib" / "backbone.js" -> target / "front.js"),
            (source / "app" / "app.js"      -> target / "back.js"),
            (source / "back.js"             -> target / "back.js"),
            (source / "lib" / "jquery.js"   -> target / "back.js"),
            (source / "app" / "admin.js"    -> target / "back.js")
          )
        }

        assert {
          require.compile(Set("front")).all.toSet === Set(
            (source / "front.js"            -> target / "front.js"),
            (source / "app" / "app.js"      -> target / "front.js"),
            (source / "lib" / "backbone.js" -> target / "front.js")
          )
        }
      }
    }

    describe("when a cacheFile is defined") {

      withUseCase("use_case_3"){base =>

        val source = (base / "js").getAbsoluteFile

        val target = (base / "js-build").getAbsoluteFile

        val cache = file(".require") / "cache"

        IO.delete(cache)

        val compiler = new RequireCompiler(
          sourceDir = Some(source),
          targetDir = Some(target),
          buildFile = Some(base / "build.js"),
          cacheFile = Some(cache)
        )

        it("should compile all modules the first time") {

          assert {
            compiler.compile().all.toSet === Set(
              (source / "front.js"            -> target / "front.js"),
              (source / "app" / "app.js"      -> target / "front.js"),
              (source / "lib" / "backbone.js" -> target / "front.js"),
              (source / "app" / "app.js"      -> target / "back.js"),
              (source / "back.js"             -> target / "back.js"),
              (source / "lib" / "jquery.js"   -> target / "back.js"),
              (source / "app" / "admin.js"    -> target / "back.js")
            )
          }

        }

        it("should only compile modules that have dependencies changed since last build then") {

          IO.touch(source / "app" / "admin.js")

          assert {
            compiler.compile().all.toSet === Set(
              (source / "back.js"           -> target / "back.js"),
              (source / "app" / "app.js"    -> target / "back.js"),
              (source / "app" / "admin.js"  -> target / "back.js") ,
              (source / "lib" / "jquery.js" -> target / "back.js")
            )
          }

        }

        it ("shouldn't compile anything if no dependencies have changed") {
          assert {
            compiler.compile().all.toSet === Set()
          }
        }

      }

    }

  }
}

