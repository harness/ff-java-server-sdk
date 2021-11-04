package io.harness.cf.client.api;

import io.harness.cf.client.common.Storage;
import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

public class FileMapStore implements Storage {

  private final DB db;
  private final HTreeMap<String, Object> map;

  public FileMapStore(@NonNull String name) {
    db = DBMaker.fileDB(name).closeOnJvmShutdown().fileChannelEnable().transactionEnable().make();
    map = db.hashMap("map", Serializer.STRING, Serializer.JAVA).createOrOpen();
  }

  @Override
  public void set(@NonNull String key, @NonNull Object value) {
    map.put(key, value);
    db.commit();
  }

  @Override
  public Object get(@NonNull String key) {
    return map.get(key);
  }

  @Override
  public void del(@NonNull String key) {
    map.remove(key);
    db.commit();
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
