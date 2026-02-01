package dev.g4s.tess.core

trait Message
case class EventMessage(e: Event) extends Message
trait Event
trait Context
trait Id extends Product with Serializable

case class ActorKey(id: Id, clazz: Class[? <: Actor])
