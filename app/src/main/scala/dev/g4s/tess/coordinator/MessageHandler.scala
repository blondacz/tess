package dev.g4s.tess.coordinator

import dev.g4s.tess.core.{Actor, ActorFactory, ActorKey, ActorUnitOfWork, Event, Message}
import dev.g4s.tess.store.{EventStore, InMemoryEventStore}
import dev.g4s.tess.syntax.all.*

import scala.util.Try

class MessageHandler(actorFactory: ActorFactory, coordinator: Coordinator) {
  def handle: PartialFunction[Message, Seq[ActorUnitOfWork]] = {
    case msg if actorFactory.route.isDefinedAt(msg) =>
      val startingRank = coordinator.lastEventRank.fold(1L)(_ + 1)

      actorFactory.route(msg).foldLeft((List.empty[ActorUnitOfWork], startingRank)) {
        case ((uows, rank), id) =>
          val loadKey = ActorKey(id, actorFactory.actorClass)

          val (uow, nextRank) = coordinator.load(loadKey)(actorFactory) match {
            case Left(ex) => throw ex //FIXME: propagate

            case Right(None) => // create
              val initialEvent =
                actorFactory.receive(id)(msg)
              val actor = actorFactory.create(id)(initialEvent)
              val (updatedActor, actorEvents) = dispatchMessage(msg, actor)
              val uow = ActorUnitOfWork(ActorKey(id, actor.getClass), 1, initialEvent +: actorEvents, rank)
              coordinator.store(uow, updatedActor).fold(throw _, identity) //FIXME: propagat
              uow -> (rank + 1 + actorEvents.size) // initial event counts as +1

            case Right(Some((rehydrated, v))) =>
              val (updatedActor, actorEvents) = dispatchMessage(msg, rehydrated)
              val uow = ActorUnitOfWork(ActorKey(id, updatedActor.getClass), v + 1, actorEvents, rank)
              coordinator.store(uow, updatedActor).fold(throw _, identity) //FIXME: propagat
              uow -> (rank + actorEvents.size)
          }

          (uows :+ uow, nextRank)
      }._1
  }

  // Actor.receive may legally drop a message by returning an empty Seq; this helper centralizes that handling.
  private def dispatchMessage(msg: Message, a: Actor): (Actor, Seq[Event]) = {
    val actorEvents = if (a.receive.isDefinedAt(msg)) a.receive(msg) else Nil
    val updatedActor = actorEvents.foldLeft(a) { case (actor, event) => actor.update(event) }
    (updatedActor, actorEvents)
  }
}

class EventSourcedSystem(actorFactories: Seq[ActorFactory]) {
  val eventStore: EventStore = new InMemoryEventStore()
  val dispatcher: Dispatcher = new MemorizingDispatcher()
  val coordinator: Coordinator = new SimpleCoordinator(eventStore, dispatcher)
  private val messageHandlers = actorFactories.map(af => new MessageHandler(af, coordinator))

  def process(msg: Message): Either[Throwable, Seq[ActorUnitOfWork]] = {
    val lastEventRank = coordinator.start()
    Try(process(msg, List.empty)).toEither match {
      case l @ Left(value) =>
        coordinator.rollback()
        Left(value).withRight[Seq[ActorUnitOfWork]]
      case r @ Right(_) =>
        coordinator.commit()
        Right(dispatcher.replay(lastEventRank))
    }
  }

  // if UOWs are non-empty get first event from first UOW convert it to message, prepend all the UOWs to acc (but the first event) and process the created message
  //if UOWS are empty and acc is not take first event from first UOW prepend rest of the UOW to the ACC process the event (convert to message first)
  //if both UOWS and acc are empty finish
  //discard the UOWs if they don't have any more events
  def process(msg: Message, acc: Seq[ActorUnitOfWork]): Unit = {
    val produced: Seq[ActorUnitOfWork] = messageHandlers.flatMap { mh =>
      if (mh.handle.isDefinedAt(msg)) mh.handle(msg) else Nil
    }

    if (produced.nonEmpty) {
      val res = split(produced.head, produced.tail ++ acc)
      res.foreach { case (nextMsg, newAcc) => process(nextMsg, newAcc) }
    } else if (acc.nonEmpty) {
      val res = split(acc.head, acc.tail)
      res.foreach { case (nextMsg, newAcc) => process(nextMsg, newAcc) }
    }
  }

  private def split(uow: ActorUnitOfWork, acc: Seq[ActorUnitOfWork]): Option[(Message, Seq[ActorUnitOfWork])] =
    uow.headEvent match {
      case (Some(e), nextUow) => Some(e.asMessage -> (nextUow.toSeq ++ acc))
      case (n, _)             => throw new AssertionError(s"Invalid combination event $n for $uow")
    }

}
