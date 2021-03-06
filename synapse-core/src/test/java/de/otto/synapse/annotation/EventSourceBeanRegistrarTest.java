package de.otto.synapse.annotation;

import de.otto.synapse.channel.selector.MessageLog;
import de.otto.synapse.configuration.InMemoryMessageLogTestConfiguration;
import de.otto.synapse.endpoint.receiver.MessageLogReceiverEndpoint;
import de.otto.synapse.eventsource.DefaultEventSource;
import de.otto.synapse.eventsource.DelegateEventSource;
import de.otto.synapse.eventsource.EventSource;
import de.otto.synapse.eventsource.EventSourceBuilder;
import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;

import static de.otto.synapse.messagestore.MessageStores.emptyMessageStore;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class EventSourceBeanRegistrarTest {

    private AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

    @After
    public void close() {
        if (this.context != null) {
            this.context.close();
        }
    }

    interface CustomSelector extends MessageLog {}
    static final EventSourceBuilder customEventSourceBuilder = mock(EventSourceBuilder.class);
    @EnableEventSource(name = "testEventSource", channelName = "test-stream", selector = CustomSelector.class)
    static class CustomEventSourceTestConfig {
        @Bean
        EventSourceBuilder customEventSourceBuilder() {
            when(customEventSourceBuilder.selector()).thenAnswer(invocationOnMock -> CustomSelector.class);
            when(customEventSourceBuilder.matches(any())).thenCallRealMethod();
            when(customEventSourceBuilder.buildEventSource(any(MessageLogReceiverEndpoint.class)))
                    .thenAnswer(invocationOnMock -> new DefaultEventSource(emptyMessageStore(), invocationOnMock.getArgument(0)));
            return customEventSourceBuilder;
        }
    }
    @EnableEventSource(name = "testEventSource", channelName = "test-stream")
    static class SingleEventSourceTestConfig {
    }

    @EnableEventSource(name = "testEventSource", channelName = "test-stream", messageLogReceiverEndpoint = "testMessageLog")
    static class SingleEventSourceWithMessageLogTestConfig {
    }

    @EnableEventSource(name = "brokenEventSource", channelName = "some-stream")
    @EnableEventSource(name = "brokenEventSource", channelName = "some-stream")
    static class MultiEventSourceTestConfigWithSameNames {
    }

    @EnableEventSource(name = "firstEventSource", channelName = "some-stream")
    @EnableEventSource(name = "secondEventSource", channelName = "some-stream")
    static class MultiEventSourceTestConfigWithDifferentNames {
    }

    @EnableEventSource(name = "firstEventSource", channelName = "first-stream")
    @EnableEventSource(name = "secondEventSource", channelName = "${test.stream-name}")
    static class RepeatableMultiEventSourceTestConfig {
    }

    @Test(expected = BeanCreationException.class)
    public void shouldFailToRegisterMultipleEventSourcesForSameStreamNameWithSameName() {
        context.register(MultiEventSourceTestConfigWithSameNames.class);
        context.register(InMemoryMessageLogTestConfiguration.class);
        context.refresh();
    }

    @Test(expected = BeanCreationException.class)
    public void shouldFailToRegisterMultipleEventSourcesForSameStream() {
        context.register(MultiEventSourceTestConfigWithDifferentNames .class);
        context.register(InMemoryMessageLogTestConfiguration.class);
        context.refresh();
    }

    @Test
    public void shouldRegisterEventSource() {
        context.register(SingleEventSourceTestConfig.class);
        context.register(InMemoryMessageLogTestConfiguration.class);
        context.refresh();

        assertThat(context.containsBean("testEventSource")).isTrue();
    }

    @Test
    public void shouldRegisterEventSourceWithSelectedEventSourceBuilder() {
        context.register(CustomEventSourceTestConfig.class);
        context.register(InMemoryMessageLogTestConfiguration.class);
        context.refresh();

        assertThat(context.containsBean("testEventSource")).isTrue();
        assertThat(context.containsBean("customEventSourceBuilder")).isTrue();
        verify(customEventSourceBuilder).buildEventSource(any(MessageLogReceiverEndpoint.class));
    }

    @Test
    public void shouldRegisterEventSourceWithDefaultType() {
        context.register(InMemoryMessageLogTestConfiguration.class);
        context.register(RepeatableMultiEventSourceTestConfig.class);
        context.refresh();

        final DelegateEventSource testEventSource = context.getBean("firstEventSource", DelegateEventSource.class);
        assertThat(testEventSource.getDelegate()).isInstanceOf(DefaultEventSource.class);
    }

    @Test
    public void shouldRegisterMultipleEventSources() {
        context.register(RepeatableMultiEventSourceTestConfig.class);
        context.register(InMemoryMessageLogTestConfiguration.class);
        TestPropertyValues.of(
                "test.stream-name=second-stream"
        ).applyTo(context);
        context.refresh();

        assertThat(context.containsBean("firstEventSource")).isTrue();
        assertThat(context.containsBean("secondEventSource")).isTrue();

        final EventSource first = context.getBean("firstEventSource", DelegateEventSource.class).getDelegate();
        assertThat(first.getChannelName()).isEqualTo("first-stream");
        assertThat(first).isInstanceOf(DefaultEventSource.class);

        final EventSource second = context.getBean("secondEventSource", DelegateEventSource.class).getDelegate();
        assertThat(second.getChannelName()).isEqualTo("second-stream");
        assertThat(second).isInstanceOf(DefaultEventSource.class);
    }

    @Test
    public void shouldRegisterMessageLogReceiverEndpointWithNameDerivedFromChannelName() {
        context.register(SingleEventSourceTestConfig.class);
        context.register(InMemoryMessageLogTestConfiguration.class);
        context.refresh();

        assertThat(context.containsBean("testStreamMessageLogReceiverEndpoint")).isTrue();
        final MessageLogReceiverEndpoint receiverEndpoint = context.getBean("testStreamMessageLogReceiverEndpoint", MessageLogReceiverEndpoint.class);
        assertThat(receiverEndpoint.getChannelName()).isEqualTo("test-stream");
    }

    @Test
    public void shouldRegisterMessageLogReceiverEndpointWithSpecifiedName() {
        context.register(SingleEventSourceWithMessageLogTestConfig.class);
        context.register(InMemoryMessageLogTestConfiguration.class);
        context.refresh();

        assertThat(context.containsBean("testMessageLog")).isTrue();
        final MessageLogReceiverEndpoint receiverEndpoint = context.getBean("testMessageLog", MessageLogReceiverEndpoint.class);
        assertThat(receiverEndpoint.getChannelName()).isEqualTo("test-stream");
    }

}
