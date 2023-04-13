enablePlugins(org.nlogo.build.NetLogoExtension)

name := "Gpt-Extension"
version := "0.0.0"

netLogoExtName      := "gpt"
netLogoClassManager := "org.nlogo.extensions.gpt.GptExtension"
netLogoVersion      := "6.3.0"

scalaVersion          := "2.12.6"
Compile / scalaSource := baseDirectory.value / "src" / "main"
Test / scalaSource    := baseDirectory.value / "src" / "test"
scalacOptions        ++= Seq("-deprecation", "-unchecked", "-Xfatal-warnings", "-encoding", "us-ascii", "-release", "11")

libraryDependencies ++= Seq(
  "com.lihaoyi" %% "requests" % "0.7.1"
)