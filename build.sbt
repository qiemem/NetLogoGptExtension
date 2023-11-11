import sbt.nio.file.FileTreeView

enablePlugins(org.nlogo.build.NetLogoExtension)

name := "Gpt-Extension"
version := "0.0.0"
isSnapshot := true

netLogoExtName      := "gpt"
netLogoClassManager := "org.nlogo.extensions.gpt.GptExtension"
netLogoVersion      := "6.3.0"
netLogoShortDescription := "Let agents communicate and make decisions using GPT-3.5 or GPT-4"
netLogoLongDescription := ""
netLogoHomepage := "https://github.com/qiemem/NetLogoGptExtension"
netLogoZipExtras := FileTreeView.default.list((baseDirectory.value / "demos").toGlob / "*.nlogo").map(_._1.toFile)

scalaVersion          := "2.12.17"
Compile / scalaSource := baseDirectory.value / "src" / "main"
Test / scalaSource    := baseDirectory.value / "src" / "test"
scalacOptions        ++= Seq("-deprecation", "-unchecked", "-Xfatal-warnings", "-encoding", "us-ascii", "-release", "11")

libraryDependencies ++= Seq(
  "com.lihaoyi" %% "upickle" % "3.1.0",
  "com.knuddels" % "jtokkit" % "0.2.0",
  "com.softwaremill.sttp.client3" %% "core" % "3.8.15",
)

ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0"