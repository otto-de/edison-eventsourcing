package de.otto.synapse.channel.aws;

import com.google.common.annotations.VisibleForTesting;
import de.otto.synapse.channel.ChannelPosition;
import de.otto.synapse.channel.ChannelResponse;
import de.otto.synapse.consumer.MessageConsumer;
import de.otto.synapse.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamRequest;
import software.amazon.awssdk.services.kinesis.model.DescribeStreamResponse;
import software.amazon.awssdk.services.kinesis.model.Shard;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.stream.Collectors.toList;

//TODO: KinesisMessageLogReaderEndpoint?

public class KinesisMessageLog implements MessageLog {

    private static final Logger LOG = LoggerFactory.getLogger(KinesisMessageLog.class);


    private final String streamName;
    private final KinesisClient kinesisClient;
    private final ExecutorService executorService;

    private List<KinesisShard> kinesisShards;

    public KinesisMessageLog(final KinesisClient kinesisClient,
                             final String streamName) {
        this(kinesisClient, streamName, newFixedThreadPool(1));
    }

    public KinesisMessageLog(final KinesisClient kinesisClient,
                             final String streamName,
                             final ExecutorService executorService) {
        this.streamName = streamName;
        this.kinesisClient = kinesisClient;
        this.executorService = executorService;
    }

    @Override
    public String getStreamName() {
        return streamName;
    }

    @Override
    public ChannelResponse consumeStream(final ChannelPosition startFrom,
                                         final Predicate<Message<?>> stopCondition,
                                         final MessageConsumer<String> consumer) {
        final List<KinesisShard> kinesisShards = retrieveAllOpenShards();
        try {
            final List<CompletableFuture<ChannelResponse>> futureShardPositions = kinesisShards
                    .stream()
                    .map(shard -> supplyAsync(
                            () -> shard.consumeShard(startFrom, stopCondition, consumer),
                            executorService))
                    .collect(toList());

            // don't chain futureShardPositions with CompletableFuture::join as lazy execution will prevent threads from
            // running in parallel

            return ChannelResponse.of(
                    futureShardPositions
                            .stream()
                            .map(CompletableFuture::join)
                            .collect(toList())
            );
        } catch (final RuntimeException e) {
            LOG.error("Failed to consume from Kinesis stream {}: {}", streamName, e.getMessage());
            // When an exception occurs in a completable future's thread, other threads continue running.
            // Stop all before proceeding.
            executorService.shutdownNow();
            try {
                boolean allThreadsSafelyTerminated = executorService.awaitTermination(30, TimeUnit.SECONDS);
                if (!allThreadsSafelyTerminated) {
                    LOG.error("Kinesis Thread for stream {} is still running", streamName);
                }

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            throw e;
        }
    }

    @VisibleForTesting
    List<KinesisShard> retrieveAllOpenShards() {
        if (kinesisShards == null) {
            kinesisShards = retrieveAllShards().stream()
                    .filter(this::isShardOpen)
                    .map(shard -> new KinesisShard(shard.shardId(), streamName, kinesisClient))
                    .collect(toImmutableList());
        }
        return kinesisShards;
    }

    private List<Shard> retrieveAllShards() {
        List<Shard> shardList = new ArrayList<>();

        boolean fetchMore = true;
        while (fetchMore) {
            fetchMore = retrieveAndAppendNextBatchOfShards(shardList);
        }
        return shardList;
    }

    private boolean retrieveAndAppendNextBatchOfShards(List<Shard> shardList) {
        DescribeStreamRequest describeStreamRequest = DescribeStreamRequest
                .builder()
                .streamName(streamName)
                .exclusiveStartShardId(getLastSeenShardId(shardList))
                .limit(10)
                .build();

        DescribeStreamResponse describeStreamResult = kinesisClient.describeStream(describeStreamRequest);
        shardList.addAll(describeStreamResult.streamDescription().shards());

        return describeStreamResult.streamDescription().hasMoreShards();
    }

    private String getLastSeenShardId(List<Shard> shardList) {
        if (!shardList.isEmpty()) {
            return shardList.get(shardList.size() - 1).shardId();
        } else {
            return null;
        }
    }

    private boolean isShardOpen(Shard shard) {
        if (shard.sequenceNumberRange().endingSequenceNumber() == null) {
            return true;
        } else {
            LOG.warn("Shard with id {} is closed. Cannot retrieve data.", shard.shardId());
            return false;
        }
    }

}
