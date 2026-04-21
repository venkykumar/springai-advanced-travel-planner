package com.example.travelplanner.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    /**
     * Selects the active ChatModel based on spring.ai.active.model (default: openai).
     * Switch at runtime: SPRING_AI_ACTIVE_MODEL=anthropic mvn spring-boot:run
     *
     * Note: EmbeddingModel always uses OpenAI — Anthropic has no embedding model.
     * PgVectorStore and knowledge base ingestion are unaffected by this setting.
     */
    @Bean
    @Primary
    public ChatModel activeChatModel(
            OpenAiChatModel openAiChatModel,
            @Autowired(required = false) AnthropicChatModel anthropicChatModel,
            @Value("${spring.ai.active.model:openai}") String activeModel) {

        if ("anthropic".equalsIgnoreCase(activeModel) && anthropicChatModel != null) {
            log.info("Active chat model: Anthropic Claude (claude-sonnet-4-6)");
            return anthropicChatModel;
        }
        log.info("Active chat model: OpenAI (gpt-4o)");
        return openAiChatModel;
    }

    /**
     * Virtual-thread executor for parallel sub-agent dispatch.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService agentExecutor(
            @Value("${travel.agent.thread-pool-size:10}") int poolSize) {
        return Executors.newFixedThreadPool(poolSize);
    }

    /**
     * Declare JdbcChatMemoryRepository explicitly — Spring AI does not auto-configure
     * this bean. Also ensures the table exists using the exact schema Spring AI queries
     * (init.sql only runs on a fresh Postgres volume, so this is the reliable path).
     */
    @Bean
    public JdbcChatMemoryRepository jdbcChatMemoryRepository(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS spring_ai_chat_memory (
                    conversation_id TEXT NOT NULL,
                    content         TEXT NOT NULL,
                    type            TEXT NOT NULL,
                    "timestamp"     TIMESTAMP NOT NULL DEFAULT NOW()
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_chat_memory_conv_id
                    ON spring_ai_chat_memory (conversation_id)
                """);
        return JdbcChatMemoryRepository.builder()
                .jdbcTemplate(jdbcTemplate)
                .build();
    }

    /**
     * JDBC-backed chat memory — conversations survive application restarts.
     */
    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository jdbcChatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(jdbcChatMemoryRepository)
                .maxMessages(20)
                .build();
    }
}
