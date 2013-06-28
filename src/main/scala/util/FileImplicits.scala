package net.tgambet.util

import java.nio.file.{Path, Paths}
import java.io.File

object FileImplicits {

  def file(s: String) = new File(s)

  case class FileW(file: File) {

    def / (other: File): File = file.toPath.resolve(other.toPath).toFile

    def / (other: String): File = file.toPath.resolve(other).toFile

    def parent: File = {
      // /~ "." normalize()
      if (file.getParentFile != null) {
        file.getParentFile
      } else new File("")
    }

    def isChildOf(other: File) = {
      file.toPath.startsWith(other.toPath)
    }

    def relativeTo(other: File): File = {
      other.toPath.toAbsolutePath.relativize(file.toPath.toAbsolutePath).toFile
    }

    def normalize() = {
      file.toPath.normalize.toFile
    }

  }

  implicit def toFileW(file: File): FileW = FileW(file)

}
