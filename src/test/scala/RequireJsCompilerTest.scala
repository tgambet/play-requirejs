package net.tgambet.requirejs

import sbt._
import org.json4s._
import org.scalatest.FunSpec

class RequireJsCompilerTest extends FunSpec with UseCases {

  val resources = new File("src/test/resources/")

  describe("A RequireJs compiler") {

    import RequireJsCompiler._

    it("should succesfully load require.js build files written in js or json syntax") {

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

      val source = file("source")
      val target = file("target")

      assert {
        parseReport( resources / "reports" / "build1.txt" , source, target).all.toSet === Set(
          (source / "lib" / "jquery.js" -> target / "main.js"),
          (source / "main.js" -> target / "main.js")
        )
      }
    }

    describe("when instanciated with apply(buildFile)") {

      it("should create a compiler with source and target directories consistent with the build file") {

        withUseCase("use_case_1"){ sources =>

          IO.write(sources / "build.js", "({ dir: 'target', appDir: 'source' })")

          val compiler = RequireJsCompiler(sources / "build.js")

          assert(compiler.buildDir === sources)
          assert(compiler.source === sources / "source")
          assert(compiler.target === sources / "target")
        }
      }

      it("should throw an exception if the target directory is a child of the source directory") {

        withUseCase("use_case_1"){ sources =>

          IO.write(sources / "build.js", "({ dir: 'source/target', appDir: 'source' })")

          intercept[Exception](RequireJsCompiler(sources / "build.js"))
        }
      }

      it("should throw an exception if the build file is a single-file optimization") {

        withUseCase("use_case_1"){ sources =>

          IO.write(sources / "build.js", "({ name: 'main', out: 'main-min.js' })")

          intercept[Exception](RequireJsCompiler(sources / "build.js"))
        }
      }
    }

    describe("when instanciated with apply(sourceDir, targetDir, buildFile)") {

      describe("should create a compiler") {

        it("when buildFile exists and contains no 'dir' or 'appDir' option") {

          withUseCase("use_case_1"){ sources =>

            IO.write(sources / "build.js", "({ })")

            val compiler = RequireJsCompiler(sources / "source", sources / "target", sources / "build.js")

            assert(compiler.buildDir === sources)
            assert(compiler.source === sources / "source")
            assert(compiler.target === sources / "target")
          }
        }

        it("when buildFile exists and contains different 'dir' or 'appDir' options") {

          withUseCase("use_case_1"){ sources =>

            IO.write(sources / "build.js", "({ dir: 'something', appDir: 'somethingElse' })")

            val compiler = RequireJsCompiler(sources / "source", sources / "target", sources / "build.js")

            assert(compiler.buildDir === sources)
            assert(compiler.source === sources / "source")
            assert(compiler.target === sources / "target")
          }
        }

        it("when buildFile does not exists") {

          withUseCase("use_case_1"){ sources =>

            val noBuild = sources / "build-404.js"

            val compiler = RequireJsCompiler(sources / "source", sources / "target", noBuild)

            assert(compiler.buildDir === sources)
            assert(compiler.source === sources / "source")
            assert(compiler.target === sources / "target")
          }
        }
      }
    }

    describe("build()") {

      it ("should succesfully build a project and produce a Relation[File, File] representing dependencies between sources and modules") {

        withUseCase("use_case_2"){ sources =>

          val source = sources / "javascripts"
          val target = sources / "build"

          val compiler = RequireJsCompiler(sources / "build.js")

          assert(compiler.build().all.toSet === Set(
            (source / "lib" / "jquery.js" -> target / "main.js"),
            (source / "main.js"           -> target / "main.js")
          ))
        }

        withUseCase("use_case_2"){ sources =>

          // specifying another target
          val source = sources / "javascripts"
          val target = sources / "build2"

          val compiler = RequireJsCompiler(source, target, sources / "build.js")

          assert(compiler.build().all.toSet === Set(
            (source / "lib" / "jquery.js" -> target / "main.js"),
            (source / "main.js"           -> target / "main.js")
          ))
        }
      }
    }

    describe("build(moduleIds)") {

      it ("should build only the modules in moduleIds") {

        withUseCase("use_case_3"){ sources =>

          val source = sources / "js"
          val target = sources / "build"

          val compiler = RequireJsCompiler(source, target, sources / "build.js")

          assert(compiler.build().all.toSet === Set(
            (source / "front.js"            -> target / "front.js"),
            (source / "app" / "app.js"      -> target / "front.js"),
            (source / "lib" / "backbone.js" -> target / "front.js"),
            (source / "app" / "app.js"      -> target / "back.js"),
            (source / "back.js"             -> target / "back.js"),
            (source / "lib" / "jquery.js"   -> target / "back.js"),
            (source / "app" / "admin.js"    -> target / "back.js")
          ))

          assert(compiler.buildModules(Set("front")).all.toSet === Set(
            (source / "front.js"            -> target / "front.js"),
            (source / "app" / "app.js"      -> target / "front.js"),
            (source / "lib" / "backbone.js" -> target / "front.js")
          ))

          // remove module config from build.js
          IO.write(sources / "build.js", "({})")

          assert(compiler.buildModules(Set("front")).all.toSet === Set(
            (source / "front.js"            -> target / "front.js"),
            (source / "app" / "app.js"      -> target / "front.js"),
            (source / "lib" / "backbone.js" -> target / "front.js")
          ))

          // change the configuration for front
          IO.write(sources / "build.js",
            """({ modules: [{ name: 'front', include: ['lib/jquery'] }] })""")

          assert(compiler.buildModules(Set("front")).all.toSet === Set(
            (source / "front.js"            -> target / "front.js"),
            (source / "app" / "app.js"      -> target / "front.js"),
            (source / "lib" / "backbone.js" -> target / "front.js"),
            (source / "lib" / "jquery.js"   -> target / "front.js")
          ))
        }
      }
    }

    describe("devBuild(cache)") {

      withUseCase("use_case_3"){ base =>

        val source = base / "js"

        val target = base / "js-build"

        val build = base / "build.js"

        val cache = base / "cache"

        IO.delete(cache)

        val compiler = new RequireJsCompiler(
          source = source,
          target = target,
          buildFile = build,
          buildDir = base
        )

        it("should compile all modules the first time") {

          assert {
            compiler.devBuild(cache).all.toSet === Set(
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
            compiler.devBuild(cache).all.toSet === Set(
              (source / "back.js"           -> target / "back.js"),
              (source / "app" / "app.js"    -> target / "back.js"),
              (source / "app" / "admin.js"  -> target / "back.js"),
              (source / "lib" / "jquery.js" -> target / "back.js")
            )
          }

        }

        it("shouldn't compile anything if no dependencies have changed") {

          assert {
            compiler.devBuild(cache).all.toSet === Set()
          }

          IO.touch(source / "lib" / "underscore.js")

          assert {
            compiler.devBuild(cache).all.toSet === Set()
          }

          assert {
            compiler.devBuild(cache).all.toSet === Set()
          }

        }

        it("should recompile everything if the build file has changed") {

          IO.touch(build)

          assert {
            compiler.devBuild(cache).all.toSet === Set(
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
      }
    }
  }

}