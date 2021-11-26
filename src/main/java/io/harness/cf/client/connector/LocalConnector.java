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
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;

@Slf4j
public class LocalConnector implements Connector, AutoCloseable {
  private final String source;
  private final Gson gson = new Gson();

  public LocalConnector(@NonNull final String source) {
    this.source = source;
  }

  @Override
  public String authenticate() {
    // there is no authentication so just return any string
    return "success";
  }

  protected Stream<File> listFiles(@NonNull final String source, @NonNull final String domain)
      throws ConnectorException {
    try {
      return Files.list(Paths.get(source, domain))
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".json"))
          .map(Path::toFile);

    } catch (IOException e) {
      throw new ConnectorException(e.getMessage());
    }
  }

  protected <T> ImmutablePair<T, Exception> loadFile(
      @NonNull final File file, @NonNull final Class<T> classOfT) {
    try {
      final String content = new String(Files.readAllBytes(file.toPath()));
      return ImmutablePair.of(gson.fromJson(content, classOfT), null);
    } catch (Exception e) {
      return ImmutablePair.of(null, e);
    }
  }

  protected <T> T load(@NonNull final File file, @NonNull final Class<T> classOfT) {
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
    return listFiles(source, "flags")
        .map(file -> load(file, FeatureConfig.class))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @Override
  public FeatureConfig getFlag(@NonNull final String identifier) throws ConnectorException {
    final Path path = Paths.get(source, "flags", identifier + ".json");
    final ImmutablePair<FeatureConfig, Exception> pair =
        loadFile(path.toFile(), FeatureConfig.class);
    if (pair.right != null) {
      throw new ConnectorException(pair.right.getMessage());
    }
    return pair.left;
  }

  @Override
  public List<Segment> getSegments() throws ConnectorException {
    return listFiles(source, "segments")
        .map(file -> load(file, Segment.class))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @Override
  public Segment getSegment(@NonNull final String identifier) throws ConnectorException {
    final Path path = Paths.get(source, "segments", identifier + ".json");
    final ImmutablePair<Segment, Exception> pair = loadFile(path.toFile(), Segment.class);
    if (pair.right != null) {
      throw new ConnectorException(pair.right.getMessage());
    }
    return pair.left;
  }

  @Override
  public void postMetrics(@NonNull final Metrics metrics) throws ConnectorException {
    final SimpleDateFormat df = new SimpleDateFormat("yyyy_MM_dd");
    final String filename = String.format("%s.jsonl", df.format(new Date()));
    final String content = gson.toJson(metrics) + '\n';
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
  public Service stream(@NonNull final Updater updater) throws ConnectorException {
    try {
      return new FileWatcherService(updater);
    } catch (IOException e) {
      throw new ConnectorException(e.getMessage());
    }
  }

  @Override
  public void close() {
    // no need to close anything
  }

  private class FileWatcherService implements Service, AutoCloseable {
    private final FileWatcher flagWatcher;
    private final FileWatcher segmentWatcher;

    private final Updater updater;

    private FileWatcherService(@NonNull final Updater updater) throws IOException {
      this.updater = updater;
      flagWatcher = new FileWatcher("flag", Paths.get(source, "flags"), updater);
      segmentWatcher = new FileWatcher("target-segment", Paths.get(source, "segments"), updater);

      this.updater.onReady();
    }

    @Override
    public void start() {
      flagWatcher.start();
      segmentWatcher.start();
      updater.onConnected();
    }

    @Override
    public void stop() throws InterruptedException {
      flagWatcher.stop();
      segmentWatcher.stop();
      updater.onDisconnected();
    }

    @Override
    public void close() throws InterruptedException {
      stop();
    }
  }
}
