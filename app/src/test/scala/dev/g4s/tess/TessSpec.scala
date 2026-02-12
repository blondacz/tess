package dev.g4s.tess

import dev.g4s.tess.*
import dev.g4s.tess.coordinator.MemorizingDispatcher
import dev.g4s.tess.domain.*
import dev.g4s.tess.core.{CommandMessage, TraceContext}
import dev.g4s.tess.input.DirectInput
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*

class TessSpec extends AnyFunSuite with Matchers {

  test("EventSourcedSystem should process messages and produce UoWs for both actors") {
    val disp = new MemorizingDispatcher()
    var direct: DirectInput = null
    val es = Tess.builder
      .withActorFactories(CustomerFactory, BasketFactory)
      .withDispatcher(() => disp)
      .withInputSource{iq => direct = new DirectInput(iq.input);direct}
      .build()
      .start()
    try {
      val traceContext = new TraceContext(Map("tkey" -> "tval"))
      direct.put(AddItemsForCustomer(1, List(2), "apples,bananas"), traceContext)
      val uows1 = awaitUows(disp, 1)
      uows1.size should be (2) // customer then basket

      val customerUow = uows1.head
      customerUow.key shouldBe ActorKey(CustomerId(2), classOf[Customer])
      customerUow.actorVersion shouldBe 1
      customerUow.reactions shouldBe Seq(
        CustomerCreated(1, CustomerId(2)),
        CustomerUpdated(1, CustomerId(2), "apples,bananas")
      )
      customerUow.startingReactionRank shouldBe 1L
      customerUow.trace shouldBe traceContext

      val basketUow = uows1(1)
      basketUow.key shouldBe ActorKey(BasketId(2), classOf[Basket])
      basketUow.actorVersion shouldBe 1
      basketUow.reactions shouldBe Seq(
        BasketCreated(1, BasketId(2)),
        BasketUpdated(1, BasketId(2), "apples,bananas")
      )
      basketUow.startingReactionRank shouldBe customerUow.endingReactionRank + 1
      
      direct.put(AddItemsForCustomer(2, List(2), "oranges"))
      val uows2 = awaitUows(disp, uows1.last.endingReactionRank + 1)
      uows2.size should be(2)
      assert(uows1.nonEmpty)
      assert(uows1.forall(_.startingReactionRank >= 1))
      assert(uows2.nonEmpty)
      val max1 = uows1.map(_.endingReactionRank).max
      val min2 = uows2.map(_.startingReactionRank).min
      assert(min2 == max1 + 1)
      assert(uows2.map(_.endingReactionRank).max > uows1.last.endingReactionRank)
    } finally es.stop()
  }

  test("Basket should be updated based on Customer events and can be listed directly") {
    val disp = new MemorizingDispatcher()
    var direct: DirectInput = null
    val es = Tess.builder
      .withActorFactories(CustomerFactory, BasketFactory)
      .withDispatcher(() => disp)
      .withInputSource{iq => direct = new DirectInput(iq.input);direct}
      .build()
      .start()
    try {
      val id = 5L
      direct.put(AddItemsForCustomer(10, List(id), "milk,bread"))
      val uows = awaitUows(disp, 1)
      assert(uows.nonEmpty)
      val events = uows.flatMap(_.events)
      val basketUpdates = events.collect { case e: BasketUpdated => e }
      assert(basketUpdates.nonEmpty)
      assert(basketUpdates.exists(_.itemsCsv.contains("milk")))

      direct.put(ListBasket(11, List(id)))
      val basketList = awaitUows(disp, uows.last.endingReactionRank + 1)
      val basketListEvents = basketList.flatMap(_.reactions).collect { case e: BasketListed => e }
      assert(basketListEvents.exists(_.itemsCsv.contains("milk")))
    } finally es.stop()
  }

  test("Commands are routed directly to target actors") {
    val disp = new MemorizingDispatcher()
    var direct: DirectInput = null
    val es = Tess.builder
      .withActorFactories(CustomerFactory, BasketFactory)
      .withDispatcher(() => disp)
      .withInputSource{iq => direct = new DirectInput(iq.input);direct}
      .build()
      .start()
    try {
      val basketId = 8L
      direct.put(AddItemsForCustomer(1, List(basketId), "coffee")) // ensure basket exists
      val uowsCreate = awaitUows(disp, 1)
      direct.put(CommandMessage(ClearBasket, List(BasketId(basketId))))
      val uows = awaitUows(disp, uowsCreate.last.endingReactionRank + 1)
      val basketCleared = uows.flatMap(_.events).collect { case e: BasketCleared if e.basketId.id == basketId => e }
      assert(basketCleared.nonEmpty)
    } finally es.stop()
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
