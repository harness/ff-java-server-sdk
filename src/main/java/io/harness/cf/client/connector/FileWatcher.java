package io.harness.cf.client.connector;

import io.harness.cf.client.common.StringUtils;
import io.harness.cf.client.dto.Message;
import java.io.IOException;
import java.nio.file.*;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class FileWatcher implements Runnable, AutoCloseable, Service {

  private final Updater updater;
  private final String domain;
  private final WatchService watcher;
  private Thread thread;

  public FileWatcher(
      @NonNull final String domain, @NonNull final Path path, @NonNull final Updater updater)
      throws IOException {
    this.domain = domain;
    this.updater = updater;

    watcher = FileSystems.getDefault().newWatchService();
    path.register(
        watcher,
        StandardWatchEventKinds.ENTRY_CREATE,
        StandardWatchEventKinds.ENTRY_DELETE,
        StandardWatchEventKinds.ENTRY_MODIFY);
    log.info("FileWatcher initialized with path {}/{}", path, domain);
  }

  @SneakyThrows
  @Override
  public void run() {
    for (; ; ) {
      if (thread.isInterrupted()) break;

      WatchKey key;
      try {
        log.debug("waiting for create event");
        key = watcher.take();
        log.debug("got an event, process it");
      } catch (InterruptedException ie) {
        log.debug("interruped, must be time to shut down...");
        break;
      }

      for (WatchEvent<?> event : key.pollEvents()) {
        final WatchEvent.Kind<?> kind = event.kind();

        if (kind == StandardWatchEventKinds.OVERFLOW) continue;

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
      final boolean valid = key.reset();
      if (!valid) {
        break;
      }
    }
  }

  @Override
  public void close() {
    try {
      stop();
    } catch (InterruptedException e) {
      log.warn("stop failed!");
    }
  }

  public static String removeFileExtension(
      @NonNull final String filename, final boolean removeAllExtensions) {
    if (StringUtils.isNullOrEmpty(filename)) {
      return filename;
    }

    String extPattern = "(?<!^)[.]" + (removeAllExtensions ? ".*" : "[^.]*$");
    return filename.replaceAll(extPattern, "");
  }

  @Override
  public void start() {
    if (thread != null) {
      log.debug("watcher thread is already running");
      return;
    }
    log.debug("starting monitor");
    thread = new Thread(this);
    thread.start();
    log.debug("monitor started");
  }

  @Override
  public void stop() throws InterruptedException {
    if (thread == null) {
      log.debug("watcher thread is null");
      return;
    }
    log.debug("stopping monitor");
    thread.interrupt();
    thread.join();
    thread = null;
    log.debug("monitor stopped");
  }
}
