package de.otto.synapse.endpoint.receiver.kinesis;

import de.otto.synapse.channel.ShardPosition;
import de.otto.synapse.channel.ShardResponse;
import de.otto.synapse.testsupport.TestClock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.kinesis.model.*;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static de.otto.synapse.channel.ShardPosition.fromHorizon;
import static de.otto.synapse.channel.ShardPosition.fromPosition;
import static de.otto.synapse.channel.StartFrom.HORIZON;
import static java.time.Duration.ofMillis;
import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.Collections.emptyList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class KinesisShardReaderTest {

    @Mock
    private KinesisAsyncClient kinesisClient;

    @Mock
    private Consumer<ShardResponse> consumer;

    private Clock clock = TestClock.now();
    private KinesisShardReader kinesisShardReader;
    private ExecutorService executorService;

    @Before
    public void setUp() {
        executorService = newSingleThreadExecutor();
        kinesisShardReader = new KinesisShardReader("someChannel", "someShard", kinesisClient, executorService, clock);

        GetShardIteratorResponse fakeResponse = GetShardIteratorResponse.builder()
                .shardIterator("someShardIterator")
                .build();

        when(kinesisClient.getShardIterator(any(GetShardIteratorRequest.class))).thenReturn(completedFuture(fakeResponse));
    }


    @Test
    @SuppressWarnings("unchecked")
    public void shouldConsumeSingleRecordSet() throws ExecutionException, InterruptedException {
        final Instant now = now();
        final Instant future = now.plus(1, SECONDS);
        // given
        final Record record1 = Record.builder()
                .sequenceNumber("1")
                .approximateArrivalTimestamp(future)
                .partitionKey("first")
                .build();
        final Record record2 = Record.builder()
                .sequenceNumber("2")
                .approximateArrivalTimestamp(future)
                .partitionKey("second")
                .build();
        final GetRecordsResponse response = GetRecordsResponse.builder()
                .records(record1, record2)
                .nextShardIterator("nextShardIterator")
                .millisBehindLatest(1234L)
                .build();
        when(kinesisClient.getRecords(any(GetRecordsRequest.class))).thenReturn(completedFuture(response));

        // when
        kinesisShardReader.stop();
        final ShardPosition shardPosition = kinesisShardReader.consumeUntil(fromHorizon("someShard"), now, consumer).get();

        // then
        final ArgumentCaptor<ShardResponse> argumentCaptor = ArgumentCaptor.forClass(ShardResponse.class);
        verify(consumer).accept(argumentCaptor.capture());
        verifyNoMoreInteractions(consumer);

        final ShardResponse shardResponse = argumentCaptor.getValue();
        assertThat(shardResponse.getChannelName(), is("someChannel"));
        assertThat(shardResponse.getShardName(), is("someShard"));
        assertThat(shardResponse.getShardPosition(), is(fromPosition("someShard", "2")));
        assertThat(shardResponse.getDurationBehind(), is(ofMillis(1234L)));
        assertThat(shardResponse.getMessages(), hasSize(2));
        assertThat(shardPosition.position(), is("2"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldcatchUp() throws ExecutionException, InterruptedException {
        final Instant now = now();
        final Instant future = now.plus(1, SECONDS);
        // given
        final Record record1 = Record.builder()
                .sequenceNumber("1")
                .approximateArrivalTimestamp(future)
                .partitionKey("first")
                .build();
        final GetRecordsResponse response1 = GetRecordsResponse.builder()
                .records(record1)
                .nextShardIterator("nextShardIterator")
                .millisBehindLatest(1234L)
                .build();

        final Record record2 = Record.builder()
                .sequenceNumber("2")
                .approximateArrivalTimestamp(future)
                .partitionKey("second")
                .build();
        final GetRecordsResponse response2= GetRecordsResponse.builder()
                .records(record2)
                .nextShardIterator("nextShardIterator")
                .millisBehindLatest(0L)
                .build();
        when(kinesisClient.getRecords(any(GetRecordsRequest.class))).thenReturn(
                completedFuture(response1),
                completedFuture(response2));

        // when
        final ShardPosition shardPosition = kinesisShardReader.catchUp(fromHorizon("someShard"), consumer).get();

        // then
        final ArgumentCaptor<ShardResponse> argumentCaptor = ArgumentCaptor.forClass(ShardResponse.class);
        verify(consumer, times(2)).accept(argumentCaptor.capture());
        verifyNoMoreInteractions(consumer);

        List<ShardResponse> allValues = argumentCaptor.getAllValues();
        assertThat(allValues, hasSize(2));
        final ShardResponse lastResponse = allValues.get(1);
        assertThat(lastResponse.getChannelName(), is("someChannel"));
        assertThat(lastResponse.getShardName(), is("someShard"));
        assertThat(lastResponse.getShardPosition(), is(fromPosition("someShard", "2")));
        assertThat(lastResponse.getDurationBehind(), is(ofMillis(0L)));
        assertThat(lastResponse.getMessages(), hasSize(1));
        assertThat(shardPosition.position(), is("2"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldConsumeEmptyRecordSet() throws ExecutionException, InterruptedException {
        final Instant now = now();
        final Instant future = now.plus(1, SECONDS);
        // given
        final GetRecordsResponse response = GetRecordsResponse.builder()
                .records(emptyList())
                .nextShardIterator("nextShardIterator")
                .millisBehindLatest(1234L)
                .build();
        when(kinesisClient.getRecords(any(GetRecordsRequest.class))).thenReturn(completedFuture(response));

        // when
        ArgumentCaptor<ShardResponse> argumentCaptor = ArgumentCaptor.forClass(ShardResponse.class);
        final ShardPosition shardPosition = kinesisShardReader.consumeUntil(fromHorizon("someShard"), now.minus(1, SECONDS), consumer).get();

        // then
        verify(consumer).accept(argumentCaptor.capture());
        verifyNoMoreInteractions(consumer);

        assertThat(shardPosition.startFrom(), is(HORIZON));
        final ShardResponse shardResponse = argumentCaptor.getValue();
        assertThat(shardResponse.getChannelName(), is("someChannel"));
        assertThat(shardResponse.getShardName(), is("someShard"));
        assertThat(shardResponse.getShardPosition(), is(fromHorizon("someShard")));
        assertThat(shardResponse.getDurationBehind(), is(ofMillis(1234L)));
        assertThat(shardResponse.getMessages(), hasSize(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldConsumeSingleRecordSetForStoppingThread() throws ExecutionException, InterruptedException, TimeoutException {
        final Instant now = now();
        final Instant future = now.plus(1, SECONDS);

        // given
        final Record record1 = Record.builder()
                .sequenceNumber("1")
                .approximateArrivalTimestamp(future)
                .partitionKey("first")
                .build();
        final Record record2 = Record.builder()
                .sequenceNumber("2")
                .approximateArrivalTimestamp(future)
                .partitionKey("second")
                .build();
        final GetRecordsResponse response = GetRecordsResponse.builder()
                .records(record1, record2)
                .nextShardIterator("nextShardIterator")
                .millisBehindLatest(1234L)
                .build();
        when(kinesisClient.getRecords(any(GetRecordsRequest.class))).thenReturn(completedFuture(response));

        // when
        ArgumentCaptor<ShardResponse> argumentCaptor = ArgumentCaptor.forClass(ShardResponse.class);
        final CompletableFuture<ShardPosition> shardPosition = kinesisShardReader.consumeUntil(fromHorizon("someShard"), now, consumer);
        kinesisShardReader.stop();
        shardPosition.get();

        // then
        verify(consumer).accept(argumentCaptor.capture());
        verifyNoMoreInteractions(consumer);
        assertThat(kinesisShardReader.isStopping(), is(true));
        assertThat(shardPosition.get(), is(fromPosition("someShard", "2")));
        final ShardResponse shardResponse = argumentCaptor.getValue();
        assertThat(shardResponse.getChannelName(), is("someChannel"));
        assertThat(shardResponse.getShardName(), is("someShard"));
        assertThat(shardResponse.getShardPosition(), is(fromPosition("someShard", "2")));
        assertThat(shardResponse.getDurationBehind(), is(ofMillis(1234L)));
        assertThat(shardResponse.getMessages(), hasSize(2));
        assertThat(shardResponse.getMessages().get(0).getKey(), is("first"));
        assertThat(shardResponse.getMessages().get(1).getKey(), is("second"));
    }

    @Test
    public void shouldReturnPositionWhenThereAreNoRecords() throws ExecutionException, InterruptedException {
        // given
        GetRecordsResponse response = GetRecordsResponse.builder()
                .records(emptyList())
                .nextShardIterator("nextShardIterator")
                .millisBehindLatest(1234L)
                .build();
        when(kinesisClient.getRecords(any(GetRecordsRequest.class))).thenReturn(completedFuture(response));

        // when
        kinesisShardReader.stop();
        ShardPosition shardPosition = kinesisShardReader.consumeUntil(fromPosition("someShard", "42"), now(), consumer).get();

        // then
        verify(consumer).accept(any(ShardResponse.class));
        assertThat(shardPosition.position(), is("42"));
    }

    @Test(expected = ExecutionException.class)
    public void shouldPropagateException() throws ExecutionException, InterruptedException {
        // given
        when(kinesisClient.getRecords(any(GetRecordsRequest.class))).thenThrow(RuntimeException.class);

        // when
        kinesisShardReader.consumeUntil(fromHorizon("someShard"), Instant.MAX, consumer).get();

        // then
        // exception is thrown
    }
}
