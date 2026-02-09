package dev.g4s.tess.input

import dev.g4s.tess.core.{Envelope, Message, TraceContext}
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}

import java.time.Duration
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import scala.jdk.CollectionConverters.*

/** Simple Kafka consumer that pushes messages into an InputQueue. */
final class KafkaInputSource(
    bootstrapServers: String,
    topic: String,
    groupId: String,
    queue: InputQueue,
    decode: Array[Byte] => Option[Message],
    pollInterval: Duration = Duration.ofMillis(200)
) extends InputSource {

  private val running = new AtomicBoolean(false)
  private val consumer = new KafkaConsumer[String, Array[Byte]](props)
  private val thread = new Thread(() => run(), s"tess-kafka-$topic")

  private def props: Properties = {
    val p = new Properties()
    p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
    p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer])
    p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[ByteArrayDeserializer])
    p.put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
    p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
    p
  }

  override def start(): Unit = {
    if (running.compareAndSet(false, true)) {
      consumer.subscribe(java.util.Collections.singletonList(topic))
      thread.start()
    }
  }

  private def run(): Unit = {
    while (running.get()) {
      val records = consumer.poll(pollInterval)
      records.iterator().asScala.foreach { rec =>
        val trace = TraceContext(
          Map(
            "kafka.topic" -> rec.topic(),
            "kafka.partition" -> rec.partition().toString,
            "kafka.offset" -> rec.offset().toString,
            "kafka.timestamp" -> rec.timestamp().toString,
            "kafka.consumer.group" -> groupId
          ) ++ Option(rec.headers().lastHeader(TraceContext.TraceParentKey)).map(h => TraceContext.TraceParentKey -> new String(h.value()))
        )
        decode(rec.value()).foreach(msg => queue.put(Envelope(msg, trace)))
      }
    }
  }

  override def stop(): Unit = {
    if (running.compareAndSet(true, false)) {
      consumer.wakeup()
      thread.join(1000)
      consumer.close()
    }
  }
}
