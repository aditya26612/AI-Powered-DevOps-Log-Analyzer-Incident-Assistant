# LLM Service internals

The LLM Service receives authenticated internal diagnosis requests from Log Layer. It accepts scoped log or incident context, retrieves shared DevOps runbooks, calls Ollama, and parses the response into structured findings for the caller.

## Runbook retrieval

`RagIngestionService` prepares packaged runbooks and maintains the shared index. The packaged knowledge lives apart from tenant source logs. Dense retrieval uses pgvector and Ollama embeddings. `BM25Retriever` supplies lexical candidates, and `HybridRetriever` combines ranked candidates through reciprocal rank fusion. The repository includes [the retrieval evaluation](../../docs/verification/rag_retrieval_experiments_and_final_decision.md).

## Diagnosis and operation

The prompt builder combines retrieved knowledge with bounded operational evidence. Parsing and validation distinguish a usable diagnosis from provider, retrieval or response failures. Log Layer owns durable claims, retries, accepted results and notification intent. The output is advisory.

Configure `INTERNAL_API_KEY`, `OLLAMA_BASE_URL` and the `llm_service_db` connection. The schema needs pgvector and the `knowledge_chunks` table. Pull `phi3:mini` and `nomic-embed-text:latest` before synchronizing a fresh vector store. With `RAG_SYNCHRONIZE_ON_START=false`, startup rebuilds the in-memory BM25 index from packaged runbooks and preserves the existing PostgreSQL vectors. With synchronization enabled, startup also updates the managed vector documents. The readiness check uses `/actuator/health`.

The verified clean-copy Compose workflow used an Ollama server on the Docker Desktop host. See the [host Ollama setup](../CONTAINER_DEPLOYMENT.md#use-ollama-already-running-on-the-docker-desktop-host) and [verification evidence](../../docs/VERIFICATION.md#clean-copy-compose-smoke-and-http-replay).
