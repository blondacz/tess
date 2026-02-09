package dev.g4s.tess.core

/** Lightweight carrier for tracing / source metadata (e.g., kafka offsets, HTTP headers, traceparent). */
final case class TraceContext(fields: Map[String, String]) {
  def get(key: String): Option[String] = fields.get(key)
  def ++(other: TraceContext): TraceContext = TraceContext(fields ++ other.fields)
}

object TraceContext {
  val TraceParentKey = "traceparent"

  val empty: TraceContext = TraceContext(Map.empty)

  def fromMaybe(parent: Option[String], extras: (String, String)*): TraceContext =
    TraceContext((parent.map(TraceParentKey -> _).toSeq ++ extras).toMap)
}
