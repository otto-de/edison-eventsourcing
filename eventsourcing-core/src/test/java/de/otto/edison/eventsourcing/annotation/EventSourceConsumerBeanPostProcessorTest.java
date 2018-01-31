package de.otto.edison.eventsourcing.annotation;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.otto.edison.eventsourcing.DelegateEventSource;
import de.otto.edison.eventsourcing.configuration.EventSourcingConfiguration;
import de.otto.edison.eventsourcing.event.Event;
import de.otto.edison.eventsourcing.consumer.EventConsumer;
import de.otto.edison.eventsourcing.consumer.MethodInvokingEventConsumer;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

public class EventSourceConsumerBeanPostProcessorTest {

    private AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

    @After
    public void close() {
        if (this.context != null) {
            this.context.close();
        }
    }

    @Test
    public void shouldRegisterMultipleEventConsumers() {
        context.register(ObjectMapper.class);
        context.register(ThreeConsumersAtTwoEventSourcesConfiguration.class);
        context.register(EventSourcingConfiguration.class);
        context.refresh();

        final DelegateEventSource someStreamEventSource = context.getBean("testEventSource", DelegateEventSource.class);
        final List<EventConsumer<?>> eventConsumers = someStreamEventSource.registeredConsumers().getAll();
        assertThat(eventConsumers).hasSize(2);
        assertThat(eventConsumers.get(0)).isInstanceOf(MethodInvokingEventConsumer.class);
        assertThat(eventConsumers.get(1)).isInstanceOf(MethodInvokingEventConsumer.class);

        final DelegateEventSource otherStreamEventSource = context.getBean("otherStreamTestSource", DelegateEventSource.class);
        final List<EventConsumer<?>> otherEventConsumers = otherStreamEventSource.registeredConsumers().getAll();
        assertThat(otherEventConsumers).hasSize(1);
        assertThat(otherEventConsumers.get(0)).isInstanceOf(MethodInvokingEventConsumer.class);
    }

    @Test(expected = BeanCreationException.class)
    public void shouldFailToRegisterConsumerBecauseOfMissingEventSource() {
        context.register(ObjectMapper.class);
        context.register(TestConfigurationWithMissingEventSource.class);
        context.register(EventSourcingConfiguration.class);
        context.refresh();
    }

    /**
     * If there is a Consumer for stream x and the consumer does refer to a single EventSource instance,
     * registration should succeed, if there are _multiple_ EventSources for stream x with matching name.
     */
    @Test
    public void shouldRegisterConsumerAtSpecifiedEventSource() {
        context.register(ObjectMapper.class);
        context.register(TwoEventSourcesWithSameStreamAndSecificConsumerConfiguration.class);
        context.register(EventSourcingConfiguration.class);
        context.refresh();

        final DelegateEventSource someStreamEventSource = context.getBean("someTestEventSource", DelegateEventSource.class);
        final List<EventConsumer<?>> eventConsumers = someStreamEventSource.registeredConsumers().getAll();
        assertThat(eventConsumers).hasSize(1);
        assertThat(eventConsumers.get(0)).isInstanceOf(MethodInvokingEventConsumer.class);
    }

    @Test
    public void shouldCreateEventConsumerWithSpecificPayloadType() {
        context.register(ObjectMapper.class);
        context.register(TestConfigurationDifferentPayload.class);
        context.register(EventSourcingConfiguration.class);
        context.refresh();

        final DelegateEventSource someStreamEventSource = context.getBean("testEventSource", DelegateEventSource.class);
        final List<EventConsumer<?>> eventConsumers = someStreamEventSource.registeredConsumers().getAll();
        assertThat(eventConsumers).hasSize(2);
        Set<Object> payloadTYpes = eventConsumers.stream().map(EventConsumer::payloadType).collect(toSet());
        assertThat(payloadTYpes).containsExactlyInAnyOrder(String.class, Integer.class);
    }


    @Test
    public void shouldRegisterEventConsumerWithSpecificKeyPattern() {
        context.register(ObjectMapper.class);
        context.register(TestConfigurationDifferentPayload.class);
        context.register(EventSourcingConfiguration.class);
        context.refresh();

        final DelegateEventSource someStreamEventSource = context.getBean("testEventSource", DelegateEventSource.class);
        final List<EventConsumer<?>> eventConsumers = someStreamEventSource.registeredConsumers().getAll();
        assertThat(eventConsumers).hasSize(2);
        Set<String> pattern = eventConsumers.stream().map(consumer -> consumer.keyPattern().pattern()).collect(toSet());
        assertThat(pattern).containsExactlyInAnyOrder("apple.*", "banana.*");
    }

    @EnableEventSource(name = "testEventSource", streamName = "some-stream")
    @EnableEventSource(name = "otherStreamTestSource", streamName = "other-stream")
    static class ThreeConsumersAtTwoEventSourcesConfiguration {
        @Bean
        public TestConsumer test() {
            return new TestConsumer();
        }
    }

    @EnableEventSource(name = "someTestEventSource", streamName = "some-stream")
    @EnableEventSource(name = "otherTestEventSource", streamName = "some-stream")
    static class TwoEventSourcesWithSameStreamAndUnspecificConsumerConfiguration {
        @Bean
        public SingleUnspecificConsumer test() {
            return new SingleUnspecificConsumer();
        }
    }

    @EnableEventSource(name = "someTestEventSource", streamName = "some-stream")
    @EnableEventSource(name = "otherTestEventSource", streamName = "some-stream")
    static class TwoEventSourcesWithSameStreamAndSecificConsumerConfiguration {
        @Bean
        public SingleSpecificConsumer test() {
            return new SingleSpecificConsumer();
        }
    }

    @EnableEventSource(name = "testEventSource", streamName = "some-stream")
    static class TestConfigurationDifferentPayload {
        @Bean
        public TestConsumerWithSameStreamNameAndDifferentPayload test() {
            return new TestConsumerWithSameStreamNameAndDifferentPayload();
        }
    }

    static class TestConfigurationWithMissingEventSource{
        @Bean
        public TestConsumerWithSnapshotEventSource test() {
            return new TestConsumerWithSnapshotEventSource();
        }
    }

    static class SingleUnspecificConsumer {
        @EventSourceConsumer(
                eventSource = "someTestEventSource",
                payloadType = String.class)
        public void first(Event<String> event) {
        }

    }

    static class SingleSpecificConsumer {
        @EventSourceConsumer(
                eventSource = "someTestEventSource",
                payloadType = String.class)
        public void first(Event<String> event) {
        }

    }

    static class TestConsumer {
        @EventSourceConsumer(
                eventSource= "testEventSource",
                payloadType = String.class)
        public void first(Event<String> event) {
        }

        @EventSourceConsumer(
                eventSource = "testEventSource",
                payloadType = String.class)
        public void second(Event<String> event) {
        }

        @EventSourceConsumer(
                eventSource = "otherStreamTestSource",
                payloadType = String.class)
        public void third(Event<String> event) {
        }
    }

    static class TestConsumerWithSameStreamNameAndDifferentPayload {
        @EventSourceConsumer(
                eventSource = "testEventSource",
                keyPattern = "apple.*",
                payloadType = String.class)
        public void first(Event<String> event) {
        }

        @EventSourceConsumer(
                eventSource = "testEventSource",
                keyPattern = "banana.*",
                payloadType = Integer.class)
        public void second(Event<String> event) {
        }

    }

    static class TestConsumerWithSnapshotEventSource {
        @EventSourceConsumer(
                eventSource = "someTestEventSource",
                payloadType = String.class)
        public void first(Event<String> event) {
        }
    }

}
