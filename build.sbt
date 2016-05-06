name := """olx"""

version := "1.0"

scalaVersion := "2.11.7"

// Change this to another test framework if you prefer
libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.4" % "test"

// Uncomment to use Akka
//libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.11"

// SNC
libraryDependencies += "com.typesafe" % "config" % "1.3.0"

//libraryDependencies += "org.seleniumhq.selenium" % "selenium-java" % "2.48.2"

//libraryDependencies += "org.seleniumhq.selenium" % "selenium-firefox-driver" % "2.45.0"

//libraryDependencies += "org.seleniumhq.selenium" % "selenium-safari-driver" % "2.53.0"

//libraryDependencies += "org.seleniumhq.selenium" % "selenium-chrome-driver" % "2.53.0"

libraryDependencies += "com.asprise.ocr" % "java-ocr-api" % "[15,)"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.3"

libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.2.11"

libraryDependencies += "org.json4s" %% "json4s-native" % "3.2.11"

libraryDependencies += "com.github.nscala-time" %% "nscala-time" % "2.10.0"

libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.4"

libraryDependencies += "com.typesafe.akka" % "akka-actor_2.11" % "2.4.2"

libraryDependencies += "com.ibm.icu" % "icu4j" % "57.1"

libraryDependencies += "com.typesafe.play" % "play-ws_2.11" % "2.5.2"

libraryDependencies += "org.jsoup" % "jsoup" % "1.9.1"
