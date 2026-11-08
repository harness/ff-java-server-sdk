package io.harness.cf.client.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.harness.cf.client.common.Storage;
import io.harness.cf.client.logger.LogUtil;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class XmlFileMapStore implements Storage, AutoCloseable {
  private static final String FORMAT_KEY = "format";
  private static final String FORMAT = "harness-sdk-store-v1";
  private static final String ENTRY_PREFIX = "entry.";
  private final Map<String, Object> map = new LinkedHashMap<>();
  private final Gson gson = new Gson();
  private final Path filename;
  private boolean closed;

  static {
    LogUtil.setSystemProps();
  }

  public XmlFileMapStore(@NonNull String filename) {
    this.filename = Paths.get(filename).toAbsolutePath();
    Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(this.filename)) {
      if (Files.size(this.filename) == 0) return;
      properties.loadFromXML(input);
      if (FORMAT.equals(properties.getProperty(FORMAT_KEY))) {
        for (String key : properties.stringPropertyNames()) {
          if (FORMAT_KEY.equals(key)) continue;
          if (!key.startsWith(ENTRY_PREFIX))
            throw new IllegalArgumentException("Invalid store entry");
          String decoded =
              new String(
                  Base64.getUrlDecoder().decode(key.substring(ENTRY_PREFIX.length())),
                  StandardCharsets.UTF_8);
          map.put(decoded, decode(properties.getProperty(key)));
        }
      } else {
        String format = properties.getProperty(FORMAT_KEY, "");
        if (format.startsWith("harness-sdk-store-"))
          throw new IllegalArgumentException("Unsupported store format");
        for (String key : properties.stringPropertyNames())
          map.put(key, properties.getProperty(key));
      }
    } catch (NoSuchFileException ignored) {
    } catch (IOException | RuntimeException ex) {
      throw new IllegalStateException("Cannot read feature flag store " + this.filename, ex);
    }
  }

  @Override
  public synchronized void set(@NonNull String key, @NonNull Object value) {
    requireOpen();
    typeOf(value);
    map.put(key, value);
  }

  @Override
  public synchronized Object get(@NonNull String key) {
    return map.get(key);
  }

  @Override
  public synchronized void delete(@NonNull String key) {
    requireOpen();
    map.remove(key);
  }

  @Override
  public synchronized List<String> keys() {
    return new ArrayList<>(map.keySet());
  }

  private String typeOf(Object value) {
    if (value instanceof FeatureConfig) return "flag";
    if (value instanceof Segment) return "segment";
    if (value instanceof String) return "string";
    throw new IllegalArgumentException("Unsupported feature flag store value type");
  }

  private String encode(Object value) {
    JsonObject record = new JsonObject();
    record.addProperty("type", typeOf(value));
    record.add("value", gson.toJsonTree(value));
    return gson.toJson(record);
  }

  private Object decode(String encoded) {
    JsonObject record = gson.fromJson(encoded, JsonObject.class);
    if (record == null
        || !record.has("type")
        || !record.has("value")
        || record.get("value").isJsonNull()) {
      throw new IllegalArgumentException("Invalid feature flag store value");
    }
    switch (record.get("type").getAsString()) {
      case "flag":
        return gson.fromJson(record.get("value"), FeatureConfig.class);
      case "segment":
        return gson.fromJson(record.get("value"), Segment.class);
      case "string":
        return record.get("value").getAsString();
      default:
        throw new IllegalArgumentException("Unsupported feature flag store value type");
    }
  }

  private void requireOpen() {
    if (closed) throw new IllegalStateException("Feature flag store is closed");
  }

  @Override
  public synchronized void close() {
    if (closed) return;
    Properties snapshot = new Properties();
    snapshot.setProperty(FORMAT_KEY, FORMAT);
    map.forEach(
        (key, value) ->
            snapshot.setProperty(
                ENTRY_PREFIX
                    + Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(key.getBytes(StandardCharsets.UTF_8)),
                encode(value)));
    Path pending = null;
    try {
      pending =
          Files.createTempFile(filename.getParent(), filename.getFileName() + ".", ".pending");
      try (OutputStream output = Files.newOutputStream(pending)) {
        snapshot.storeToXML(output, "Harness feature flag cache");
      }
      Files.move(
          pending, filename, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      closed = true;
    } catch (IOException ex) {
      log.warn(
          "Store write failed; previous cache is unchanged and pending file is retained at {}",
          pending);
      throw new IllegalStateException("Cannot write feature flag store " + filename, ex);
    }
  }
}
