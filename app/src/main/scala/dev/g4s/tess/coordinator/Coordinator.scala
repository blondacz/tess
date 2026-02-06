package dev.g4s.tess.coordinator

import dev.g4s.tess.core.{Actor, ActorFactory, ActorKey, ActorUnitOfWork, Id}
import dev.g4s.tess.store.EventStore

trait Coordinator {
  def eventStore: EventStore
  def start(): Long
  def commit(): Option[Long]
  def rollback(): Unit
  def load[AF <: ActorFactory[?,?]](key: ActorKey)(actorFactory: AF ): Either[Throwable, Option[(Actor, Long)]]
  def lastReactionRank: Option[Long]
  def store(uow: ActorUnitOfWork, actor: Actor): Either[Throwable, Unit]
}
