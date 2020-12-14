package ru.yandex.cloud.examples.kafkastreams

import java.time.Duration
import java.util.Properties

import org.apache.kafka.streams.scala.StreamsBuilder
import org.apache.kafka.streams.scala.kstream._
import org.apache.kafka.streams.{KafkaStreams, StreamsConfig}

/**
 * Demonstrates, using the high-level KStream DSL, how to implement the WordCount program that
 * computes a simple word occurrence histogram from an input text.
 **/
object WordCountScalaExample extends App {

  import org.apache.kafka.streams.scala.Serdes._
  import org.apache.kafka.streams.scala.ImplicitConversions._

  val jaasTemplate = "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"%s\" password=\"%s\";";
  val jaasCfg = jaasTemplate.format(sys.env("KAFKA_USER"), sys.env("KAFKA_PASS"));

  val config: Properties = {
    val p = new Properties()
    p.put(StreamsConfig.APPLICATION_ID_CONFIG, "wordcount-scala-application")
    p.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, sys.env("KAFKA_BROKERS"))
    p.put("security.protocol", "SASL_SSL")
    p.put("sasl.mechanism", "SCRAM-SHA-512")
    p.put("sasl.jaas.config", jaasCfg)
    p.put("ssl.truststore.location", sys.env("TRUSTSTORE_PATH"))
    p.put("ssl.truststore.password", sys.env("TRUSTSTORE_PASS"))
    p
  }

  val builder = new StreamsBuilder()
  val textLines: KStream[String, String] = builder.stream[String, String]("streams-plaintext-input")
  val wordCounts: KTable[String, Long] = textLines
    .flatMapValues(textLine => textLine.toLowerCase.split("\\W+"))
    .groupBy((_, word) => word)
    .count()
  wordCounts.toStream.to("streams-wordcount-output")

  val streams: KafkaStreams = new KafkaStreams(builder.build(), config)

  // Always (and unconditionally) clean local state prior to starting the processing topology.
  // We opt for this unconditional call here because this will make it easier for you to play around with the example
  // when resetting the application for doing a re-run (via the Application Reset Tool,
  // http://docs.confluent.io/current/streams/developer-guide.html#application-reset-tool).
  //
  // The drawback of cleaning up local state prior is that your app must rebuilt its local state from scratch, which
  // will take time and will require reading all the state-relevant data from the Kafka cluster over the network.
  // Thus in a production scenario you typically do not want to clean up always as we do here but rather only when it
  // is truly needed, i.e., only under certain conditions (e.g., the presence of a command line flag for your app).
  // See `ApplicationResetExample.java` for a production-like example.
  streams.cleanUp()

  streams.start()

  // Add shutdown hook to respond to SIGTERM and gracefully close Kafka Streams
  sys.ShutdownHookThread {
    streams.close(Duration.ofSeconds(10))
  }

}
