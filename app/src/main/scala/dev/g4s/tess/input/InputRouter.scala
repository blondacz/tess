package dev.g4s.tess.input

import dev.g4s.tess.core.Envelope

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.*

/** Prioritised draining of admin -> bus -> input queues into Tess. */
final class InputRouter[R](
    process: Envelope => Either[Throwable, R],
    adminQ: InputQueue,
    busQ: InputQueue,
    inputQ: InputQueue,
    pollTimeout: FiniteDuration = 200.millis,
    busBurst: Int = 4
) extends Runnable {

  @volatile private var running = true

  override def run(): Unit = {
    while (running) {
      nextEnvelope().foreach { env =>
        process(env) // errors propagate; consider logging/metrics
      }
    }
  }

  def stop(): Unit = running = false

  private def nextEnvelope(): Option[Envelope] = {
    // Highest priority: adminQ
    adminQ.poll(0, TimeUnit.MILLISECONDS).orElse {
      // Drain a small burst from bus before dropping to input
      var burst = busBurst
      var env: Option[Envelope] = None
      while (burst > 0 && env.isEmpty) {
        env = busQ.poll(0, TimeUnit.MILLISECONDS)
        burst -= 1
      }
      env.orElse(inputQ.poll(pollTimeout.toMillis, TimeUnit.MILLISECONDS))
    }
  }
}
