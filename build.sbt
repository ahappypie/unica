name := "unica"

version := "0.0.1"

scalaVersion := "2.12.8"

PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value
)
PB.protoSources in Compile := Seq(baseDirectory.value / "protos")

libraryDependencies ++= Seq(
  "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion,
  "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
)

enablePlugins(AshScriptPlugin)
enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)

maintainer in Docker := "Brian Bagdasarian"
dockerRepository := Some("quay.io")
dockerUsername := Some("ahappypie")

dockerBaseImage := "openjdk:8-jre-alpine"

mainClass in Compile := Some("io.github.ahappypie.unica.UnicaServer")

javaOptions in Universal ++= Seq("-J-XX:+UnlockExperimentalVMOptions", "-J-XX:+UseCGroupMemoryLimitForHeap")