package io.harness.cf.client.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.cf.client.connector.Connector;
import io.harness.cf.client.connector.ConnectorException;
import io.harness.cf.client.connector.Updater;
import io.harness.cf.client.dto.Message;
import org.junit.jupiter.api.Test;

public class UpdateProcessorTest {

  @Test
  public void shouldNotCallOutToServerOnDeleteTargetSegmentEvent() throws ConnectorException {
    final Connector mockConnector = mock(Connector.class);
    final Repository mockRepo = mock(Repository.class);
    final Updater mockUpdater = mock(Updater.class);

    final UpdateProcessor processor = new UpdateProcessor(mockConnector, mockRepo, mockUpdater);
    final Message message = new Message("delete", "target-segment", "test", 1);
    processor.processSegment(message).run();

    verify(mockConnector, times(0)).getSegment(anyString());
    verify(mockRepo, times(1)).deleteSegment(anyString());
  }

  @Test
  public void shouldNotCallOutToServerOnDeleteFlagEvent() throws ConnectorException {
    final Connector mockConnector = mock(Connector.class);
    final Repository mockRepo = mock(Repository.class);
    final Updater mockUpdater = mock(Updater.class);

    final UpdateProcessor processor = new UpdateProcessor(mockConnector, mockRepo, mockUpdater);
    final Message message = new Message("delete", "flag", "test", 1);
    processor.processFlag(message).run();

    verify(mockConnector, times(0)).getFlag(anyString());
    verify(mockRepo, times(1)).deleteFlag(anyString());
  }
}
