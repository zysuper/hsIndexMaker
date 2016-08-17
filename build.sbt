name := "hsIndexMaker"

version := "1.0"

scalaVersion := "2.11.8"

javacOptions ++= Seq("-encoding", "GBK")
scalacOptions ++= Seq("-encoding", "GBK")

libraryDependencies += "com.github.scopt" %% "scopt" % "3.5.0"

libraryDependencies ++= Seq(
  "org.scalikejdbc" %% "scalikejdbc" % "2.4.2",
  "ch.qos.logback" % "logback-classic" % "1.1.7",
  "org.scalikejdbc" %% "scalikejdbc-test" % "2.4.2" % "test",
  "org.scalikejdbc" %% "scalikejdbc-config" % "2.4.2"
)

libraryDependencies += "org.scalactic" %% "scalactic" % "2.2.6"
libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6" % "test"
libraryDependencies += "org.scala-lang" % "scala-xml" % "2.11.0-M4"

resolvers += Resolver.sonatypeRepo("public")

import AssemblyKeys._

// put this at the top of the file

assemblySettings

jarName in assembly := "indexMaker-1.0.jar"

scalikejdbcSettings

