enablePlugins(org.nlogo.build.NetLogoExtension)

name := "Gpt-Extension"
version := "0.0.0"
isSnapshot := true

netLogoExtName      := "gpt"
netLogoClassManager := "org.nlogo.extensions.gpt.GptExtension"
netLogoVersion      := "6.3.0"

scalaVersion          := "2.12.17"
Compile / scalaSource := baseDirectory.value / "src" / "main"
Test / scalaSource    := baseDirectory.value / "src" / "test"
scalacOptions        ++= Seq("-deprecation", "-unchecked", "-Xfatal-warnings", "-encoding", "us-ascii", "-release", "11")

libraryDependencies ++= Seq(
  "com.lihaoyi" %% "upickle" % "3.1.0",
  "com.knuddels" % "jtokkit" % "0.2.0",
  "com.softwaremill.sttp.client3" %% "core" % "3.8.15",
)
