package de.otto.synapse.message.kinesis;

import de.otto.synapse.message.*;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.model.Record;

import javax.annotation.Nonnull;
import java.util.function.Function;

import static de.otto.synapse.channel.ShardPosition.fromPosition;
import static de.otto.synapse.message.DefaultHeaderAttr.MSG_ARRIVAL_TS;
import static de.otto.synapse.translator.MessageCodec.decode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static software.amazon.awssdk.core.SdkBytes.fromByteArray;

public class KinesisMessage {


    public static final String SYNAPSE_MSG_HEADERS = "_synapse_msg_headers";
    public static final String SYNAPSE_MSG_PAYLOAD = "_synapse_msg_payload";

    private static final SdkBytes EMPTY_SDK_BYTES_BUFFER = fromByteArray(new byte[]{});

    private static final Function<SdkBytes, String> SDK_BYTES_STRING = sdkBytes -> {
        if (sdkBytes == null || sdkBytes.equals(EMPTY_SDK_BYTES_BUFFER)) {
            return null;
        } else {
            return sdkBytes.asString(UTF_8);
        }
    };

    public static TextMessage kinesisMessage(final @Nonnull String shard,
                                             final @Nonnull Record record) {

        return decode(
                Key.of(record.partitionKey()),
                Header.builder()
                        .withAttribute(MSG_ARRIVAL_TS, record.approximateArrivalTimestamp())
                        .withShardPosition(fromPosition(shard, record.sequenceNumber())).build(),
                SDK_BYTES_STRING.apply(record.data()));
    }


}
