package dev.g4s.tess

import scala.reflect.{ClassTag, classTag}
import scala.util.Try


trait Message
case class EventMessage(e: Event) extends Message
trait Event
trait Context
trait Id extends Product with Serializable

case class ActorKey(id: Id, clazz: Class[? <: Actor])

case class ActorUnitOfWork(key: ActorKey,
                           actorVersion: Long,
                           events: Seq[Event],
                           startingEventRank: Long) {
  lazy val endingEventRank : Long = startingEventRank + events.size -1
  lazy val headEvent : (Option[Event],Option[ActorUnitOfWork]) = events.headOption-> {
    if (events.tail.isEmpty) None else Some(ActorUnitOfWork(key, actorVersion + 1, events.tail, endingEventRank + 1))
  }
}


trait EventStore {
  def store(uow: ActorUnitOfWork): Either[Throwable,Unit]
  def load(key: ActorKey): Either[Throwable,List[ActorUnitOfWork]]
  def lastEventRank : Option[Long]
}

trait Coordinator {
  def eventStore: EventStore
  def start() :  Long
  def commit() :  Option[Long]
  def rollback() : Unit
  def load[AF <: ActorFactory, ID <: Id](key: ActorKey)( actorFactory: AF { type ActorIdType = ID}): Either[Throwable,Option[(Actor,Long)]]
  def lastEventRank : Option[Long]
  def store(uow: ActorUnitOfWork, actor: Actor): Either[Throwable,Unit]
}


trait ActorFactory {
  type ActorIdType <: Id
  type ActorType <: Actor {type ActorIdType = ActorFactory.this.ActorIdType}

  def route: PartialFunction[Message,List[ActorIdType]]
  def receive(id: ActorIdType): PartialFunction[Message,Event]
  def create(id: ActorIdType): PartialFunction[Event,ActorType]
  def actorClass: Class[? <: Actor]
}

trait Actor {
  type ActorIdType <: Id
  def id: ActorIdType
  def receive: PartialFunction[Message,Seq[Event]]
  def update(event: Event): Actor
}

