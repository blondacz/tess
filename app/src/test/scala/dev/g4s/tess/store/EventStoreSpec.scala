package dev.g4s.tess.store

import dev.g4s.tess.core.{ActorKey, ActorUnitOfWork}
import dev.g4s.tess.domain.{Customer, CustomerCreated, CustomerId, CustomerUpdated}
import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.{Files, Path}
import java.util.Comparator

class EventStoreSpec extends AnyFunSuite {

  private case class StoreFixture(name: String, build: () => (EventStore, () => Unit))

  private val fixtures = List(
    StoreFixture("InMemoryEventStore", () => {
      val store = new InMemoryEventStore()
      (store, () => ())
    }),
    StoreFixture("RocksDbEventStore", () => {
      val dir = Files.createTempDirectory("event-store-spec")
      val store = new RocksDbEventStore(dir.toString)
      (store, () => {
        store match {
          case c: AutoCloseable => c.close()
          case null => () // keep match exhaustive without warning
        }
        deleteRecursively(dir)
      })
    })
  )

  fixtures.foreach { fixture =>

    test(s"${fixture.name} stores and loads unit-of-work batches in order") {
      val (store, cleanup) = fixture.build()
      try {
        val key = ActorKey(CustomerId(1), classOf[Customer])

        val uow1 = ActorUnitOfWork(
          key = key,
          actorVersion = 1,
          events = Seq(CustomerCreated(1, CustomerId(1), "apples")),
          startingEventRank = 1
        )

        val uow2 = ActorUnitOfWork(
          key = key,
          actorVersion = 2,
          events = Seq(CustomerUpdated(2, CustomerId(1), "apples,oranges")),
          startingEventRank = 2
        )

        store.store(uow1).fold(fail(_), identity)
        store.store(uow2).fold(fail(_), identity)

        val loaded = store.load(key).fold(throw _, identity)
        assert(loaded == List(uow1, uow2))

        assert(store.lastEventRank.contains(uow2.endingEventRank))
      } finally {
        cleanup()
      }
    }

    test(s"${fixture.name} isolates actors and tracks lastEventRank of last write") {
      val (store, cleanup) = fixture.build()
      try {
        val keyA = ActorKey(CustomerId(10), classOf[Customer])
        val keyB = ActorKey(CustomerId(20), classOf[Customer])

        val a1 = ActorUnitOfWork(keyA, actorVersion = 1, Seq(CustomerCreated(1, CustomerId(10), "A")), startingEventRank = 1)
        val b1 = ActorUnitOfWork(keyB, actorVersion = 1, Seq(CustomerCreated(2, CustomerId(20), "B")), startingEventRank = 5)

        store.store(a1).fold(fail(_), identity)
        store.store(b1).fold(fail(_), identity)

        val loadedA = store.load(keyA).fold(throw _, identity)
        val loadedB = store.load(keyB).fold(throw _, identity)

        assert(loadedA == List(a1))
        assert(loadedB == List(b1))

        assert(store.lastEventRank.contains(b1.endingEventRank))
      } finally {
        cleanup()
      }
    }

    test(s"${fixture.name} returns empty list for unknown actor") {
      val (store, cleanup) = fixture.build()
      try {
        val missing = store.load(ActorKey(CustomerId(999), classOf[Customer])).fold(throw _, identity)
        assert(missing.isEmpty)
        assert(store.lastEventRank.isEmpty)
      } finally {
        cleanup()
      }
    }
  }

  private def deleteRecursively(path: Path): Unit = {
    if (Files.exists(path)) {
      Files.walk(path)
        .sorted(Comparator.reverseOrder())
        .forEach(p => Files.deleteIfExists(p))
    }
  }
}
