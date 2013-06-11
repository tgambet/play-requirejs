name := "play-require"

version := "0.1"

scalaVersion := "2.9.2"

sbtPlugin := true

organization := "org.github.tgambet"

description := "SBT plugin for using require.js in Play 2.1"

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "1.7.1" % "test"
)

addSbtPlugin("play" % "sbt-plugin" % "2.1.0")                                        
