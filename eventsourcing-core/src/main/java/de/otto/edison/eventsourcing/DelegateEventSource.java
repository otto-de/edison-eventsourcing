package de.otto.edison.eventsourcing;

import de.otto.edison.eventsourcing.consumer.*;
import de.otto.edison.eventsourcing.event.Event;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.function.Predicate;

public class DelegateEventSource implements EventSource, ApplicationContextAware {

    private final String name;
    private final String streamName;
    private final String eventSourceBuilder;
    private EventSource delegate;

    public DelegateEventSource(final String name,
                               final String streamName,
                               final String eventSourceBuilder) {
        this.name = name;
        this.streamName = streamName;
        this.eventSourceBuilder = eventSourceBuilder;
    }

    @Override
    public String getName() {
        return name;
    }

    public EventSource getDelegate() {
        return delegate;
    }

    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {
        delegate = applicationContext
                .getBean(eventSourceBuilder, EventSourceBuilder.class)
                .buildEventSource(name, streamName);
    }

    /**
     * Registers a new EventConsumer at the EventSource.
     * <p>
     * {@link EventConsumer consumers} have to be thread safe as it may be called from multiple threads
     * (e.g. for kinesis streams there is one thread per shard)
     *
     * @param eventConsumer the registered EventConsumer
     */
    @Override
    public void register(final EventConsumer<?> eventConsumer) {
        delegate.register(eventConsumer);
    }

    /**
     * Returns registered EventConsumers.
     *
     * @return EventConsumers
     */
    @Override
    public EventConsumers registeredConsumers() {
        return delegate.registeredConsumers();
    }

    /**
     * Returns the name of the EventSource.
     * <p>
     * For streaming event-sources, this is the name of the event stream.
     * </p>
     *
     * @return name
     */
    @Override
    public String getStreamName() {
        return delegate.getStreamName();
    }

    /**
     * Consumes all events from the EventSource, beginning with {@link StreamPosition startFrom}, until
     * the {@link Predicate stopCondition} is met.
     * <p>
     * The registered {@link EventConsumer consumers} will be called zero or more times, depending on
     * the number of events retrieved from the EventSource.
     * </p>
     *
     * @param startFrom     the read position returned from earlier executions
     * @param stopCondition the predicate used as a stop condition
     * @return the new read position
     */
    @Override
    public StreamPosition consumeAll(final StreamPosition startFrom,
                                     final Predicate<Event<?>> stopCondition) {
        return delegate.consumeAll(startFrom, stopCondition);
    }

    @Override
    public String toString() {
        return "DelegateEventSource{" +
                "streamName='" + streamName + '\'' +
                ", eventSourceBuilder='" + eventSourceBuilder + '\'' +
                ", delegate=" + delegate +
                '}';
    }
}
