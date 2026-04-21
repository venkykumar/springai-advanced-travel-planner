-- Enable pgvector extension — required for Spring AI PgVectorStore
CREATE EXTENSION IF NOT EXISTS vector;

-- Chat memory table — matches exact schema Spring AI JdbcChatMemoryRepository queries
CREATE TABLE IF NOT EXISTS spring_ai_chat_memory (
    conversation_id TEXT      NOT NULL,
    content         TEXT      NOT NULL,
    type            TEXT      NOT NULL,
    "timestamp"     TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_chat_memory_conv_id
    ON spring_ai_chat_memory (conversation_id);

-- Spring AI PgVectorStore auto-creates vector_store on startup
-- (spring.ai.vectorstore.pgvector.initialize-schema=true)
