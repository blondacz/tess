package dev.g4s.tess.coordinator

import dev.g4s.tess.core.{Actor, ActorFactory, ActorKey, ActorUnitOfWork, Id}
import dev.g4s.tess.store.EventStore

trait Coordinator {
  def eventStore: EventStore
  def start(): Long
  def commit(): Option[Long]
  def rollback(): Unit
  def load[AF <: ActorFactory, ID <: Id](key: ActorKey)(actorFactory: AF { type ActorIdType = ID }): Either[Throwable, Option[(Actor, Long)]]
  def lastEventRank: Option[Long]
  def store(uow: ActorUnitOfWork, actor: Actor): Either[Throwable, Unit]
}
