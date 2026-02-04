package dev.g4s.tess.coordinator

import dev.g4s.tess.core.Message.Reaction
import dev.g4s.tess.core.{Actor, ActorFactory, ActorKey, ActorUnitOfWork, CommandMessage, Event, Message}
import dev.g4s.tess.syntax.all._

class MessageHandler(actorFactory: ActorFactory, coordinator: Coordinator) {

  /** Handles messages this factory can process, producing ordered UoWs. */
  def handle: PartialFunction[Message, Seq[ActorUnitOfWork]] = {
    case msg @ CommandMessage(_, ids) if ids.forall(id => actorFactory.idClass.isInstance(id)) =>
      val startingRank = coordinator.lastReactionRank.fold(1L)(_ + 1)
      processMessage(msg, startingRank, ids.asInstanceOf[List[actorFactory.ActorIdType]])
    case msg if actorFactory.route.isDefinedAt(msg) =>
      val startingRank = coordinator.lastReactionRank.fold(1L)(_ + 1)
      val ids: Seq[actorFactory.ActorIdType] = actorFactory.route(msg)
      processMessage(msg, startingRank, ids)
  }

  private def processMessage(msg: Message, startingRank: Long, ids: Seq[actorFactory.ActorIdType]): Seq[ActorUnitOfWork] = {
    val (resultUows, _) = ids.foldLeft((List.empty[ActorUnitOfWork], startingRank)) {
      case ((uows, rank), id) =>
        val loadKey = ActorKey(id, actorFactory.actorClass)

        val (uow, nextRank) = coordinator.load(loadKey)(actorFactory) match {
          case Left(ex) => throw ex //FIXME: propagate

          case Right(None) => // create
            val initialEvent = actorFactory.receive(id)(msg)
            val actor = actorFactory.create(id)(initialEvent)
            val (updatedActor, actorReactions) = dispatchMessage(msg, actor)
            val reactionsSeq: Seq[Reaction] = initialEvent +: actorReactions
            val uow = ActorUnitOfWork(ActorKey(id, actor.getClass), 1, reactionsSeq, rank)
            coordinator.store(uow, updatedActor).fold(throw _, identity) //FIXME: propagate
            (uow, rank + reactionsSeq.size)

          case Right(Some((rehydrated, v))) =>
            val (updatedActor, actorReactions) = dispatchMessage(msg, rehydrated)
            val uow = ActorUnitOfWork(ActorKey(id, updatedActor.getClass), v + 1, actorReactions, rank)
            coordinator.store(uow, updatedActor).fold(throw _, identity) //FIXME: propagate
            (uow, rank + actorReactions.size)
        }

        (uows :+ uow, nextRank)
    }
    resultUows
  }

  // Actor.receive may legally drop a message by returning an empty Seq; this helper centralizes that handling.
  private def dispatchMessage(msg: Message, a: Actor): (Actor, Seq[Reaction]) = {
    val actorReactions = if (a.receive.isDefinedAt(msg)) a.receive(msg) else Nil
    val updatedActor = actorReactions.collect { case e: Event => e }.foldLeft(a) { case (actor, event) => actor.update(event) }
    (updatedActor, actorReactions)
  }
}
