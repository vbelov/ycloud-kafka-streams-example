name := "kafka-streams-scala"

version := "0.1"

scalaVersion := "2.13.4"

libraryDependencies += "org.apache.kafka" % "kafka-streams" % "2.6.0"
libraryDependencies += "org.apache.kafka" % "kafka-streams-scala_2.13" % "2.6.0"
libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.30"
libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.30"

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs@_*) => MergeStrategy.discard
  case PathList("javax", "servlet", xs@_*) => MergeStrategy.first
  case PathList(ps@_*) if ps.last endsWith ".html" => MergeStrategy.first
  case "application.conf" => MergeStrategy.concat
  case "unwanted.txt" => MergeStrategy.discard
  case x =>
    //    val oldStrategy = (assemblyMergeStrategy in assembly).value
    //    oldStrategy(x)
    MergeStrategy.first
}
