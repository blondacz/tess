package dev.g4s.tess

import org.scalatest.funsuite.AnyFunSuite

class EventSourcedSystemSpec extends AnyFunSuite {

  test("EventSourcedSystem should process messages and produce UoWs for both actors") {
    val es = new EventSourcedSystem(Seq(FirstActorFactory, SecondActorFactory))

    val uows1 = es.process(FirstActorMessage(1, List(2, 3), "hello")).fold(throw _, identity)
    assert(uows1.nonEmpty)

    assert(uows1.forall(_.startingEventRank >= 1))

    val uows2 = es.process(FirstActorMessage(2, List(2, 3), ", world")).fold(throw _, identity)
    assert(uows2.nonEmpty)
    // Event ranks must increase across calls
    val max1 = uows1.map(_.endingEventRank).max
    val min2 = uows2.map(_.startingEventRank).min
    assert(min2 > max1)
  }

  test("SecondActor should be updated based on FirstActor events") {
    val es = new EventSourcedSystem(Seq(FirstActorFactory, SecondActorFactory))

    val id = 5L
    val uows = es.process(FirstActorMessage(10, List(id), "X")).fold(throw _, identity)
    assert(uows.nonEmpty)

    // Collect all events to verify propagated updates
    val events = uows.flatMap(_.events)

    // There should be a SecondActorUpdated reflecting FirstActorCreated
    val secondUpdates = events.collect { case e: SecondActorUpdated => e }
    assert(secondUpdates.nonEmpty)
    assert(secondUpdates.exists(_.text.contains("First actor inserted with text: X")))
  }
}
