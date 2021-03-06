package de.otto.synapse.eventsource;

import de.otto.synapse.channel.selector.Selector;
import de.otto.synapse.endpoint.Selectable;
import de.otto.synapse.endpoint.receiver.MessageLogReceiverEndpoint;

/**
 * A builder used to build {@link EventSource instances}.
 */
public interface EventSourceBuilder extends Selectable {

    /**
     * Should build an event source for a given stream name. Classes that implement this interface use this differently
     * depending on their specific event queuing tech.
     *
     * @param messageLogReceiverEndpoint the MessageLogReceiverEndpoint used to consume
     * @return EventSource implementation for this specific technology
     */
    EventSource buildEventSource(final MessageLogReceiverEndpoint messageLogReceiverEndpoint);

    @Override
    default boolean matches(final Class<? extends Selector> channelSelector) {
        return channelSelector.isAssignableFrom(selector());
    }
}
