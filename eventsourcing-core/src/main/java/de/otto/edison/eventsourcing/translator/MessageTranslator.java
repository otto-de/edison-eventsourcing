package de.otto.edison.eventsourcing.translator;

import de.otto.edison.eventsourcing.message.Message;

import javax.annotation.Nonnull;

/**
 * <p>
 *     The Message Translator is the messaging equivalent of the Adapter pattern described in
 *     [GoF]. An adapter converts the interface of a component into a another interface so it
 *     can be used in a different context.
 * </p>
 * <p>
 *     <img src="http://www.enterpriseintegrationpatterns.com/img/MessageTranslator.gif" alt="MessageTranslator">
 * </p>
 * @param <P> The type of the translated message's payload
 */
@FunctionalInterface
public interface MessageTranslator<P> {

    /**
     * Translates a Message into a Message with payload-type &lt;P&gt;
     *
     * @param message Message&lt;?&gt;
     * @return Message&lt;P&gt;
     */
    @Nonnull Message<P> translate(final @Nonnull Message<?> message);

}