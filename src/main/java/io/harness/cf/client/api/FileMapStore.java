package io.harness.cf.client.api;

import io.harness.cf.client.common.Storage;
import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

@Slf4j
public class FileMapStore implements Storage, AutoCloseable {

  private final DB db;
  private final HTreeMap<String, Object> map;

  public FileMapStore(@NonNull String name) {
    db = DBMaker.fileDB(name).closeOnJvmShutdown().fileChannelEnable().transactionEnable().make();
    map = db.hashMap("map", Serializer.STRING, Serializer.JAVA).createOrOpen();
  }

  @Override
  public void set(@NonNull String key, @NonNull Object value) {
    try {
      map.put(key, value);
      db.commit();
    } catch (UnsupportedOperationException
        | ClassCastException
        | NullPointerException
        | IllegalArgumentException e) {
      log.error(
          "Exception was raised when storing the key {} with the error message {}",
          key,
          e.getMessage());
    }
  }

  @Override
  public Object get(@NonNull String key) {
    try {
      return map.get(key);
    } catch (ClassCastException | NullPointerException e) {
      log.error(
          "Exception was raised while getting the key {} with the error message {}",
          key,
          e.getMessage());
      return null;
    }
  }

  @Override
  public void delete(@NonNull String key) {
    try {
      map.remove(key);
      db.commit();
    } catch (UnsupportedOperationException | ClassCastException | NullPointerException e) {
      log.error(
          "Exception was raised while deleting the key {} with the error message {}",
          key,
          e.getMessage());
    }
  }

  @Override
  public List<String> keys() {
    return new ArrayList<>(map.keySet());
  }

  @Override
  public void close() {
    db.close();
  }
}
