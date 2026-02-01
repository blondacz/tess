package dev.g4s.tess.coordinator

import dev.g4s.tess.core.{Actor, ActorFactory, ActorKey, ActorUnitOfWork, Event, Id}
import dev.g4s.tess.store.EventStore

class SimpleCoordinator(val eventStore: EventStore, dispatcher: Dispatcher) extends Coordinator {
  private val actors = new collection.mutable.HashMap[ActorKey, (List[ActorUnitOfWork], Actor)]
  private var eventRank: Option[Long] = None
  private var startEventRank: Long = 0

  override def start(): Long = {
    startEventRank = lastEventRank.map(_ + 1).getOrElse(1)
    startEventRank
  }

  override def commit(): Option[Long] = {
    actors.values.map(_._1).tapEach { uows =>
      uows.tapEach(uow => eventStore.store(uow).fold(throw _, identity))
    }
    actors.clear()
    dispatcher.commit(eventRank.getOrElse(0L))
    eventRank
  }

  override def rollback(): Unit = {
    actors.clear()
    eventRank = None
  }

  override def load[AF <: ActorFactory, ID <: Id](key: ActorKey)(actorFactory: AF { type ActorIdType = ID }): Either[Throwable, Option[(Actor, Long)]] = {
    actors.get(key).map(a => (a._2, a._1.last.actorVersion)) match {
      case a @ Some(_) => Right(a)
      case None =>
        val uows = eventStore.load(key)
        uows.map(w => Rehydrator.rehydrateNewActor(key.id.asInstanceOf[ID], w)(actorFactory))
    }
  }

  override def lastEventRank: Option[Long] = {
    eventRank = eventRank.orElse(eventStore.lastEventRank) // inject during startup
    eventRank
  }

  override def store(uow: ActorUnitOfWork, actor: Actor): Either[Throwable, Unit] = {
    dispatcher.dispatch(uow)
    val akey = uow.key
    actors.put(akey, (actors.getOrElse(akey, (Nil, actor))._1 :+ uow, actor))
    eventRank = Some(uow.endingEventRank)
    Right(())
  }
}

object Rehydrator {

  def rehydrateNewActor[AF <: ActorFactory, ID <: Id](id: ID, uows: List[ActorUnitOfWork])(actorFactory: AF { type ActorIdType = ID }): Option[(Actor, Long)] = {
    val rehydrated = uows.foldLeft(Option.empty[(Actor, Long)]) {
      case (None, uow) =>
        val actor: Option[Actor] = uow.events.headOption.flatMap { e =>
          if (actorFactory.create(id).isDefinedAt(e))
            Some(actorFactory.create(id)(e))
          else
            None
        }

        uow.events.tail
          .foldLeft(actor) { case (a, e) => a.map(_.update(e)) }
          .map((_, uow.actorVersion))
      case (actor, uow) =>
        uow.events.foldLeft(actor) { case (a, e) => a.map(a => (a._1.update(e), uow.actorVersion)) }
    }
    rehydrated
  }

  def updateActor(actor: Actor, events: Seq[Event]): Actor =
    events.foldLeft(actor) { case (a, e) => a.update(e) }
}
