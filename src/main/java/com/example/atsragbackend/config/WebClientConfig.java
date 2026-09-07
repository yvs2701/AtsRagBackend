package com.example.atsragbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        // 1. Configure the connection pool
        ConnectionProvider provider = ConnectionProvider.builder("apify-connection-pool")
                // Drop connections from the pool if they sit idle for more than 60 seconds
                .maxIdleTime(Duration.ofSeconds(60))
                // Run a background task to clean up expired connections proactively
                .evictInBackground(Duration.ofSeconds(120))
                .build();

        // 2. Apply the provider to the Netty HttpClient
        HttpClient httpClient = HttpClient.create(provider);

        // 3. Plug the custom Netty client into Spring's WebClient
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}