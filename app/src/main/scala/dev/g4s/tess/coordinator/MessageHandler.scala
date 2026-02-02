package dev.g4s.tess.coordinator

import dev.g4s.tess.core.{Actor, ActorFactory, ActorKey, ActorUnitOfWork, Event, Message}
import dev.g4s.tess.store.{EventStore, InMemoryEventStore}
import dev.g4s.tess.syntax.all.*

import scala.util.Try

class MessageHandler(actorFactory: ActorFactory, coordinator: Coordinator) {
  def handle: PartialFunction[Message, Seq[ActorUnitOfWork]] = {
    case msg if actorFactory.route.isDefinedAt(msg) =>
      var eventRank = coordinator.lastEventRank.fold(1L)(i => i + 1)
      actorFactory.route(msg).foldLeft(List.empty[ActorUnitOfWork]) { case (uows, id) =>
        val loadKey = ActorKey(id, actorFactory.actorClass)
        coordinator.load(loadKey)(actorFactory) match {
          case Left(ex) => throw ex //FIXME: propagat
          case Right(None) => //create
            val initialEvent =
              actorFactory.receive(id)(msg)
            val actor = actorFactory.create(id)(initialEvent)
            val (updatedActor, actorEvents) = dispatchMessage(msg, actor)
            val uow = ActorUnitOfWork(ActorKey(id, actor.getClass), 1, initialEvent +: actorEvents, eventRank)
            coordinator.store(uow, updatedActor).fold(throw _, identity) //FIXME: propagat
            eventRank = eventRank + 1 /*initial*/ + actorEvents.size
            uows :+ uow
          case Right(Some((rehydrated, v))) =>
            val (updatedActor, actorEvents) = dispatchMessage(msg, rehydrated)
            val uow = ActorUnitOfWork(ActorKey(id, updatedActor.getClass), v + 1, actorEvents, eventRank)
            coordinator.store(uow, updatedActor).fold(throw _, identity) //FIXME: propagat
            eventRank = eventRank + actorEvents.size
            uows :+ uow
        }
      }
  }

  private def dispatchMessage(msg: Message, a: Actor) : (Actor,Seq[Event]) = {
    val actorEvents = if (a.receive.isDefinedAt(msg)) {
      a.receive(msg)
    } else Nil
    val updatedActor =  actorEvents.foldLeft(a) { case (a, e) => a.update(e) }
    (updatedActor,actorEvents)
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
      case (Some(e), Some(nextUow)) => Some(e.asMessage -> (nextUow +: acc))
      case (Some(e), None)         => Some((e.asMessage -> acc))
      case (n, _)                  => throw new AssertionError(s"Invalid combination event $n for $uow")

    }

}
