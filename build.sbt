name := "olx"

version := "0.2"

scalaVersion := "2.11.7"

resolvers +=
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

libraryDependencies ++= Seq(
  "org.jsoup" % "jsoup" % "1.10.2"
  ,"com.typesafe" % "config" % "1.3.1"
  ,"com.typesafe.akka" %% "akka-http" % "10.0.9"
  ,"com.typesafe.akka" %% "akka-stream" % "2.5.3"
  ,"com.typesafe.akka" %% "akka-http-spray-json" % "10.0.7"
  ,"com.janschulte" %% "akvokolekta" % "0.1.0-SNAPSHOT"
  ,"joda-time" % "joda-time" % "2.9.9"
)


//enablePlugins(JavaAppPackaging)