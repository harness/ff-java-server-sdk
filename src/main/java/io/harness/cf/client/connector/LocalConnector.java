package io.harness.cf.client.connector;

import com.google.gson.Gson;
import io.harness.cf.client.logger.LogUtil;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

final class Pair<L, R> {
  final L left;
  final R right;

  Pair(L left, R right) {
    this.left = left;
    this.right = right;
  }

  static <L, R> Pair<L, R> of(L left, R right) {
    return new Pair<>(left, right);
  }
}

@Slf4j
public class LocalConnector implements Connector, AutoCloseable {
  private static final String JSON_EXTENSION = ".json";
  private static final String FLAGS = "flags";
  private static final String SEGMENTS = "segments";
  private final String source;
  private final Gson gson = new Gson();
  private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

  static {
    LogUtil.setSystemProps();
  }

  public LocalConnector(@NonNull final String source) {
    this.source = source;
    Stream.of("flags", "segments", "metrics")
        .forEach(nextDir -> Paths.get(source, nextDir).toFile().mkdirs());
    log.info("LocalConnector initialized with source {}", source);
  }

  @Override
  public String authenticate() {
    log.info("authenticate");
    return "success";
  }

  @Override
  public void setOnUnauthorized(Runnable runnable) {
    // no need for this in local connector because there is no
    // authentication check
    log.info("authenticate");
  }

  protected Stream<File> listFiles(@NonNull final String source, @NonNull final String domain)
      throws ConnectorException {
    log.debug("List files in {} with {}", source, domain);
    try {
      Paths.get(source, domain).toFile().mkdirs();
      Stream<File> fileStream =
          Files.list(Paths.get(source, domain))
              .filter(Files::isRegularFile)
              .filter(path -> path.toString().endsWith(JSON_EXTENSION))
              .map(Path::toFile);
      log.debug("List files successfully executed");
      return fileStream;
    } catch (IOException e) {
      log.error(
          "Exception was raised while listing the files in {} and domain {}", source, domain, e);
      throw new ConnectorException(e.getMessage());
    }
  }

  <T> Pair<T, Exception> loadFile(@NonNull final File file, @NonNull final Class<T> classOfT) {
    log.debug("Loading file {}", file);
    try {
      final String content = new String(Files.readAllBytes(file.toPath()));
      log.debug("File was successfully loaded {}", file);
      return Pair.of(gson.fromJson(content, classOfT), null);
    } catch (Exception e) {
      log.error("Exception was raised while loading file {}", file, e);
      return Pair.of(null, e);
    }
  }

  protected <T> T load(@NonNull final File file, @NonNull final Class<T> classOfT) {
    final Pair<T, Exception> pair = loadFile(file, classOfT);
    if (pair.right != null) {
      log.error(
          "Exception was raised while loading file {} with error {}",
          file.getName(),
          pair.right.getMessage());
      return null;
    }
    return pair.left;
  }

  @Override
  public List<FeatureConfig> getFlags() throws ConnectorException {

    List<FeatureConfig> configs =
        listFiles(source, FLAGS)
            .map(file -> load(file, FeatureConfig.class))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    log.info("Flags successfully loaded from {}/{}", source, FLAGS);
    return configs;
  }

  @Override
  public FeatureConfig getFlag(@NonNull final String identifier) throws ConnectorException {
    final Path path = Paths.get(source, FLAGS, identifier + JSON_EXTENSION);
    log.debug("Load flag {} from path {}/{}", identifier, source, FLAGS);
    final Pair<FeatureConfig, Exception> pair = loadFile(path.toFile(), FeatureConfig.class);
    if (pair.right != null) {
      throw new ConnectorException(pair.right.getMessage());
    }
    log.debug("Flag {} successfully loaded from path {}/{}", identifier, source, FLAGS);
    return pair.left;
  }

  @Override
  public List<Segment> getSegments() throws ConnectorException {
    log.debug("Loading target groups from path {}/{}", source, SEGMENTS);

    List<Segment> list =
        listFiles(source, SEGMENTS)
            .map(file -> load(file, Segment.class))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    log.debug("Target groups successfully loaded from {}/{}", source, SEGMENTS);
    return list;
  }

  @Override
  public Segment getSegment(@NonNull final String identifier) throws ConnectorException {
    final Path path = Paths.get(source, SEGMENTS, identifier + JSON_EXTENSION);
    log.debug("Load target group {} from path {}/{}", identifier, source, SEGMENTS);
    final Pair<Segment, Exception> pair = loadFile(path.toFile(), Segment.class);
    if (pair.right != null) {
      throw new ConnectorException(pair.right.getMessage());
    }
    log.debug("Target group {} successfully loaded from path {}/{}", identifier, source, SEGMENTS);
    return pair.left;
  }

  @Override
  public void postMetrics(@NonNull final Metrics metrics) throws ConnectorException {
    final SimpleDateFormat df = new SimpleDateFormat("yyyy_MM_dd");
    final String filename = String.format("%s.jsonl", df.format(new Date()));
    final String content = gson.toJson(metrics) + '\n';
    log.debug("Storing metrics data");
    try {
      Files.write(
          Paths.get(source, "metrics", filename),
          content.getBytes(),
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
      log.debug("Metrics stored successfully");
    } catch (IOException e) {
      log.error("Exception was raised while storing metrics", e);
      throw new ConnectorException(e.getMessage());
    }
  }

  @Override
  public Service stream(@NonNull final Updater updater) throws ConnectorException {
    log.debug("Initializing stream");
    try {
      FileWatcherService fileWatcherService = new FileWatcherService(updater);
      log.debug("Stream successfully initialized");
      return fileWatcherService;
    } catch (IOException e) {
      log.error("Error initializing stream", e);
      throw new ConnectorException(e.getMessage());
    }
  }

  @Override
  public void close() {
    log.debug("LocalConnector closed");
  }

  @Override
  public void setIsShuttingDown() {
    isShuttingDown.set(true);
  }

  private class FileWatcherService implements Service, AutoCloseable {
    private final FileWatcher flagWatcher;
    private final FileWatcher segmentWatcher;

    private final Updater updater;

    private FileWatcherService(@NonNull final Updater updater) throws IOException {
      this.updater = updater;
      flagWatcher = new FileWatcher("flag", Paths.get(source, FLAGS), updater);
      segmentWatcher = new FileWatcher("target-segment", Paths.get(source, SEGMENTS), updater);

      this.updater.onReady();
      log.info("FileWatcherService initialized");
    }

    @Override
    public void start() {
      log.info("FileWatcherService starting");
      flagWatcher.start();
      segmentWatcher.start();
      updater.onConnected();
      log.info("FileWatcherService started");
    }

    @Override
    public void stop() throws InterruptedException {
      log.info("FileWatcherService stopping");
      flagWatcher.stop();
      segmentWatcher.stop();
      updater.onDisconnected("LocalConnector stopped");
      log.info("FileWatcherService stopped");
    }

    @Override
    public void close() throws InterruptedException {
      stop();
      log.info("FileWatcherService closed");
    }
  }
}
