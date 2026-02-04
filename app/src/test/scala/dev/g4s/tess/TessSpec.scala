package dev.g4s.tess

import dev.g4s.tess._
import dev.g4s.tess.domain._
import org.scalatest.funsuite.AnyFunSuite

class TessSpec extends AnyFunSuite {

  test("EventSourcedSystem should process messages and produce UoWs for both actors") {
    val es =  Tess.builder.withActorFactories(CustomerFactory, BasketFactory).build()

    val uows1 = es.process(AddItemsForCustomer(1, List(2, 3), "apples,bananas")).fold(throw _, identity)
    assert(uows1.nonEmpty)

    assert(uows1.forall(_.startingReactionRank >= 1))

    val uows2 = es.process(AddItemsForCustomer(2, List(2, 3), "oranges")).fold(throw _, identity)
    assert(uows2.nonEmpty)
    // Event ranks must increase across calls
    val max1 = uows1.map(_.endingReactionRank).max
    val min2 = uows2.map(_.startingReactionRank).min
    assert(min2 > max1)
  }

  test("Basket should be updated based on Customer events and can be listed directly") {
    val es = Tess.builder.withActorFactories(CustomerFactory, BasketFactory).build()

    val id = 5L
    val uows = es.process(AddItemsForCustomer(10, List(id), "milk,bread")).fold(throw _, identity)
    assert(uows.nonEmpty)

    // Collect all events to verify propagated updates
    val events = uows.flatMap(_.events)

    // There should be a BasketUpdated reflecting CustomerCreated
    val basketUpdates = events.collect { case e: BasketUpdated => e }
    assert(basketUpdates.nonEmpty)
    assert(basketUpdates.exists(_.itemsCsv.contains("milk")))

    val basketId = id
    val basketList = es.process(ListBasket(11, List(basketId))).fold(throw _, identity)
    val basketListEvents = basketList.flatMap(_.events).collect { case e: BasketListed => e }
    assert(basketListEvents.exists(_.itemsCsv.contains("milk")))
  }

  test("Commands produced by actors are routed directly to target actors") {
    val es = Tess.builder.withActorFactories(CustomerFactory, BasketFactory).build()

    val basketId = 8L
    val uows = es.process(AddItemsForCustomer(99, List(basketId), "coffee")).fold(throw _, identity)

    val commandApplied = uows.flatMap(_.events).collect { case e: BasketCommandApplied if e.basketId.id == basketId => e }
    assert(commandApplied.nonEmpty)
    assert(commandApplied.exists(_.itemsCsv.contains("coffee")))
  }
}
