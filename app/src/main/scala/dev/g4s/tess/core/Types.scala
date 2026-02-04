package dev.g4s.tess.core

trait Message
case class EventMessage(e: Event) extends Message
case class CommandMessage(c: Command, ids: List[Id]) extends Message

trait Event
trait Context
trait Id extends Product with Serializable

case class ActorKey(id: Id, clazz: Class[? <: Actor])
trait Command
trait Notification extends Message

object Message {
  type Reaction = Event  | Notification | CommandMessage
}
