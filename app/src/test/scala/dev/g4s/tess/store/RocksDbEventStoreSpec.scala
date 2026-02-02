package dev.g4s.tess.store

import dev.g4s.tess.core.{ActorKey, ActorUnitOfWork}
import dev.g4s.tess.domain.{FirstActor, FirstActorCreatedEvent, FirstActorUpdated, StandardId}
import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.{Files, Path}
import java.util.Comparator

class RocksDbEventStoreSpec extends AnyFunSuite {

  private def withTempDir(testCode: Path => Any): Unit = {
    val dir = Files.createTempDirectory("rocksdb-eventstore-spec")
    try testCode(dir)
    finally deleteRecursively(dir)
  }

  private def deleteRecursively(path: Path): Unit = {
    if (Files.exists(path)) {
      Files.walk(path)
        .sorted(Comparator.reverseOrder())
        .forEach(f => Files.deleteIfExists(f))
    }
  }

  test("store and load preserve ordering and track lastEventRank across restarts") {
    withTempDir { dir =>
      val key = ActorKey(StandardId(1), classOf[FirstActor])

      val uow1 = ActorUnitOfWork(
        key = key,
        actorVersion = 1,
        events = Seq(FirstActorCreatedEvent(1, StandardId(1), "hi")),
        startingEventRank = 1
      )

      val uow2 = ActorUnitOfWork(
        key = key,
        actorVersion = 2,
        events = Seq(FirstActorUpdated(2, StandardId(1), "there")),
        startingEventRank = 2
      )

      // First session: write two unit-of-work batches
      val store = new RocksDbEventStore(dir.toString)
      store.store(uow1).fold(fail(_), identity)
      store.store(uow2).fold(fail(_), identity)

      assert(store.lastEventRank.contains(uow2.endingEventRank))

      val loaded = store.load(key).fold(throw _, identity)
      assert(loaded == List(uow1, uow2))

      store.close()

      // Second session: ensure data and last rank survive a restart
      val reopened = new RocksDbEventStore(dir.toString)
      assert(reopened.lastEventRank.contains(uow2.endingEventRank))

      val reloaded = reopened.load(key).fold(throw _, identity)
      assert(reloaded == List(uow1, uow2))

      val missing = reopened.load(ActorKey(StandardId(999), classOf[FirstActor])).fold(throw _, identity)
      assert(missing.isEmpty)

      reopened.close()
    }
  }
}
