package dev.g4s.tess.core

trait Actor {
  type ActorIdType <: Id
  def id: ActorIdType
  def receive: PartialFunction[Message, Seq[Event]]
  def update(event: Event): Actor
}

trait ActorFactory {
  type ActorIdType <: Id
  type ActorType <: Actor { type ActorIdType = ActorFactory.this.ActorIdType }

  def route: PartialFunction[Message, List[ActorIdType]]
  def receive(id: ActorIdType): PartialFunction[Message, Event]
  def create(id: ActorIdType): PartialFunction[Event, ActorType]
  def actorClass: Class[? <: Actor]
}
