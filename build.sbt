val Http4sVersion = "0.23.29"
val CirceVersion = "0.14.9"
val MunitVersion = "1.0.2"
val LogbackVersion = "1.5.6"
val MunitCatsEffectVersion = "2.0.0"
val scala3Version = "3.4.1"
val http4sVersion = "0.23.29"


lazy val root = (project in file("."))
  .settings(
    organization := "stanikol",
    name := "olx",
    version := "0.4.0",
    scalaVersion := scala3Version,
//    scalacOptions ++= Seq("-Xfatal-warnings", "-Werror"),
    libraryDependencies ++= Seq(
      "org.http4s"      %% "http4s-ember-server" % Http4sVersion,
      "org.http4s"      %% "http4s-ember-client" % Http4sVersion,
      "org.http4s"      %% "http4s-circe"        % Http4sVersion,
      "org.http4s"      %% "http4s-dsl"          % Http4sVersion,
      "org.scalameta"   %% "munit"               % MunitVersion           % Test,
      "org.typelevel"   %% "munit-cats-effect"   % MunitCatsEffectVersion % Test
      // "ch.qos.logback"  %  "logback-classic"     % LogbackVersion         % Runtime,
    ),
    assembly / assemblyMergeStrategy := {
      case "module-info.class" => MergeStrategy.discard
//      case x => (assembly / assemblyMergeStrategy).value.apply(x)
      case x => MergeStrategy.first //TODO
    }
  )
  .settings(
      // https://mvnrepository.com/artifact/org.seleniumhq.selenium/selenium-remote-driver
      libraryDependencies += "org.seleniumhq.selenium" % "selenium-remote-driver" % "4.26.0",
      // https://mvnrepository.com/artifact/org.seleniumhq.selenium/selenium-java
      libraryDependencies += "org.seleniumhq.selenium" % "selenium-java" % "4.26.0",
      libraryDependencies += "co.fs2" %% "fs2-core" % "3.11.0",
      libraryDependencies += "org.typelevel" %% "cats-effect" % "3.5.6",
      libraryDependencies += "org.wvlet.airframe" %% "airframe-log" % "24.11.0",
      libraryDependencies += "org.slf4j" % "slf4j-jdk14" % "2.0.16",
      libraryDependencies +=
        "com.github.joonasvali.naturalmouse" % "naturalmouse" % "2.0.3",
      
      // https://mvnrepository.com/artifact/org.slf4j/slf4j-api
      libraryDependencies += "org.slf4j" % "slf4j-api" % "2.0.16",
      // https://mvnrepository.com/artifact/org.apache.commons/commons-csv
      libraryDependencies += "org.apache.commons" % "commons-csv" % "1.12.0",
      // https://mvnrepository.com/artifact/org.jsoup/jsoup
      libraryDependencies += "org.jsoup" % "jsoup" % "1.18.1",
      libraryDependencies += "org.tpolecat" %% "doobie-h2" % "1.0.0-RC6"

  )