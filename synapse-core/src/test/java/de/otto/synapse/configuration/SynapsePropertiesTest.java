package de.otto.synapse.configuration;

import org.junit.After;
import org.junit.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class SynapsePropertiesTest {

    private AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

    @After
    public void close() {
        if (this.context != null) {
            this.context.close();
        }
    }

    @Test
    public void shoulResolveSenderNamePlaceHolder() {

        context.register(SynapseAutoConfiguration.class);
        TestPropertyValues.of(
                "spring.application.name=my service"
        ).applyTo(context);
        context.refresh();

        assertThat(context.getBean(SynapseProperties.class).getSender().getName()).isEqualTo("my service");
    }
}