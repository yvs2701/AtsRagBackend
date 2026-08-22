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

    private static final String BASE_URL = "http://localhost:11434";
    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();
    private static final String MAC_OS_IDENTIFIER = "mac";
    private static final String MODEL_NAME_MAC = "gemma4:e4b-mlx";
    private static final String MODEL_NAME_OTHER = "gemma4:e4b";
    private static final String EMBEDDING_MODEL_NAME = "nomic-embed-text";

    // Centralize the base URL connection
    @Bean
    public OllamaApi ollamaApi(RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder) {
        Duration readTimeout = Duration.ofMinutes(AI_READ_TIMEOUT_MINUTES);
        Duration connectTimeout = Duration.ofSeconds(AI_CONNECT_TIMEOUT_SECONDS);

        // 1. Configure Synchronous Client
        java.net.http.HttpClient nativeHttpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(nativeHttpClient);
        requestFactory.setReadTimeout(readTimeout);

        // Request interceptor for logging
        restClientBuilder
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    try {
                        return execution.execute(request, body);
                    } catch (Exception e) {
                        logOllamaTimeoutViolation(e, request.getURI().toString());
                        throw e; // Rethrow to let the application handle the failure
                    }
                });

        // 2. Configure Asynchronous Client
        HttpClient nettyHttpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())
                .responseTimeout(readTimeout)
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(AI_READ_TIMEOUT_MINUTES, TimeUnit.MINUTES))
                        .addHandlerLast(new WriteTimeoutHandler(AI_READ_TIMEOUT_MINUTES, TimeUnit.MINUTES)));

        // Request filter for logging timeouts
        webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(nettyHttpClient))
                .filter((request, next) -> next.exchange(request).onErrorMap(e -> {
                    logOllamaTimeoutViolation(e, request.url().toString());
                    return e; // Rethrow to let the application handle the failure
                }));

        log.info("Configuring OllamaApi with base URL: {} | Read Timeout: {}m | Connect Timeout: {}s", BASE_URL, AI_READ_TIMEOUT_MINUTES, AI_CONNECT_TIMEOUT_SECONDS);
        return OllamaApi.builder()
                .baseUrl(BASE_URL)
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
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

    // Configure the Generation/Chat model
    @Bean
    public OllamaChatModel ollamaChatModel(OllamaApi ollamaApi) {
        String chatModelName = OS_NAME.contains(MAC_OS_IDENTIFIER) ? MODEL_NAME_MAC : MODEL_NAME_OTHER;
        log.info("Detected OS: {}. Using chat model: {}", OS_NAME, chatModelName);

        OllamaChatOptions chatOptions = OllamaChatOptions.builder()
                .model(chatModelName)
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

    // Configure the Retrieval/Embedding model
    @Bean
    public OllamaEmbeddingModel ollamaEmbeddingModel(OllamaApi ollamaApi) {
        OllamaEmbeddingOptions embeddingOptions = OllamaEmbeddingOptions.builder()
                .model(EMBEDDING_MODEL_NAME)
                .build();

        ModelManagementOptions modelManagementOptions = ModelManagementOptions.builder()
                .pullModelStrategy(PullModelStrategy.NEVER)
                .build();

        return OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .options(embeddingOptions)
                .modelManagementOptions(modelManagementOptions)
                .build();
    }
}