package dev.g4s.tess.store

import dev.g4s.tess.core.{ActorKey, ActorUnitOfWork}
import dev.g4s.tess.domain.{Customer, CustomerCreated, CustomerId, CustomerUpdated}
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

  test("store and load preserve ordering and track lastReactionRank across restarts") {
    withTempDir { dir =>
      val key = ActorKey(CustomerId(1), classOf[Customer])

      val uow1 = ActorUnitOfWork(
        key = key,
        actorVersion = 1,
        reactions = Seq(CustomerCreated(1, CustomerId(1), "apples")),
        startingReactionRank = 1
      )

      val uow2 = ActorUnitOfWork(
        key = key,
        actorVersion = 2,
        reactions = Seq(CustomerUpdated(2, CustomerId(1), "apples,oranges")),
        startingReactionRank = 2
      )

      // First session: write two unit-of-work batches
      val store = new RocksDbEventStore(dir.toString)
      store.store(uow1).fold(fail(_), identity)
      store.store(uow2).fold(fail(_), identity)

      assert(store.lastReactionRank.contains(uow2.endingReactionRank))

      val loaded = store.load(key).fold(throw _, identity)
      assert(loaded == List(uow1, uow2))

      store.close()

      // Second session: ensure data and last rank survive a restart
      val reopened = new RocksDbEventStore(dir.toString)
      assert(reopened.lastReactionRank.contains(uow2.endingReactionRank))

      val reloaded = reopened.load(key).fold(throw _, identity)
      assert(reloaded == List(uow1, uow2))

      val missing = reopened.load(ActorKey(CustomerId(999), classOf[Customer])).fold(throw _, identity)
      assert(missing.isEmpty)

      reopened.close()
    }
  }
}
