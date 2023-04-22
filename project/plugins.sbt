resolvers ++= Seq(
  "netlogo-extension-plugin" at "https://dl.cloudsmith.io/public/netlogo/netlogo-extension-plugin/maven/"
)

addSbtPlugin("org.nlogo" % "netlogo-extension-plugin" % "6.1.0")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.10.4")
