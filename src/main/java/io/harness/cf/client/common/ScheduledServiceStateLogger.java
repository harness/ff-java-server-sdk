package io.harness.cf.client.common;

import com.google.common.util.concurrent.Service;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class ScheduledServiceStateLogger extends Service.Listener {
  private final String name;

  @Override
  public void starting() {
    log.info("{}: ScheduledService starting", name);
  }

  @Override
  public void running() {
    log.info("{}: ScheduledService running", name);
  }

  @Override
  public void stopping(Service.State from) {
    log.info("{}: ScheduledService stopping [from={}]", name, from);
  }

  @Override
  public void terminated(Service.State from) {
    log.info("{}: ScheduledService terminated [from={}]", name, from);
  }

  @Override
  public void failed(Service.State from, Throwable failure) {
    log.warn("{}: ScheduledService failed [from={} message={}]", name, from, failure.getMessage());
    log.warn(name + ": ScheduledService failed exception", failure);
  }
}
