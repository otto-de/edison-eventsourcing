package de.otto.synapse.endpoint;

import com.google.common.collect.ImmutableList;
import de.otto.synapse.message.Key;
import de.otto.synapse.message.TextMessage;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class InterceptorChainTest {

    @Test
    @SuppressWarnings("unchecked")
    public void shouldBuildEmptyChain() {
        final InterceptorChain chain = new InterceptorChain();
        final TextMessage message = mock(TextMessage.class);
        final TextMessage intercepted = chain.intercept(message);
        verifyNoMoreInteractions(message);
        assertThat(message, is(intercepted));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldReturnNull() {
        final MessageInterceptor interceptor = mock(MessageInterceptor.class);
        when(interceptor.intercept(any(TextMessage.class))).thenReturn(null);
        final InterceptorChain chain = new InterceptorChain(ImmutableList.of(interceptor));
        assertThat(chain.intercept(someMessage("foo")), is(nullValue()));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldStopInterceptingOnNull() {
        final MessageInterceptor first = mock(MessageInterceptor.class);
        when(first.intercept(any(TextMessage.class))).thenReturn(null);
        final MessageInterceptor second = mock(MessageInterceptor.class);
        final InterceptorChain chain = new InterceptorChain(ImmutableList.of(first, second));
        assertThat(chain.intercept(someMessage("foo")), is(nullValue()));
        verifyNoMoreInteractions(second);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldReturnResultFromLastInterceptor() {
        final MessageInterceptor first = mock(MessageInterceptor.class);
        when(first.intercept(any(TextMessage.class))).thenReturn(someMessage("foo"));
        final MessageInterceptor second = mock(MessageInterceptor.class);
        when(second.intercept(any(TextMessage.class))).thenReturn(someMessage("bar"));
        final InterceptorChain chain = new InterceptorChain(ImmutableList.of(first, second));
        //noinspection ConstantConditions
        assertThat(chain.intercept(someMessage("foo")).getKey(), is(Key.of("bar")));
    }

    @SuppressWarnings("unchecked")
    private TextMessage someMessage(final String keyValue) {
        return TextMessage.of(Key.of(keyValue), null);
    }
}
