package dev.g4s.tess.store

import dev.g4s.tess.core.{ActorKey, ActorUnitOfWork, Id}
import dev.g4s.tess.proto.{fromProtoActorUnitOfWork, toProtoActorUnitOfWork}
import dev.g4s.tess.raft.v1.tess.ActorUnitOfWork as ProtoActorUnitOfWork
import org.rocksdb.*

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import scala.util.Using

// RocksDB-backed EventStore using three column families:
// actors:   a:<actorType>:<actorId>:<reactionRank> -> ActorUnitOfWork (protobuf)
// raft-log: r:<group>:<logIndex>                    -> RaftLogIndexEntry (reserved, not used here)
// raft-meta:r:<group>:meta:<field>                  -> metadata entries (stores lastReactionRank)
class RocksDbEventStore(dbPath: String, group: String = "main") extends EventStore with AutoCloseable {
  RocksDB.loadLibrary()

  private val cfOptions = new ColumnFamilyOptions()
  private val options = new DBOptions()
    .setCreateIfMissing(true)
    .setCreateMissingColumnFamilies(true)

  private val descriptors = java.util.Arrays.asList(
    new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, cfOptions),
    new ColumnFamilyDescriptor("actors".getBytes(StandardCharsets.UTF_8), cfOptions),
    new ColumnFamilyDescriptor("raft-log".getBytes(StandardCharsets.UTF_8), cfOptions),
    new ColumnFamilyDescriptor("raft-meta".getBytes(StandardCharsets.UTF_8), cfOptions)
  )

  private val handles = new java.util.ArrayList[ColumnFamilyHandle]()
  private val db = RocksDB.open(options, dbPath, descriptors, handles)
  private val writeOptions = new WriteOptions()

  private val actorsCf = handles.get(1)
  private val raftMetaCf = handles.get(3)

  private var cachedLastRank: Option[Long] = readLastReactionRank()

  private val LastReactionRankKey = "lastReactionRank"

  override def store(uow: ActorUnitOfWork): Either[Throwable, Unit] = {
    Using(new WriteBatch()) { batch =>
        val key = actorKey(uow.key, uow.startingReactionRank)
        val value = toProtoActorUnitOfWork(uow).toByteArray
        batch.put(actorsCf, key, value)
        cachedLastRank = Some(uow.endingReactionRank)
        //TODO: write the log as well, we can always rollback on the last comitted
        batch.put(raftMetaCf, raftMetaKey(LastReactionRankKey), longToBytes(uow.endingReactionRank))
        db.write(writeOptions, batch)
        ()
    }.toEither
  }

  override def load(key: ActorKey): Either[Throwable, List[ActorUnitOfWork]] =
    val prefix = actorKeyPrefix(key)
    val prefixBytes = prefix.getBytes(StandardCharsets.UTF_8)

    Using(db.newIterator(actorsCf)) { iter =>
        val results = collection.mutable.ListBuffer.empty[(Long, ActorUnitOfWork)]
        iter.seek(prefixBytes)
        while (iter.isValid && new String(iter.key(), StandardCharsets.UTF_8).startsWith(prefix)) {
          val entryKey = new String(iter.key(), StandardCharsets.UTF_8)
          val rank = parseRank(entryKey)
          val uow = fromProtoActorUnitOfWork(ProtoActorUnitOfWork.parseFrom(iter.value()))
          results += rank -> uow
          iter.next()
        }
        results.sortBy(_._1).map(_._2).toList
    }.toEither

  override def lastReactionRank: Option[Long] = {
    cachedLastRank.orElse {
      cachedLastRank = readLastReactionRank()
      cachedLastRank
    }
  }

  override def close(): Unit = {
    db.flushWal(true)
    db.cancelAllBackgroundWork(true)
    handles.forEach(_.close())
    db.close()
    writeOptions.close()
    options.close()
    cfOptions.close()
  }

  private def actorKeyPrefix(key: ActorKey): String =
    s"a:${key.clazz.getName}:${idAsString(key.id)}:"

  private def actorKey(key: ActorKey, reactionRank: Long): Array[Byte] =
    s"${actorKeyPrefix(key)}$reactionRank".getBytes(StandardCharsets.UTF_8)

  private def parseRank(key: String): Long =
    key.split(':').last.toLong

  private def idAsString(id: Id): String = id.productIterator.mkString("_")

  private def raftMetaKey(field: String): Array[Byte] =
    s"r:$group:meta:$field".getBytes(StandardCharsets.UTF_8)

  private def readLastReactionRank(): Option[Long] =
    Option(db.get(raftMetaCf, raftMetaKey(LastReactionRankKey))).map(bytesToLong)

  private def longToBytes(value: Long): Array[Byte] =
    ByteBuffer.allocate(java.lang.Long.BYTES).putLong(value).array()

  private def bytesToLong(bytes: Array[Byte]): Long =
    ByteBuffer.wrap(bytes).getLong
}
