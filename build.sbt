import java.nio.file.Paths

import sbt._
import sbt.Keys._

lazy val akkaHttpVersion = "10.2.0"
lazy val akkaVersion    = "2.6.9"

val buildOlx: TaskKey[Unit] = taskKey[Unit]("Build and copy to ./bin dir")

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization    := "snc",
      scalaVersion    := "2.13.3"
    )),
    name := "olx3",
    version := "0.3.0",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http"                % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json"     % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-actor-typed"         % akkaVersion,
      "com.typesafe.akka" %% "akka-stream"              % akkaVersion,
      "ch.qos.logback"    % "logback-classic"           % "1.2.3",
      "com.typesafe.akka" %% "akka-http-testkit"        % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion     % Test,
      "org.scalatest"     %% "scalatest"                % "3.0.8"         % Test
    )
    , // https://mvnrepository.com/artifact/org.jsoup/jsoup
    libraryDependencies += "org.jsoup" % "jsoup" % "1.13.1"
    ,libraryDependencies ++= Seq(
      "org.reactivemongo" %% "reactivemongo" % "1.0.0",
      "org.reactivemongo" %% "reactivemongo-play-json" % "0.20.12-play29"
    )
    , assemblyJarName in assembly := s"${name.value}.jar"
    , mainClass in assembly := Some("olx.Server")
    , assemblyMergeStrategy in assembly := {
//      case PathList("javax", "servlet", xs @ _*)         => MergeStrategy.first
      case pl @ PathList(ps @ _*) if ps.last == "module-info.class" =>
        println(s"Strategy first was ran on $pl $ps !")
        MergeStrategy.first
//      case "application.conf"                            => MergeStrategy.concat
//      case "unwanted.txt"                                => MergeStrategy.discard
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
    , buildOlx := {
      val assmbly: sbt.File = assembly.value
      IO.createDirectory(new File("bin"))
      IO.copyFile(assmbly, new File("./bin/olx.jar"))
      IO.copyFile(new File("src/main/resources/application.conf"), new File("./bin/olx.conf"))
      IO.copyFile(new File("proxies.tsv"), new File("./bin/proxies.tsv"))
      val bin = new File(baseDirectory.value, "bin").getAbsolutePath
      println(s"Olx parser is copied to $bin")
    }
  )

