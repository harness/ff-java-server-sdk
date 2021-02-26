package io.harness.cf.client.api.analytics;

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
    long sequence = ringBuffer.next(); // Grab the next sequence
    try {
      log.info("Publishing timerInfo to ringBuffer");
      Analytics event = ringBuffer.get(sequence); // Get the entry in the Disruptor for the sequence
      event.setEventType(EventType.TIMER);
    } finally {
      ringBuffer.publish(sequence);
    }
  }
}
