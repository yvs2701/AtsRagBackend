package com.example.atsragbackend.config;

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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class OllamaConfig {

    private static final Logger log = LoggerFactory.getLogger(OllamaConfig.class);

    private static final int AI_REQUEST_TIMEOUT_MINUTES = 2;
    private static final String BASE_URL = "http://localhost:11434";
    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();
    private static final String MAC_OS_IDENTIFIER = "mac";
    private static final String MODEL_NAME_MAC = "gemma4:e4b-mlx";
    private static final String MODEL_NAME_OTHER = "gemma4:e4b";
    private static final String EMBEDDING_MODEL_NAME = "nomic-embed-text";

    // Centralize the base URL connection
    @Bean
    public OllamaApi ollamaApi(RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder) {
        Duration timeout = Duration.ofMinutes(AI_REQUEST_TIMEOUT_MINUTES);

        // Configure Sync Client Timeout using standard Spring HTTP factory
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setReadTimeout((int) timeout.toMillis());
        requestFactory.setConnectTimeout((int) timeout.toMillis());
        restClientBuilder.requestFactory(requestFactory);

        // Configure Async/Netty Client Timeout
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(timeout);
        webClientBuilder.clientConnector(new ReactorClientHttpConnector(httpClient));

        log.info("Configuring OllamaApi with base URL: {}", BASE_URL);
        return OllamaApi.builder()
                .baseUrl(BASE_URL)
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
                .build();
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