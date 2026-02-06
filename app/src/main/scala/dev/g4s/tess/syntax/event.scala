package dev.g4s.tess.syntax

import dev.g4s.tess.core.{ActorFactory, Command, CommandMessage, Event, EventMessage, Id, Message}

trait EventSyntax {
  implicit final def tessSyntaxEventOps(e: Event): EventOps =
    new EventOps(e)
  extension (c: Command)
      def to(id: Id) = CommandMessage(c,id::Nil)
}


final class EventOps(private val e: Event) extends AnyVal {
  def asMessage: Message =
    EventMessage(e)
}

object event extends EventSyntax
object all extends EventSyntax {
  type GenericActorFactory = ActorFactory[?,?]
}
