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

  @Override
  public List<FeatureConfig> getFlags() throws ConnectorException {
    List<FeatureConfig> configs = new ArrayList<>();
    try {
      List<File> files =
          Files.list(Paths.get(source, "flags"))
              .filter(Files::isRegularFile)
              .filter(path -> path.toString().endsWith(".json"))
              .map(Path::toFile)
              .collect(Collectors.toList());

      files.forEach(
          file -> {
            try {
              String content = new String(Files.readAllBytes(file.toPath()));
              FeatureConfig featureConfig = gson.fromJson(content, FeatureConfig.class);
              if (featureConfig != null) {
                configs.add(featureConfig);
              }
            } catch (Exception e) {
              log.error(
                  "Exception was raised while loading flag file {} with error {}",
                  file.getName(),
                  e.getMessage());
            }
          });
    } catch (IOException e) {
      throw new ConnectorException(e.getMessage());
    }
    return configs;
  }

  @Override
  public FeatureConfig getFlag(@NonNull String identifier) throws ConnectorException {

    try {
      Path path = Paths.get(source, "flags", identifier + ".json");
      String content = new String(Files.readAllBytes(path));
      return gson.fromJson(content, FeatureConfig.class);
    } catch (IOException e) {
      throw new ConnectorException(e.getMessage());
    }
  }

  @Override
  public List<Segment> getSegments() throws ConnectorException {
    List<Segment> segments = new ArrayList<>();
    try {
      List<File> files =
          Files.list(Paths.get(source, "segments"))
              .filter(Files::isRegularFile)
              .filter(path -> path.toString().endsWith(".json"))
              .map(Path::toFile)
              .collect(Collectors.toList());

      files.forEach(
          file -> {
            try {
              String content = new String(Files.readAllBytes(file.toPath()));
              Segment segment = gson.fromJson(content, Segment.class);
              if (segment != null) {
                segments.add(segment);
              }
            } catch (IOException e) {
              log.error(
                  "Exception was raised while loading segment file {} with error {}",
                  file.getName(),
                  e.getMessage());
            }
          });
    } catch (IOException e) {
      throw new ConnectorException(e.getMessage());
    }
    return segments;
  }

  @Override
  public Segment getSegment(@NonNull String identifier) throws ConnectorException {

    try {
      Path path = Paths.get(source, "segments", identifier + ".json");
      String content = new String(Files.readAllBytes(path));
      return gson.fromJson(content, Segment.class);
    } catch (IOException e) {
      throw new ConnectorException(e.getMessage());
    }
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
