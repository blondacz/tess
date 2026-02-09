package dev.g4s.tess.core

/** Wrapper to pair a domain Message with tracing/provenance metadata. */
final case class Envelope(message: Message, trace: TraceContext = TraceContext.empty)
