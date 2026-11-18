-- ==============================================================================
-- DevInsight Multi-Database & Vector Store Initialization
-- ==============================================================================

-- 1. Create databases for microservices
CREATE DATABASE devinsight;
CREATE DATABASE log_layer_db;
CREATE DATABASE llm_service_db;

-- 2. Configure pgvector and RAG Knowledge Base in llm_service_db
\c llm_service_db;

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS knowledge_chunks (
    id BIGSERIAL PRIMARY KEY,
    document_id VARCHAR(255) NOT NULL UNIQUE,
    content TEXT NOT NULL,
    metadata JSONB,
    embedding vector(768) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_knowledge_chunks_embedding
    ON knowledge_chunks USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_knowledge_chunks_doc_id
    ON knowledge_chunks (document_id);
