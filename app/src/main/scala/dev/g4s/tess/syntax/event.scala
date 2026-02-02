package dev.g4s.tess.syntax

import dev.g4s.tess.core.{Event, EventMessage, Message}

trait EventSyntax {
  implicit final def tessSyntaxEventOps(e: Event): EventOps =
    new EventOps(e)
}

final class EventOps(private val e: Event) extends AnyVal {
  def asMessage: Message =
    EventMessage(e)
}

object event extends EventSyntax
object all extends EventSyntax
