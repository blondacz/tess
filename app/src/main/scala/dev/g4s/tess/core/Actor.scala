package dev.g4s.tess.core

import dev.g4s.tess.core.Message.Reaction

import scala.reflect.ClassTag

trait Actor {
  type ActorIdType <: Id
  def id: ActorIdType
  def receive: PartialFunction[Message, Seq[Reaction]]
  def update(event: Event): Actor
}

trait ActorFactory[IdType <: Id : ClassTag, ActorType <: Actor { type ActorIdType = IdType } : ClassTag]{
  type ActorIdType = IdType
  def route: PartialFunction[Message, List[IdType]]
  def receive(id: IdType): PartialFunction[Message, Event]
  def create(id: IdType): PartialFunction[Event, ActorType]
  lazy val idClass: Class[? <: Id] =
    summon[ClassTag[IdType]].runtimeClass.asInstanceOf[Class[? <: Id]]

  lazy val actorClass: Class[? <: Actor] =
    summon[ClassTag[ActorType]].runtimeClass.asInstanceOf[Class[? <: Actor]]
}