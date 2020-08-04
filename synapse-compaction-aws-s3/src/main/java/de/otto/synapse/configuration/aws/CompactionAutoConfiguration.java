package de.otto.synapse.configuration.aws;

import de.otto.synapse.compaction.s3.CompactionService;
import de.otto.synapse.compaction.s3.SnapshotWriteService;
import de.otto.synapse.endpoint.receiver.MessageLogReceiverEndpointFactory;
import de.otto.synapse.eventsource.EventSourceBuilder;
import de.otto.synapse.messagestore.MessageStore;
import de.otto.synapse.messagestore.MessageStoreFactory;
import de.otto.synapse.state.ConcurrentMapStateRepository;
import de.otto.synapse.state.StateRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@EnableConfigurationProperties(CompactionProperties.class)
@Import(S3SnapshotAutoConfiguration.class)
public class CompactionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "compactionStateRepository")
    public StateRepository<String> compactionStateRepository() {
        return new ConcurrentMapStateRepository<>("Compaction");
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "synapse.compaction", name = "enabled", havingValue = "true")
    public CompactionService compactionService(final SnapshotWriteService snapshotWriteService,
                                               final StateRepository<String> compactionStateRepository,
                                               final MessageLogReceiverEndpointFactory messageLogReceiverEndpointFactory,
                                               final MessageStoreFactory<? extends MessageStore> messageStoreFactory) {
        return new CompactionService(snapshotWriteService, compactionStateRepository, messageLogReceiverEndpointFactory, messageStoreFactory);
    }
}
