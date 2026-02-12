package dev.g4s.tess.app

import dev.g4s.tess.TessConfig.DispatcherFactory
import dev.g4s.tess.{Tess, TessConfig}
import dev.g4s.tess.TessConfig.Resource
import dev.g4s.tess.coordinator.{Dispatcher, MemorizingDispatcher, NotificationDispatcher}
import dev.g4s.tess.core.{ActorUnitOfWork, Notification, TraceContext}
import dev.g4s.tess.domain.{AddItemsForCustomer, BasketFactory, BasketId, ClearBasket, CustomerFactory, ListBasket}
import dev.g4s.tess.input.DirectInput
import dev.g4s.tess.store.{InMemoryEventStore, RocksDbEventStore}
import dev.g4s.tess.syntax.all.*

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object TessDemo extends TessApp {
  private val memorizingDispatcher = new MemorizingDispatcher()
  //  private val dispatcherFactory = () => new NotificationDispatcher(new MemorizingDispatcher())
  private val dispatcherFactory : DispatcherFactory[List[ActorUnitOfWork]] = () => new PrintingDispatcher(memorizingDispatcher)
  private var directInput: DirectInput = null

  /** Wire the app as a Resource-backed Tess instance. */
  override def run: Resource[Tess[Dispatcher { type ReplayType = List[ActorUnitOfWork] }]] =
    Resource(
      () => {
        val tess = Tess
          .builder
          .withActorFactories(CustomerFactory, BasketFactory)
          .withEventStore(Resource.apply(() => new InMemoryEventStore()))
          .withInputSource { iq => directInput = new DirectInput(iq.input); directInput }
          .withDispatcher(dispatcherFactory)
          .build()

        // queue demo messages before start (will drain once runtime starts)
        primeDemoMessages()
        tess
      },
      _.stop()
    )

  private def primeDemoMessages(): Unit = {
    println("Queueing demo workload...")
    directInput.put(AddItemsForCustomer(1,List(2,3), "apples,bananas"), TraceContext(Map("cid" -> "1")))
    directInput.put(AddItemsForCustomer(2,List(2,3), "oranges"), TraceContext(Map("cid" -> "2")))
    directInput.put(AddItemsForCustomer(3,List(2,3), "grapes"), TraceContext(Map("cid" -> "3")))
    directInput.put(ListBasket(4, List(2,3)),TraceContext(Map("cid" -> "4")))
    directInput.put(ClearBasket.to(BasketId(3)),TraceContext(Map("cid" -> "5")))
    directInput.put(ListBasket(4, List(2,3)),TraceContext(Map("cid" -> "6")))
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
