package com.example.atsragbackend.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Configuration
public class OllamaConfig {

    private static final Logger log = LoggerFactory.getLogger(OllamaConfig.class);

    private static final int AI_READ_TIMEOUT_MINUTES = 5;
    private static final int AI_CONNECT_TIMEOUT_SECONDS = 30;

    private static final String LOCAL_BASE_URL = "http://localhost:11434";
    private static final String CLOUD_BASE_URL = "https://ollama.com";

    private static final String CHAT_MODEL_NAME = "nemotron-3-nano:30b-cloud";
    private static final String EMBEDDING_MODEL_NAME = "nomic-embed-text";

    // Cloud API for Chat LLM (requires authentication)
    @Bean
    public OllamaApi cloudOllamaApi(RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder, @Value("${ollama.api.key}") String apiKey) {
        return buildOllamaApi(CLOUD_BASE_URL, apiKey, restClientBuilder, webClientBuilder);
    }

    // Local API for Embedding Models (no authentication required)
    @Bean
    public OllamaApi localOllamaApi(RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder) {
        return buildOllamaApi(LOCAL_BASE_URL, null, restClientBuilder, webClientBuilder);
    }

    // Centralized helper to build OllamaApi with timeouts and optional API key
    private OllamaApi buildOllamaApi(String baseUrl, String apiKey, RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder) {
        Duration readTimeout = Duration.ofMinutes(AI_READ_TIMEOUT_MINUTES);
        Duration connectTimeout = Duration.ofSeconds(AI_CONNECT_TIMEOUT_SECONDS);

        // Configure Synchronous Client
        java.net.http.HttpClient nativeHttpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(nativeHttpClient);
        requestFactory.setReadTimeout(readTimeout);

        // Clone builder to prevent mutational side effects across the two APIs
        RestClient.Builder customizedRestClient = restClientBuilder.clone()
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    try {
                        return execution.execute(request, body);
                    } catch (Exception e) {
                        logOllamaTimeoutViolation(e, request.getURI().toString());
                        throw e; // Rethrow to let the application handle the failure
                    }
                });

        // Configure Asynchronous Client
        HttpClient nettyHttpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())
                .responseTimeout(readTimeout)
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(AI_READ_TIMEOUT_MINUTES, TimeUnit.MINUTES))
                        .addHandlerLast(new WriteTimeoutHandler(AI_READ_TIMEOUT_MINUTES, TimeUnit.MINUTES)));

        WebClient.Builder customizedWebClient = webClientBuilder.clone()
                .clientConnector(new ReactorClientHttpConnector(nettyHttpClient))
                .filter((request, next) -> next.exchange(request).onErrorMap(e -> {
                    logOllamaTimeoutViolation(e, request.url().toString());
                    return e;
                }));

        // Inject authorization header if connecting to the cloud
        if (apiKey != null && !apiKey.isBlank()) {
            customizedRestClient.defaultHeader("Authorization", "Bearer " + apiKey); //
            customizedWebClient.defaultHeader("Authorization", "Bearer " + apiKey); //
        }

        log.info("Configuring OllamaApi with base URL: {} | Read Timeout: {}m | Connect Timeout: {}s",
                baseUrl, AI_READ_TIMEOUT_MINUTES, AI_CONNECT_TIMEOUT_SECONDS);
        return OllamaApi.builder()
                .baseUrl(baseUrl)
                .restClientBuilder(customizedRestClient)
                .webClientBuilder(customizedWebClient)
                .build();
    }

    /**
     * Centralized logger to determine which timeout was violated.
     */
    private void logOllamaTimeoutViolation(Throwable e, String uri) {
        Throwable cause = e;
        while (cause != null) {
            // Check for Connect Timeouts
            if (cause instanceof java.net.http.HttpConnectTimeoutException ||
                    cause instanceof java.net.ConnectException ||
                    (cause instanceof java.net.SocketTimeoutException
                            && cause.getMessage() != null
                            && cause.getMessage().contains("connect"))) {
                log.error("[OLLAMA TIMEOUT] Took too long to connect to: {}", uri);
                return;
            }

            // Check for Read Timeouts
            if (cause instanceof java.net.http.HttpTimeoutException ||
                    cause instanceof java.net.SocketTimeoutException ||
                    cause instanceof ReadTimeoutException) {
                log.error("[OLLAMA TIMEOUT] The model took too long to generate a response. URI: {}",
                        uri);
                return;
            }
            cause = cause.getCause();
        }
    }

    // Configure the Generation/Chat model to use the Cloud API
    @Bean
    public OllamaChatModel ollamaChatModel(@Qualifier("cloudOllamaApi") OllamaApi ollamaApi) {
        log.info("Using cloud chat model: {}", CHAT_MODEL_NAME);

        OllamaChatOptions chatOptions = OllamaChatOptions.builder()
                .model(CHAT_MODEL_NAME)
                .numCtx(4096)
                .disableThinking()
                .build();

        ModelManagementOptions modelManagementOptions = ModelManagementOptions.builder()
                .pullModelStrategy(PullModelStrategy.NEVER)
                .build();

        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .options(chatOptions)
                .modelManagementOptions(modelManagementOptions)
                .build();
    }

    // Configure the Retrieval/Embedding model to use the Local API
    @Bean
    public OllamaEmbeddingModel ollamaEmbeddingModel(@Qualifier("localOllamaApi") OllamaApi ollamaApi) {
        OllamaEmbeddingOptions embeddingOptions = OllamaEmbeddingOptions.builder()
                .model(EMBEDDING_MODEL_NAME)
                .build();

        ModelManagementOptions modelManagementOptions = ModelManagementOptions.builder()
                .pullModelStrategy(PullModelStrategy.WHEN_MISSING)
                .build();

        return OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .options(embeddingOptions)
                .modelManagementOptions(modelManagementOptions)
                .build();
    }
}