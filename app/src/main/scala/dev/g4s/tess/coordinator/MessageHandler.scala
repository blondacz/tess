package dev.g4s.tess.coordinator

import dev.g4s.tess.core.Message.Reaction
import dev.g4s.tess.core.{Actor, ActorFactory, ActorKey, ActorUnitOfWork, CommandMessage, Event, Message}
import dev.g4s.tess.store.{EventStore, InMemoryEventStore}
import dev.g4s.tess.syntax.all._

import scala.util.Try

class MessageHandler(actorFactory: ActorFactory, coordinator: Coordinator) {
  def handle: PartialFunction[Message, Seq[ActorUnitOfWork]] = {
    case msg @ CommandMessage(_, ids: List[actorFactory.ActorIdType]) =>
      val startingRank = coordinator.lastEventRank.fold(1L)(_ + 1)
      processMessage(msg, startingRank, ids)
    case msg if actorFactory.route.isDefinedAt(msg) =>
      val startingRank = coordinator.lastEventRank.fold(1L)(_ + 1)
      val ids: Seq[actorFactory.ActorIdType] = actorFactory.route(msg)
      processMessage(msg, startingRank, ids)
  }

  private def processMessage(msg: Message, startingRank: Long, ids: Seq[actorFactory.ActorIdType]) = {
    val (resultUows, rank) = ids.foldLeft((List.empty[ActorUnitOfWork], startingRank)) {
      case ((uows, rank), id) =>
        val loadKey = ActorKey(id, actorFactory.actorClass)

        val (uow, nextRank) = coordinator.load(loadKey)(actorFactory) match {
          case Left(ex) => throw ex //FIXME: propagate

          case Right(None) => // create
            val initialEvent =
              actorFactory.receive(id)(msg)
            val actor = actorFactory.create(id)(initialEvent)
            val (updatedActor, actorEvents) = dispatchMessage(msg, actor)
            val uow = ActorUnitOfWork(ActorKey(id, actor.getClass), 1, initialEvent +: actorEvents.collect { case e: Event => e }, rank)
            coordinator.store(uow, updatedActor).fold(throw _, identity) //FIXME: propagat
            uow -> (rank + 1 + actorEvents.size) // initial event counts as +1

          case Right(Some((rehydrated, v))) =>
            val (updatedActor, actorEvents) = dispatchMessage(msg, rehydrated)
            val uow = ActorUnitOfWork(ActorKey(id, updatedActor.getClass), v + 1, actorEvents.collect { case e: Event => e }, rank)
            coordinator.store(uow, updatedActor).fold(throw _, identity) //FIXME: propagat
            uow -> (rank + actorEvents.size)
        }

        (uows :+ uow, nextRank)
    }
    resultUows
  }

  // Actor.receive may legally drop a message by returning an empty Seq; this helper centralizes that handling.
  private def dispatchMessage(msg: Message, a: Actor): (Actor, Seq[Reaction]) = {
    val actorEvents = if (a.receive.isDefinedAt(msg)) a.receive(msg) else Nil
    val updatedActor = actorEvents.collect{case e: Event => e }.foldLeft(a) { case (actor, event) => actor.update(event) }
    (updatedActor, actorEvents)
  }
}


