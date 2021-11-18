package io.harness.cf.client.connector;

import com.google.gson.Gson;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Metrics;
import io.harness.cf.model.Segment;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;

@Slf4j
public class LocalConnector implements Connector, AutoCloseable {
  private final String source;
  private final Gson gson = new Gson();
  private FileWatcher flagsWatcher;
  private FileWatcher segmentsWatcher;

  private final ExecutorService pool = Executors.newFixedThreadPool(2);

  public LocalConnector(@NonNull String source) {
    this.source = source;
  }

  @Override
  public String authenticate() {
    // there is no authentication so just return any string
    return "success";
  }

  protected List<File> listFiles(@NonNull String source, @NonNull String domain)
      throws ConnectorException {
    try {
      return Files.list(Paths.get(source, domain))
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".json"))
          .map(Path::toFile)
          .collect(Collectors.toList());

    } catch (IOException e) {
      throw new ConnectorException(e.getMessage());
    }
  }

  protected <T> ImmutablePair<T, Exception> loadFile(@NonNull File file, Class<T> classOfT) {
    try {
      final String content = new String(Files.readAllBytes(file.toPath()));
      return ImmutablePair.of(gson.fromJson(content, classOfT), null);
    } catch (Exception e) {
      return ImmutablePair.of(null, e);
    }
  }

  protected <T> T load(@NonNull File file, Class<T> classOfT) {
    final ImmutablePair<T, Exception> pair = loadFile(file, classOfT);
    if (pair.right != null) {
      log.error(
          "Exception was raised while loading flag file {} with error {}",
          file.getName(),
          pair.right.getMessage());
      return null;
    }
    return pair.left;
  }

  @Override
  public List<FeatureConfig> getFlags() throws ConnectorException {
    List<FeatureConfig> configs = new ArrayList<>();

    listFiles(source, "flags")
        .forEach(
            file -> {
              FeatureConfig config = load(file, FeatureConfig.class);
              if (config != null) configs.add(config);
            });

    return configs;
  }

  @Override
  public FeatureConfig getFlag(@NonNull String identifier) throws ConnectorException {
    Path path = Paths.get(source, "flags", identifier + ".json");
    ImmutablePair<FeatureConfig, Exception> pair = loadFile(path.toFile(), FeatureConfig.class);
    if (pair.right != null) {
      throw new ConnectorException(pair.right.getMessage());
    }
    return pair.left;
  }

  @Override
  public List<Segment> getSegments() throws ConnectorException {
    List<Segment> segments = new ArrayList<>();

    listFiles(source, "segments")
        .forEach(
            file -> {
              Segment segment = load(file, Segment.class);
              if (segment != null) segments.add(segment);
            });

    return segments;
  }

  @Override
  public Segment getSegment(@NonNull String identifier) throws ConnectorException {
    Path path = Paths.get(source, "segments", identifier + ".json");
    ImmutablePair<Segment, Exception> pair = loadFile(path.toFile(), Segment.class);
    if (pair.right != null) {
      throw new ConnectorException(pair.right.getMessage());
    }
    return pair.left;
  }

  @Override
  public void postMetrics(Metrics metrics) throws ConnectorException {
    SimpleDateFormat df = new SimpleDateFormat("yyyy_MM_dd");
    String filename = String.format("%s.jsonl", df.format(new Date()));
    String content = gson.toJson(metrics) + '\n';
    try {
      Files.write(
          Paths.get(source, "metrics", filename),
          content.getBytes(),
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new ConnectorException(e.getMessage());
    }
  }

  @Override
  public void stream(Updater updater) throws ConnectorException {
    try {
      flagsWatcher = new FileWatcher("flag", Paths.get(source, "flags"), updater);
      segmentsWatcher = new FileWatcher("target-segment", Paths.get(source, "segments"), updater);
      updater.onReady();
      pool.submit(flagsWatcher);
      pool.submit(segmentsWatcher);
      updater.onConnected();
    } catch (IOException e) {
      throw new ConnectorException(e.getMessage());
    }
  }

  @Override
  public void close() {
    flagsWatcher.close();
    segmentsWatcher.close();
    pool.shutdown();
  }
}
