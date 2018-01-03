

name := "exam"

version := "4.0.0"

licenses += "EUPL 1.1" -> url("http://joinup.ec.europa.eu/software/page/eupl/licence-eupl")

lazy val `exam` = (project in file(".")).enablePlugins(PlayJava, PlayEbean)

scalaVersion := "2.11.11"

libraryDependencies ++= Seq(javaJdbc, ehcache, ws, evolutions, filters, guice,
  "org.postgresql" % "postgresql" % "42.1.4",
  "be.objectify" %% "deadbolt-java" % "2.6.0",
  "org.apache.commons" % "commons-email" % "1.5",
  "org.apache.poi" % "poi" % "3.17",
  "org.apache.poi" % "poi-ooxml" % "3.17",
  "org.apache.commons" % "commons-compress" % "1.14",
  "com.opencsv" % "opencsv" % "4.0",
  "org.jsoup" % "jsoup" % "1.10.3",
  "net.sf.biweekly" % "biweekly" % "0.6.1",
  "com.google.code.gson" % "gson" % "2.8.2",
  "com.github.fge" % "json-schema-validator" % "2.2.6",
  "com.jayway.jsonpath" % "json-path" % "2.2.0" % "test",
  "com.icegreen" % "greenmail" % "1.5.5" % "test",
  "org.eclipse.jetty" % "jetty-server" % "9.4.4.v20170414" % "test",
  "org.eclipse.jetty" % "jetty-servlet" % "9.4.4.v20170414" % "test",
  "org.easytesting" % "fest-assert" % "1.4" % "test",
  "org.yaml" % "snakeyaml" % "1.17" % "test",
  "net.jodah" % "concurrentunit" % "0.4.2" % "test"
)

libraryDependencies ++= Seq(
  "org.webjars" %% "webjars-play" % "2.6.2",
  "org.webjars.bower" % "bootstrap-sass" % "3.3.7" // TODO move css away from backend
)

javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")

routesImport += "util.scala.Binders._"

routesGenerator := InjectedRoutesGenerator

sources in (Compile, doc) := Seq.empty
publishArtifact in (Compile, packageDoc) := false


// Webpack build task
//val appPath = "./app/frontend"
//val webpackBuild = taskKey[Unit]("Webpack build task.")

//webpackBuild := {
//  Process("npm run build", file(appPath)).run
//}

//(packageBin in Universal) := ((packageBin in Universal) dependsOn webpackBuild).value

//lazy val frontendDirectory = baseDirectory {_ / appPath}
//PlayKeys.playRunHooks += frontendDirectory.map(WebpackServer(_)).value
