package dev.g4s.tess.store

import dev.g4s.tess.core.{ActorKey, ActorUnitOfWork}

trait EventStore {
  def store(uow: ActorUnitOfWork): Either[Throwable, Unit]
  def load(key: ActorKey): Either[Throwable, List[ActorUnitOfWork]]
  def lastEventRank: Option[Long]
}
