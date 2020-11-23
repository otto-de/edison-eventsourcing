package de.otto.synapse.endpoint.receiver.kafka;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import de.otto.synapse.channel.ChannelPosition;
import de.otto.synapse.channel.ChannelResponse;
import de.otto.synapse.consumer.MessageDispatcher;
import de.otto.synapse.endpoint.MessageInterceptor;
import de.otto.synapse.endpoint.MessageInterceptorRegistry;
import de.otto.synapse.message.Key;
import de.otto.synapse.message.TextMessage;
import de.otto.synapse.testsupport.TestClock;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.record.TimestampType;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.stream.Collectors;

import static de.otto.synapse.channel.ChannelPosition.channelPosition;
import static de.otto.synapse.channel.ChannelPosition.fromHorizon;
import static de.otto.synapse.channel.ShardPosition.fromHorizon;
import static de.otto.synapse.channel.ShardPosition.fromPosition;
import static de.otto.synapse.channel.ShardPosition.fromPositionAndTimestamp;
import static de.otto.synapse.endpoint.MessageInterceptorRegistration.allChannelsWith;
import static de.otto.synapse.message.Header.of;
import static de.otto.synapse.message.TextMessage.of;
import static java.time.Instant.now;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.apache.kafka.common.record.TimestampType.LOG_APPEND_TIME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.mockito.Mockito.*;

public class KafkaRecordsConsumerTest {

    private final Consumer<String, String> kafkaConsumer = mock(Consumer.class);
    private MessageInterceptorRegistry registry = mock(MessageInterceptorRegistry.class);
    private MessageInterceptor interceptor = (m) -> m;
    private MessageDispatcher dispatcher = mock(MessageDispatcher.class);
    private ChannelDurationBehindHandler durationBehindHandler;

    @Before
    public void setup() {
        registry = new MessageInterceptorRegistry();
        registry.register(allChannelsWith(interceptor));
        dispatcher = mock(MessageDispatcher.class);
        when(kafkaConsumer.endOffsets(any())).thenAnswer(input -> ((Collection<TopicPartition>) input.getArgument(0)).stream().collect(Collectors.toMap(partition -> partition, partition -> 42L)));
        durationBehindHandler = new ChannelDurationBehindHandler("", ChannelPosition.fromHorizon(), mock(ApplicationEventPublisher.class), kafkaConsumer);
    }

    @Test
    public void shouldConsumeRecords() {
        // given
        final KafkaRecordsConsumer consumer = someKafkaRecordsConsumer(fromHorizon());

        Clock fixedClock = TestClock.now();
        final ConsumerRecord<String, String> record = someRecord(0, 42L, fixedClock);

        // when
        final ConsumerRecords<String,String> records = new ConsumerRecords<>(ImmutableMap.of(
                new TopicPartition("foo", 0),
                singletonList(record))
        );
        final ChannelResponse channelResponse = consumer.apply(records);

        // then
        final ChannelPosition expectedChannelPosition = channelPosition(
                fromPositionAndTimestamp("0", "42", fixedClock.instant().truncatedTo(ChronoUnit.MILLIS)),
                fromHorizon("1")
        );
        assertThat(channelResponse.getChannelPosition(), is(expectedChannelPosition));
    }

    @Test
    public void shouldConsumeRecordsFromMultiplePartitions() {
        // given
        final KafkaRecordsConsumer consumer = someKafkaRecordsConsumer(fromHorizon());

        Clock fixedClock = TestClock.now();
        final ConsumerRecord<String, String> recordOne = someRecord(0, 42, fixedClock);
        final ConsumerRecord<String, String> recordTwo = someRecord(1, 4711, fixedClock);

        // when
        final ConsumerRecords<String,String> records = new ConsumerRecords<>(ImmutableMap.of(
                new TopicPartition("foo", 0), singletonList(recordOne),
                new TopicPartition("foo", 1), singletonList(recordTwo)
        ));

        final ChannelResponse channelResponse = consumer.apply(records);

        // then
        final ChannelPosition expectedChannelPosition = channelPosition(
                fromPositionAndTimestamp("0", "42", fixedClock.instant().truncatedTo(ChronoUnit.MILLIS)),
                fromPositionAndTimestamp("1", "4711", fixedClock.instant().truncatedTo(ChronoUnit.MILLIS))
        );
        assertThat(channelResponse.getChannelPosition(), is(expectedChannelPosition));
    }

    @Test
    public void shouldUpdateShardPositionFromLastRecord() {
        // given
        final KafkaRecordsConsumer consumer = someKafkaRecordsConsumer(fromHorizon());

        Clock fixedClock = TestClock.now();
        final ConsumerRecord<String, String> recordOne = someRecord(0, 42, fixedClock);
        final ConsumerRecord<String, String> recordTwo = someRecord(0, 43, fixedClock);

        // when
        final ConsumerRecords<String,String> records = new ConsumerRecords<>(ImmutableMap.of(
                new TopicPartition("foo", 0), asList(recordOne, recordTwo)
        ));

        final ChannelResponse channelResponse = consumer.apply(records);

        // then
        final ChannelPosition expectedChannelPosition = channelPosition(
                fromPositionAndTimestamp("0", "43", fixedClock.instant().truncatedTo(ChronoUnit.MILLIS)),
                fromHorizon("1")
        );
        assertThat(channelResponse.getChannelPosition(), is(expectedChannelPosition));
    }

    @Test
    public void shouldUpdateShardPositionFromPreviousCall() {
        // given
        final KafkaRecordsConsumer consumer = someKafkaRecordsConsumer(fromHorizon());

        Clock fixedClock = TestClock.now();
        final ConsumerRecord<String, String> recordOne = someRecord(0, 42, fixedClock);
        final ConsumerRecords<String,String> firstRecords = new ConsumerRecords<>(ImmutableMap.of(
                new TopicPartition("foo", 0), singletonList(recordOne)
        ));
        consumer.apply(firstRecords);

        // when
        final ConsumerRecord<String, String> recordTwo = someRecord(0, 43, fixedClock);
        final ConsumerRecords<String,String> followingRecords = new ConsumerRecords<>(ImmutableMap.of(
                new TopicPartition("foo", 0), singletonList(recordTwo)
        ));

        final ChannelResponse channelResponse = consumer.apply(followingRecords);

        // then
        final ChannelPosition expectedChannelPosition = channelPosition(
                fromPositionAndTimestamp("0", "43", fixedClock.instant().truncatedTo(ChronoUnit.MILLIS)),
                fromHorizon("1")
        );
        assertThat(channelResponse.getChannelPosition(), is(expectedChannelPosition));
    }

    @Test
    public void shouldInterceptMessage() {
        // given
        final KafkaRecordsConsumer consumer = someKafkaRecordsConsumer(fromHorizon());

        final ConsumerRecord<String, String> record = someRecord(0, 42L, Clock.systemDefaultZone());

        // when
        registry.register(allChannelsWith((m) -> {
            return TextMessage.of(m.getKey(), m.getHeader(), "intercepted");
        }));

        final ConsumerRecords<String,String> records = new ConsumerRecords<>(ImmutableMap.of(
                new TopicPartition("foo", 0),
                singletonList(record))
        );
        consumer.apply(records);

        // then
        verify(dispatcher).accept(of(Key.of("key"), of(fromPosition("0", "42")), "intercepted"));
    }

    @Test
    public void shouldDispatchMessage() {
        // given
        final KafkaRecordsConsumer consumer = someKafkaRecordsConsumer(fromHorizon());

        final ConsumerRecord<String, String> record = someRecord(0, 42L, Clock.systemDefaultZone());

        // when
        final ConsumerRecords<String,String> records = new ConsumerRecords<>(ImmutableMap.of(
                new TopicPartition("foo", 0),
                singletonList(record))
        );
        consumer.apply(records);

        // then
        verify(dispatcher).accept(of(Key.of("key"), of(fromPosition("0", "42")), "payload"));
    }

    @Test
    public void shouldNotDispatchMessageDroppedByInterceptor() {
        // given
        final KafkaRecordsConsumer consumer = someKafkaRecordsConsumer(fromHorizon());

        final ConsumerRecord<String, String> record = someRecord(0, 42L, Clock.systemDefaultZone());

        // when
        registry.register(allChannelsWith((m) -> null));
        final ConsumerRecords<String,String> records = new ConsumerRecords<>(ImmutableMap.of(
                new TopicPartition("foo", 0),
                singletonList(record))
        );
        consumer.apply(records);

        // then
        verifyNoInteractions(dispatcher);
    }

    @Test
    public void shouldUpdateDurationBehindHandler() {
        // given
        final KafkaRecordsConsumer consumer = someKafkaRecordsConsumer(fromHorizon());

        final ConsumerRecord<String, String> record = someRecord(0, 42L, Clock.systemDefaultZone());

        // when
        final ConsumerRecords<String,String> records = new ConsumerRecords<>(ImmutableMap.of(
                new TopicPartition("foo", 0),
                singletonList(record))
        );
        consumer.apply(records);

        // then
        final long secondsBehind = getSecondsBehind("0");
        assertThat(secondsBehind, is(lessThanOrEqualTo(2L)));
    }

    @Test
    public void shouldNotChangeDurationBehindOnNoRecords() {
        // given
        final KafkaRecordsConsumer consumer = someKafkaRecordsConsumer(fromHorizon());
        durationBehindHandler.onPartitionsAssigned(asList(new TopicPartition("", 0), new TopicPartition("", 1)));
        ConsumerRecord<String,String> consumerRecord = new ConsumerRecord<>("", 0, 23, now().minusSeconds(100).toEpochMilli(), TimestampType.CREATE_TIME, 0, 0, 0, "key", "value");

        consumer.apply(new ConsumerRecords<>(ImmutableMap.of(new TopicPartition("", 0), singletonList(consumerRecord))));
        assertThat(getSecondsBehind("0"), is(100L));
        assertThat(getSecondsBehind("1"), is(9223372036854775L));

        // when
        consumer.apply(ConsumerRecords.empty());

        // then
        assertThat(getSecondsBehind("0"), is(100L));
        assertThat(getSecondsBehind("1"), is(9223372036854775L));
    }

    private long getSecondsBehind(String shard) {
        return durationBehindHandler
                .getChannelDurationBehind()
                .getShardDurationsBehind()
                .get(shard)
                .getSeconds();
    }

    private ConsumerRecord<String, String> someRecord(final int partition, final long offset, Clock clock) {
        return new ConsumerRecord<>(
                "foo",
                partition,
                offset,
                clock.instant().toEpochMilli(), LOG_APPEND_TIME,
                -1L, -1, -1,
                "key",
                "payload"
        );
    }

    private KafkaRecordsConsumer someKafkaRecordsConsumer(final ChannelPosition startFrom) {
        final KafkaDecoder decoder = new KafkaDecoder();
        return new KafkaRecordsConsumer("foo", startFrom, registry, dispatcher, durationBehindHandler, () -> ImmutableSet.of("0", "1"), decoder);
    }
}
