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
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.NonNull;

public class LocalConnector implements Connector {
  private final String source;
  private final Gson gson = new Gson();
  private FileWatcher flagsWatcher;
  private FileWatcher segmentsWatcher;

  private final ExecutorService pool = Executors.newFixedThreadPool(2);

  public LocalConnector(@NonNull String source) {
    this.source = source;
  }

  @Override
  public Optional<String> authenticate(Consumer<String> onError) {
    return Optional.of("success");
  }

  @Override
  public List<FeatureConfig> getFlags() {
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
              e.printStackTrace();
            }
          });
    } catch (IOException e) {
      // Error while reading the directory
      e.printStackTrace();
    }
    return configs;
  }

  @Override
  public Optional<FeatureConfig> getFlag(@NonNull String identifier) {

    try {
      Path path = Paths.get(source, "flags", identifier + ".json");
      String content = new String(Files.readAllBytes(path));
      FeatureConfig config = gson.fromJson(content, FeatureConfig.class);
      return Optional.ofNullable(config);
    } catch (IOException e) {
      e.printStackTrace();
      return Optional.empty();
    }
  }

  @Override
  public List<Segment> getSegments() {
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
              e.printStackTrace();
            }
          });
    } catch (IOException e) {
      // Error while reading the directory
      e.printStackTrace();
    }
    return segments;
  }

  @Override
  public Optional<Segment> getSegment(@NonNull String identifier) {

    try {
      Path path = Paths.get(source, "segments", identifier + ".json");
      String content = new String(Files.readAllBytes(path));
      Segment segment = gson.fromJson(content, Segment.class);
      return Optional.ofNullable(segment);
    } catch (IOException e) {
      e.printStackTrace();
      return Optional.empty();
    }
  }

  @Override
  public void postMetrics(Metrics metrics) {
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
      e.printStackTrace();
    }
  }

  @Override
  public void stream(Updater updater) {
    try {
      flagsWatcher = new FileWatcher("flag", Paths.get(source, "flags"), updater);
      segmentsWatcher = new FileWatcher("target-segment", Paths.get(source, "segments"), updater);
      updater.onReady();
      pool.submit(flagsWatcher);
      pool.submit(segmentsWatcher);
      updater.onConnected();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void close() {
    flagsWatcher.close();
    segmentsWatcher.close();
    pool.shutdown();
  }
}
