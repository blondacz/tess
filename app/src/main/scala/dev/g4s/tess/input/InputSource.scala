package dev.g4s.tess.input

/** Base contract for any external ingress (Kafka, AMPS, HTTP, etc.). */
trait InputSource extends AutoCloseable {
  def start(): Unit
  override def close(): Unit = stop()
  def stop(): Unit
}
