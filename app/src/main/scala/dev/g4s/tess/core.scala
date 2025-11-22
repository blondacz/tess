package dev.g4s.tess

import scala.util.Try


trait Message
case class EventMessage(e: Event) extends Message
trait Event
trait Context
trait Id extends Product with Serializable

case class UnitOfWork(id: Id,
                      actorVersion: Long,
                      actorClass: Class[_ <: Actor],
                      events: Seq[Event],
                      startingEventRank: Long) {
  lazy val endingEventRank : Long = startingEventRank + events.size -1
  lazy val headEvent : (Option[Event],Option[UnitOfWork]) = events.headOption-> {
    if (events.tail.isEmpty) None else Some(UnitOfWork(id, actorVersion + 1, actorClass, events.tail, endingEventRank + 1))
  }
}


trait EventStore {
  def store(uow: UnitOfWork): Either[Throwable,Unit]
  def load(id: Id): Either[Throwable,List[UnitOfWork]]
  def lastEventRank : Option[Long]
}

trait Coordinator {
  def eventStore: EventStore
  def commit() :  Option[Long]
  def rollback() : Unit
  def load[AF <: ActorFactory, ID <: Id](id: ID)( actorFactory: AF { type ActorIdType = ID}): Either[Throwable,Option[(Actor,Long)]]
  def lastEventRank : Option[Long]
  def store(uow: UnitOfWork, actor: Actor): Either[Throwable,Unit]
}


trait ActorFactory {
  type ActorIdType <: Id
  type ActorType <: Actor {type ActorIdType = ActorFactory.this.ActorIdType}

  def route: PartialFunction[Message,List[ActorIdType]]
  def receive(id: ActorIdType): PartialFunction[Message,Event]
  def create(id: ActorIdType): PartialFunction[Event,ActorType]
}

trait Actor {
  type ActorIdType <: Id
  def id: ActorIdType
  def receive: PartialFunction[Message,Seq[Event]]
  def update(event: Event): Actor
}

