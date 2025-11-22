package dev.g4s.tess

import scala.util.Try


class SimpleCoordinator(val eventStore: EventStore, dispatcher: Dispatcher) extends Coordinator {
  private val actors = new collection.mutable.HashMap[Id,(List[UnitOfWork], Actor)]
  private var eventRank : Option[Long] = None
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

  override def load[AF <: ActorFactory, ID <: Id](id: ID)( actorFactory: AF { type ActorIdType = ID}): Either[Throwable, Option[(Actor,Long)]] = {
    actors.get(id).map(a => (a._2,a._1.last.actorVersion)) match {
      case a@Some(value) => Right(a)
      case None =>
        val uows = eventStore.load(id)
        uows.map(w => Rehydrator.rehydrateNewActor(id, w)(actorFactory))
    }
  }

  override def lastEventRank: Option[Long] =  {
    eventRank = eventRank.orElse(eventStore.lastEventRank) //inject during startup
    eventRank
  }

  override def store(uow: UnitOfWork, actor: Actor): Either[Throwable, Unit] = {
    dispatcher.dispatch(uow)
    actors.put(uow.id, (actors.getOrElse(uow.id, (Nil, actor))._1 :+ uow, actor))
    eventRank = Some(uow.endingEventRank)
    Right(())
  }
}

class InMemoryEventStore extends EventStore {
  private val actors = new collection.mutable.HashMap[Id,List[UnitOfWork]]
  private var eventRank : Option[Long] = None
  override def store(uow: UnitOfWork): Either[Throwable, Unit] = {
    eventRank = Some(uow.endingEventRank)
    actors.updateWith(uow.id) {
      maybeUows =>
        Some(maybeUows.fold(List(uow))(_ :+ uow))
    }.map(_ => ()).toRight(new AssertionError("should not happen in memory"))
  }

  override def load(id: Id): Either[Throwable, List[UnitOfWork]] = actors.get(id).orElse(Some(Nil)).toRight(new AssertionError("should not happen in memory"))
  override def lastEventRank: Option[Long] = eventRank
}


object Rehydrator {

  def rehydrateNewActor[AF <: ActorFactory, ID <: Id](id: ID, uows: List[UnitOfWork])(actorFactory: AF { type ActorIdType = ID}): Option[(Actor,Long)] = {
    val rehydrated = uows.foldLeft(Option.empty[(Actor, Long)]) {
      case (None, uow) =>
        val actor: Option[Actor] = uow.events.headOption.flatMap(
          e =>
            if (actorFactory.create(id).isDefinedAt(e))
              Some(actorFactory.create(id)(e))
            else
              None
        )

        uow.events.tail.foldLeft(actor) {
          case (a, e) => a.map(_.update(e))
        }.map((_, uow.actorVersion))
      case (actor, uow) =>
        uow.events.foldLeft(actor) {
          case (a, e) => a.map(a => (a._1.update(e), uow.actorVersion))
        }
    }
    rehydrated
  }

  def updateActor(actor: Actor, events: Seq[Event]): Actor =
    events.foldLeft(actor) {case (a,e) => a.update(e)}
}

class MessageHandler( actorFactory: ActorFactory, coordinator: Coordinator) {
     def handle : PartialFunction[Message,Seq[UnitOfWork]] = {
       case msg if actorFactory.route.isDefinedAt(msg) =>
         var eventRank = coordinator.lastEventRank.fold(1L)(i => i + 1)
         actorFactory.route(msg).foldLeft(List.empty[UnitOfWork]) { case (uows,id) =>
           coordinator.load(id)(actorFactory) match {
             case Left(ex) => throw ex //FIXME: propagat
             case Right(None) => //create
               val initialEvent =
                 actorFactory.receive(id)(msg)
               val actor = actorFactory.create(id)(initialEvent)
               val actorEvents = if (actor.receive.isDefinedAt(msg)) {
                 actor.receive(msg)
               } else Nil
               val updatedActor = Rehydrator.updateActor(actor, actorEvents)

               val uow = UnitOfWork(id, 1, actor.getClass, initialEvent +: actorEvents, eventRank)
               coordinator.store(uow, updatedActor).fold(throw _, identity)//FIXME: propagat
               eventRank = eventRank + 1 /*initial*/ +  actorEvents.size
               uows :+ uow
             case Right(Some((rehydrated,v))) =>
                  val actorEvents = if (rehydrated.receive.isDefinedAt(msg)) {
                    rehydrated.receive(msg)
                  } else Nil
                  val updatedActor = Rehydrator.updateActor(rehydrated, actorEvents)

                  val uow = UnitOfWork(id, v + 1, updatedActor.getClass, actorEvents, eventRank)
                  coordinator.store(uow, updatedActor).fold(throw _, identity) //FIXME: propagat
                  eventRank = eventRank + actorEvents.size
                  uows :+ uow

           }
         }
     }

}

trait Dispatcher {
  def dispatch(unitOfWork: UnitOfWork) : Unit
  def replay(from: Long): List[UnitOfWork]
  def commit(eventRank: Long): Unit
  def rollback(): Unit
}

class MemorizingDispatcher extends Dispatcher {
  private val dispatched = new collection.mutable.ArrayBuffer[UnitOfWork]
  private var commitedUptTo = 0L
  override def dispatch(unitOfWork: UnitOfWork): Unit =
    dispatched += unitOfWork

  def replay(from: Long): List[UnitOfWork] = dispatched.filter(_.endingEventRank >= from).toList
  def commit(eventRank: Long): Unit = commitedUptTo = eventRank
  def rollback(): Unit = dispatched.dropWhile(_.endingEventRank > commitedUptTo)
}


class EventSourcedSystem(actorFactories: Seq[ActorFactory]) {
  val eventStore = new InMemoryEventStore()
  val dispatcher = new MemorizingDispatcher()
  val coordinator = new SimpleCoordinator(eventStore, dispatcher)
  private val messageHandlers = actorFactories.map(af => new MessageHandler(af, coordinator))
  def process(msg: Message): Either[Throwable, Seq[UnitOfWork]] = {
    val lastEventRank = coordinator.lastEventRank.getOrElse(0L)
    Try(process(msg, List.empty)).toEither match {
      case l@Left(value) =>
        coordinator.rollback()
        Left(value).withRight[Seq[UnitOfWork]]
      case r@Right(value) =>
        coordinator.commit()
        Right(dispatcher.replay(lastEventRank + 1))
    }
  }


  // if UOWs are non-empty get first event from first UOW convert it to message, prepend all the UOWs to acc (but the first event) and process the created message
  //if UOWS are empty and acc is not take first event from first UOW prepend rest of the UOW to the ACC process the event (convert to message first)
  //if both UOWS and acc is empty finish
  //discard the UOWs if they dont have any more events
  def process(msg: Message, acc: Seq[UnitOfWork]): Unit = {
      val produced: Seq[UnitOfWork] = messageHandlers.flatMap { mh =>
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


  private def split(uow: UnitOfWork, acc: Seq[UnitOfWork]): Option[(Message, Seq[UnitOfWork])] =
    uow.headEvent match {
      case (Some(e),Some(uow)) =>  Some(e.asMessage-> (uow +: acc))
      case (Some(e),None) =>  Some((e.asMessage-> acc))
      case (n,m) => throw new AssertionError(s"Invalid combination event $n for $uow")

    }

  implicit class EventOps(e: Event) {
    def asMessage: Message =  {
      EventMessage(e)
    }
  }

}