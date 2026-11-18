package ec.com.technoloqie.auditbot.api.config;

import ec.com.technoloqie.auditbot.api.analyzer.KnowledgeGapAnalyzer;
import ec.com.technoloqie.auditbot.api.analyzer.LeadAnalyzer;
import ec.com.technoloqie.auditbot.api.analyzer.SentimentAnalyzer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración del cliente de Spring AI para la integración con endpoints
 * compatibles con OpenAI (como Ollama /v1).
 */
@Configuration
public class OpenAiConfiguration {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultOptions(OpenAiChatOptions.builder().temperature(0.1))
                .build();
    }

    @Bean
    public KnowledgeGapAnalyzer knowledgeGapAnalyzer() {
        return new KnowledgeGapAnalyzer();
    }

    @Bean
    public SentimentAnalyzer sentimentAnalyzer() {
        return new SentimentAnalyzer();
    }

    @Bean
    public LeadAnalyzer leadAnalyzer() {
        return new LeadAnalyzer();
    }
}
