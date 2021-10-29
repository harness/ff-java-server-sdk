package io.harness.cf.client.api;

import com.oath.halodb.*;
import io.harness.cf.client.common.KeyValueStore;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileStore implements KeyValueStore {
  private HaloDB db;

  public FileStore() {
    // Open a db with default options.
    HaloDBOptions options = new HaloDBOptions();

    // Size of each data file will be 10MB.
    options.setMaxFileSize(1024 * 1024 * 10);

    // Size of each tombstone file will be 64MB
    // Large file size mean less file count but will slow down db open time. But if set
    // file size too small, it will result large amount of tombstone files under db folder
    options.setMaxTombstoneFileSize(2 * 1024 * 1024);

    // Set the number of threads used to scan index and tombstone files in parallel
    // to build in-memory index during db open. It must be a positive number which is
    // not greater than Runtime.getRuntime().availableProcessors().
    // It is used to speed up db open time.
    options.setBuildIndexThreads(8);

    // The threshold at which page cache is synced to disk.
    // data will be durable only if it is flushed to disk, therefore
    // more data will be lost if this value is set too high. Setting
    // this value too low might interfere with read and write performance.
    options.setFlushDataSizeBytes(1024);

    // Setting this value is important as it helps to preallocate enough
    // memory for the off-heap cache. If the value is too low the db might
    // need to rehash the cache. For a db of size n set this value to 2*n.
    options.setNumberOfRecords(10_000_000);

    // Delete operation for a key will write a tombstone record to a tombstone file.
    // the tombstone record can be removed only when all previous version of that key
    // has been deleted by the compaction job.
    // enabling this option will delete during startup all tombstone records whose previous
    // versions were removed from the data file.
    options.setCleanUpTombstonesDuringOpen(true);

    // HaloDB does native memory allocation for the in-memory index.
    // Enabling this option will release all allocated memory back to the kernel when the db is
    // closed.
    // This option is not necessary if the JVM is shutdown when the db is closed, as in that case
    // allocated memory is released automatically by the kernel.
    // If using in-memory index without memory pool this option,
    // depending on the number of records in the database,
    // could be a slow as we need to call _free_ for each record.
    options.setCleanUpInMemoryIndexOnClose(false);

    // ** settings for memory pool **
    options.setUseMemoryPool(true);

    // Hash table implementation in HaloDB is similar to that of ConcurrentHashMap in Java 7.
    // Hash table is divided into segments and each segment manages its own native memory.
    // The number of segments is twice the number of cores in the machine.
    // A segment's memory is further divided into chunks whose size can be configured here.
    options.setMemoryPoolChunkSize(2 * 1024 * 1024);

    // using a memory pool requires us to declare the size of keys in advance.
    // Any write request with key length greater than the declared value will fail, but it
    // is still possible to store keys smaller than this declared size.
    options.setFixedKeySize(8);
    try {
      db = HaloDB.open("ff-harness", options);
    } catch (HaloDBException e) {
      e.printStackTrace(); // logging
    }
  }

  @Override
  public void set(@NonNull String key, @NonNull Object value) {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
      ObjectOutputStream out;
      out = new ObjectOutputStream(bos);
      out.writeObject(value);
      out.flush();
      db.put(key.getBytes(StandardCharsets.UTF_8), bos.toByteArray());
    } catch (HaloDBException | IOException e) {
      log.error("Exception while storing a value, {}", e.getMessage());
    }
  }

  @Override
  public Object get(@NonNull String key) {
    byte[] bytes = new byte[0];
    try {
      bytes = db.get(key.getBytes(StandardCharsets.UTF_8));
    } catch (HaloDBException e) {
      log.error("Exception while getting a value for key {} with error {}", key, e.getMessage());
      return null;
    }

    ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
    try (ObjectInput in = new ObjectInputStream(bis)) {
      return in.readObject();
    } catch (IOException | ClassNotFoundException e) {
      log.error(
          "Exception while converting bytes to object for key {} with error {}",
          key,
          e.getMessage());
      return null;
    }
  }

  @Override
  public void del(@NonNull String key) {
    try {
      db.delete(key.getBytes(StandardCharsets.UTF_8));
    } catch (HaloDBException e) {
      log.error("Exception while deleting key {} with error {}", key, e.getMessage());
    }
  }

  @Override
  public List<String> keys() {
    List<String> keys = new ArrayList<>();
    HaloDBKeyIterator haloDBKeyIterator = db.newKeyIterator();
    while (haloDBKeyIterator.hasNext()) {
      RecordKey next = haloDBKeyIterator.next();
      byte[] bytes = next.getBytes();
      keys.add(new String(bytes, StandardCharsets.UTF_8));
    }
    return keys;
  }
}
