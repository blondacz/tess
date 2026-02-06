package dev.g4s.tess.app

import dev.g4s.tess.{Tess, TessConfig}
import dev.g4s.tess.coordinator.{Dispatcher, MemorizingDispatcher, NotificationDispatcher}
import dev.g4s.tess.core.{ActorUnitOfWork, Notification}
import dev.g4s.tess.domain.{AddItemsForCustomer, BasketFactory, BasketId, ClearBasket, CustomerFactory, ListBasket}
import dev.g4s.tess.store.{InMemoryEventStore, RocksDbEventStore}
import dev.g4s.tess.syntax.all._
import dev.g4s.tess.app.PrintMagnet
import PrintMagnet.given
import scala.Conversion


object TessDemo {
//  private val dispatcherFactory = () => new NotificationDispatcher(new MemorizingDispatcher())
  private val dispatcherFactory = () => new MemorizingDispatcher()

  def main(args: Array[String]): Unit = {
      
    val es = Tess
      .builder
      .withActorFactories(CustomerFactory, BasketFactory)
      .withEventStore(() => new RocksDbEventStore("roxsdb","g1"))
      .withDispatcher(dispatcherFactory)
      .build()

    val events= es.process(AddItemsForCustomer(1,List(2,3), "apples,bananas")).fold(throw _,identity)
    printResult(events)
    val events2 = es.process(AddItemsForCustomer(2,List(2,3), "oranges")).fold(throw _,identity)
    printResult(events2)
    val events3 = es.process(AddItemsForCustomer(3,List(2,3), "grapes")).fold(throw _,identity)
    printResult(events3)

    val directBasket = es.process(ListBasket(4, List(2,3))).fold(throw _, identity)
    printResult(directBasket) 
    val directBasket1 = es.process(ClearBasket.to(BasketId(3))).fold(throw _, identity)
    printResult(directBasket1) 
    val directBasket2 = es.process(ListBasket(4, List(2,3))).fold(throw _, identity)
    printResult(directBasket2)
  }

  private def printResult[A](a: A)(using Conversion[A, PrintMagnet]): Unit = {
    println(s"\nGenerated")
    summon[Conversion[A, PrintMagnet]](a)()
  }
}
