package dev.g4s.tess.input

/** Convenience wiring: start sources, run router, and stop everything on close. */
final class InputRuntime[R](
    adminQ: InputQueue,
    busQ: InputQueue,
    inputQ: InputQueue,
    sources: Seq[InputSource],
    router: InputRouter[R]
) extends AutoCloseable {

  private val routerThread = new Thread(router, "tess-input-router")

  def start(): Unit = {
    sources.foreach(_.start())
    routerThread.start()
  }

  override def close(): Unit = stop()

  def stop(): Unit = {
    sources.reverse.foreach(_.stop())
    router.stop()
    routerThread.join(1000)
  }
}
