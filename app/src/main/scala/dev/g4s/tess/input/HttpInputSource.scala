package dev.g4s.tess.input

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import dev.g4s.tess.core.{Envelope, Message, TraceContext}

import java.io.InputStream
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Lightweight HTTP ingress: POST /message with body -> Message. */
final class HttpInputSource(
    port: Int,
    queue: InputQueue,
    decode: Array[Byte] => Option[Message],
    path: String = "/message"
) extends InputSource {
  private val running = new AtomicBoolean(false)
  private val server = HttpServer.create(new InetSocketAddress(port), 0)
  private val executor = Executors.newFixedThreadPool(4)

  server.createContext(path, new HttpHandler {
    override def handle(exchange: HttpExchange): Unit = {
      if (exchange.getRequestMethod != "POST") {
        exchange.sendResponseHeaders(405, -1)
        return
      }
      val body = readAll(exchange.getRequestBody)
      val trace = TraceContext(
        Map(
          "http.method" -> exchange.getRequestMethod,
          "http.path"   -> exchange.getHttpContext.getPath,
          "http.remote" -> exchange.getRemoteAddress.toString
        ) ++ Option(exchange.getRequestHeaders.getFirst(TraceContext.TraceParentKey)).map(TraceContext.TraceParentKey -> _)
      )
      decode(body).foreach(msg => queue.put(Envelope(msg, trace)))
      exchange.sendResponseHeaders(202, -1)
      exchange.close()
    }
  })

  override def start(): Unit = {
    if (running.compareAndSet(false, true)) {
      server.setExecutor(executor)
      server.start()
    }
  }

  override def stop(): Unit = {
    if (running.compareAndSet(true, false)) {
      server.stop(0)
      executor.shutdown()
    }
  }

  private def readAll(is: InputStream): Array[Byte] = {
    val buf = is.readAllBytes()
    is.close()
    buf
  }
}
