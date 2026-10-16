package com.mcverse.jobify.match;

import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Chooses the engine: the real gRPC one when {@code matching.enabled=true}, otherwise one that always says
 * "unavailable". The channel is plaintext because the service is reachable only on the internal network; mutual TLS
 * is a stretch goal of the plan.
 */
@Configuration
@EnableConfigurationProperties(MatchProperties.class)
class MatchConfig {

    @Bean
    @ConditionalOnProperty(name = "matching.enabled", havingValue = "false", matchIfMissing = true)
    MatchEngine disabledMatchEngine() {
        return new DisabledMatchEngine();
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "matching.enabled", havingValue = "true")
    ManagedChannel matchingChannel(MatchProperties properties) {
        if (properties.token().isBlank()) {
            throw new IllegalStateException("matching.enabled is true but MATCHING_TOKEN is not set. "
                    + "Set the shared secret, or switch matching off.");
        }
        return Grpc.newChannelBuilder(properties.target(), InsecureChannelCredentials.create())
                .idleTimeout(5, TimeUnit.MINUTES)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "matching.enabled", havingValue = "true")
    MatchEngine grpcMatchEngine(ManagedChannel matchingChannel, MatchProperties properties) {
        return new GrpcMatchEngine(matchingChannel, properties);
    }
}
