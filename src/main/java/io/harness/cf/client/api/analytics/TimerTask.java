package io.harness.cf.client.api.analytics;

import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.RingBuffer;
import io.harness.cf.client.dto.Analytics;
import io.harness.cf.client.dto.EventType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TimerTask implements Runnable {

  private RingBuffer<Analytics> ringBuffer;

  public TimerTask(RingBuffer<Analytics> ringBuffer) {
    this.ringBuffer = ringBuffer;
  }

  @Override
  public void run() {
    long sequence = -1;
    try {
      sequence = ringBuffer.tryNext(); // Grab the next sequence if we can
      log.debug("Publishing timerInfo to ringBuffer");
      Analytics event = ringBuffer.get(sequence); // Get the entry in the Disruptor for the sequence
      event.setEventType(EventType.TIMER);
    } catch (InsufficientCapacityException e) {
      log.debug("Insufficient capacity in the analytics ringBuffer");
    } finally {
      if (sequence != -1) {
        ringBuffer.publish(sequence);
      }
    }
  }
}
