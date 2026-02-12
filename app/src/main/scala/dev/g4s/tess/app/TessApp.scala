package dev.g4s.tess.app

import dev.g4s.tess.{Tess, TessConfig}

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/**
  * Small application helper, similar to cats-effect IOApp, for running a Tess instance
  * built from a managed Resource. Implement `run` to describe how to build your Tess,
  * then extend this trait on your main object.
  */
trait TessApp {

  /** Describe how to build your Tess (including acquire/release of its dependencies). */
  def run: TessConfig.Resource[? <: Tess[?]]

  final def main(args: Array[String]): Unit = {
    val resource = run
    val tess = resource.acquire()
    val released = new AtomicBoolean(false)
    val latch = new CountDownLatch(1)

    def release(): Unit =
      if (released.compareAndSet(false, true)) {
        try tess.stop() finally resource.release(tess)
        latch.countDown()
      }

    // Ensure resources are cleaned on JVM shutdown (e.g., SIGINT).
    Runtime.getRuntime.addShutdownHook(new Thread(() => release(), "tess-shutdown"))

    try {
      tess.start()
      latch.await() // Keep the app running until a shutdown is triggered.
    } finally {
      release()
    }
  }
}
