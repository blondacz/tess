package dev.g4s.tess.core

import dev.g4s.tess.core.Message.Reaction

trait Actor {
  type ActorIdType <: Id
  def id: ActorIdType
  def receive: PartialFunction[Message, Seq[Reaction]]
  def update(event: Event): Actor
}

trait ActorFactory {
  type ActorIdType <: Id
  type ActorType <: Actor { type ActorIdType = ActorFactory.this.ActorIdType }

  def route: PartialFunction[Message, List[ActorIdType]]
  def receive(id: ActorIdType): PartialFunction[Message, Event]
  def create(id: ActorIdType): PartialFunction[Event, ActorType]
  def actorClass: Class[? <: Actor]
  def idClass: Class[? <: Id]
}
