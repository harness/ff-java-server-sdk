package io.harness.cf.client.connector;

import io.harness.cf.client.dto.Message;
import java.io.IOException;
import java.nio.file.*;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileWatcher implements Runnable {

  private final WatchService watchService;
  private final Updater updater;
  private final String domain;

  private boolean running = false;

  public FileWatcher(@NonNull String domain, @NonNull Path path, @NonNull Updater updater)
      throws IOException {
    this.domain = domain;
    this.updater = updater;
    watchService = FileSystems.getDefault().newWatchService();

    path.register(
        watchService,
        StandardWatchEventKinds.ENTRY_CREATE,
        StandardWatchEventKinds.ENTRY_DELETE,
        StandardWatchEventKinds.ENTRY_MODIFY);

    running = true;
  }

  @SneakyThrows
  @Override
  public void run() {
    WatchKey key;
    while (running && (key = watchService.take()) != null) {
      for (WatchEvent<?> event : key.pollEvents()) {
        log.info("Event kind:" + event.kind().name() + ". File affected: " + event.context() + ".");
        String strEvent = "";
        switch (event.kind().name()) {
          case "ENTRY_CREATE":
            strEvent = "create";
            break;
          case "ENTRY_MODIFY":
            strEvent = "patch";
            break;
          case "ENTRY_DELETE":
            strEvent = "delete";
            break;
        }
        updater.update(
            new Message(
                strEvent, domain, removeFileExtension(event.context().toString(), false), 0));
      }
      key.reset();
    }
  }

  public void close() {
    running = false;
    updater.onDisconnected();
  }

  public static String removeFileExtension(String filename, boolean removeAllExtensions) {
    if (filename == null || filename.isEmpty()) {
      return filename;
    }

    String extPattern = "(?<!^)[.]" + (removeAllExtensions ? ".*" : "[^.]*$");
    return filename.replaceAll(extPattern, "");
  }
}
