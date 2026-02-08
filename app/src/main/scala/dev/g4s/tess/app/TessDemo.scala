package dev.g4s.tess.app

import dev.g4s.tess.TessConfig.DispatcherFactory
import dev.g4s.tess.{Tess, TessConfig}
import dev.g4s.tess.coordinator.{Dispatcher, MemorizingDispatcher, NotificationDispatcher}
import dev.g4s.tess.core.{ActorUnitOfWork, Notification}
import dev.g4s.tess.domain.{AddItemsForCustomer, BasketFactory, BasketId, ClearBasket, CustomerFactory, ListBasket}
import dev.g4s.tess.input.DirectInput
import dev.g4s.tess.store.{InMemoryEventStore, RocksDbEventStore}
import dev.g4s.tess.syntax.all.*

import scala.concurrent.duration.{DurationInt, FiniteDuration}


object TessDemo {
//  private val dispatcherFactory = () => new NotificationDispatcher(new MemorizingDispatcher())
  private val dispatcherFactory : DispatcherFactory[List[ActorUnitOfWork]] = () => new PrintingDispatcher(new MemorizingDispatcher())


  def main(args: Array[String]): Unit = {
    var di : DirectInput = null
    val es = Tess
      .builder
      .withActorFactories(CustomerFactory, BasketFactory)
      .withEventStore(() => new RocksDbEventStore("roxsdb","g1"))
      .withInputSource{iq => di = new DirectInput(iq.input);di}
      .withDispatcher(dispatcherFactory)
      .build()

    es.startInputs()

    println("Running..")
    di.put(AddItemsForCustomer(1,List(2,3), "apples,bananas"))
    di.put(AddItemsForCustomer(2,List(2,3), "oranges"))
    di.put(AddItemsForCustomer(3,List(2,3), "grapes"))

    di.put(ListBasket(4, List(2,3)))
    di.put(ClearBasket.to(BasketId(3)))
    di.put(ListBasket(4, List(2,3)))
    println("Done.")
    es.stopInputs()

  }

  class PrintingDispatcher[A <: Dispatcher {type ReplayType = List[ActorUnitOfWork]}](val d: A) extends Dispatcher {
    override type ReplayType = d.ReplayType

    def dispatch(unitOfWork: ActorUnitOfWork): Unit = d.dispatch(unitOfWork)

    def replay(from: Long): ReplayType = d.replay(from)

    def commit(reactionRank: Long): Unit = {
      d.commit(reactionRank)
      printResult(d.replay(reactionRank))
    }

    private def printResult(uows: List[ActorUnitOfWork]): Unit = {
      println(s"\nGenerated")
      uows.foreach(println)
    }

    def rollback(): Unit = d.rollback()
  }



  private def awaitUows(d: MemorizingDispatcher, from: Long, timeout: FiniteDuration = 5.seconds, poll: FiniteDuration = 50.millis) = {
    val deadline = timeout.fromNow
    var res: List[dev.g4s.tess.core.ActorUnitOfWork] = Nil
    while (res.isEmpty && deadline.hasTimeLeft()) {
      res = d.replay(from)
      if (res.isEmpty) Thread.sleep(poll.toMillis)
    }
    assert(res.nonEmpty, s"Timed out waiting for UoWs from $from")
    res
  }
}
