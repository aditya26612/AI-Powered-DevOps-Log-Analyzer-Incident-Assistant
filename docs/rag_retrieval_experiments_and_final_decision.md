# RAG Retrieval Experiments and Evaluation

## AI-Powered DevOps Log Analyzer — LLM Service

This document records the retrieval experiments performed during development of the LLM Service's Retrieval-Augmented Generation (RAG) pipeline.

The purpose of this document is to provide **engineering evidence for retrieval design decisions**. Each experiment was evaluated against a fixed benchmark, and changes were retained or rejected based on measured results rather than assumptions.

> **Important:** The results in this document are internal experimental results from the project's DevOps knowledge base and benchmark. They should not be interpreted as general claims about RAG systems.

---

## 1. Experimental Objective

The RAG pipeline was developed incrementally from a dense-retrieval baseline toward a hybrid retrieval architecture.

The experimental progression was:

```text
Knowledge Documents
       ↓
Document Chunking
       ↓
Dense Vector Retrieval
       ↓
BM25 Retrieval
       ↓
Hybrid Retrieval with RRF
       ↓
Reranking Experiments
       ↓
Retrieved Context
       ↓
LLM
```

The primary objective was to determine which retrieval configuration produced the strongest results on the project's fixed DevOps benchmark.

The evaluation used:

- Hit@1
- Hit@3
- Hit@5
- Mean Reciprocal Rank (MRR)

A controlled, incremental approach was used so that the effect of each major change could be measured independently.

---

# 2. Knowledge Base

The RAG knowledge base contains **21 Markdown documents** covering common DevOps troubleshooting scenarios.

| Category | Documents |
|---|---:|
| Docker | 5 |
| Kubernetes | 5 |
| Nginx | 3 |
| PostgreSQL | 3 |
| Spring Boot | 5 |
| **Total** | **21** |

The knowledge files are stored under:

```text
src/main/resources/knowledge/
```

The knowledge base contains troubleshooting information covering areas such as:

- Docker container startup failures
- Docker image pull failures
- Docker networking problems
- Kubernetes CrashLoopBackOff
- Kubernetes ImagePullBackOff
- Kubernetes deployment failures
- Nginx upstream timeouts
- Nginx connection failures
- PostgreSQL authentication failures
- PostgreSQL connection failures
- Spring Boot datasource failures
- Spring Boot port binding failures
- Spring Boot startup failures

---

# 3. Document Loading

The active document loader is:

```text
ResourceDocumentLoader
```

It loads Markdown resources recursively using:

```text
classpath*:knowledge/**/*.md
```

This allows the knowledge base to be organized by technology:

```text
knowledge/
├── docker/
├── kubernetes/
├── nginx/
├── postgresql/
└── spring-boot/
```

`FileSystemDocumentLoader` is not part of the active retrieval path.

---

# 4. Document Chunking

The stable configuration uses:

```text
Chunk size    = 1000 characters
Chunk overlap = 0 characters
```

For example, a 2500-character document is divided conceptually into:

```text
Chunk 0 → 0–999
Chunk 1 → 1000–1999
Chunk 2 → 2000–2499
```

The resulting 21-document knowledge base contains:

```text
189 chunks
```

This configuration became the reference configuration after the overlap experiment described below.

---

# 5. Chunk Overlap Experiment

A 200-character overlap was introduced to determine whether retaining context across chunk boundaries improved retrieval.

The experimental configuration was:

```text
Chunk size    = 1000
Chunk overlap = 200
```

Conceptually:

```text
Chunk 0 → 0–999
Chunk 1 → 800–1799
Chunk 2 → 1600–2499
```

The implementation initially required an additional termination condition to prevent the final chunk from being processed repeatedly. The chunking tests were subsequently updated and passed.

### Chunking test result

```text
Tests run: 5
Failures: 0
Errors: 0
BUILD SUCCESS
```

The overlap configuration was then evaluated using the same 20-query retrieval benchmark.

---

## 5.1 Dense Retrieval with 200-Character Overlap

```text
Hit@1 = 80%
Hit@3 = 95%
Hit@5 = 95%
MRR   = 0.8500
```

The no-overlap dense baseline was:

```text
Hit@1 = 80%
Hit@3 = 100%
Hit@5 = 100%
MRR   = 0.9000
```

Therefore, the overlap configuration reduced retrieval performance on this benchmark.

---

## 5.2 BM25 with 200-Character Overlap

```text
Hit@1 = 65%
Hit@3 = 95%
Hit@5 = 100%
MRR   = 0.8042
```

The no-overlap BM25 result was:

```text
Hit@1 = 75%
Hit@3 = 95%
Hit@5 = 100%
MRR   = 0.8542
```

---

## 5.3 Hybrid Retrieval with 200-Character Overlap

```text
Hit@1 = 65%
Hit@3 = 95%
Hit@5 = 100%
MRR   = 0.8042
```

The no-overlap Hybrid result was:

```text
Hit@1 = 75%
Hit@3 = 95%
Hit@5 = 100%
MRR   = 0.8542
```

---

## 5.4 Overlap Experiment Decision

The 200-character overlap did not improve any of the evaluated retrieval configurations.

The measured MRR changes were:

```text
Dense:
0.9000 → 0.8500

BM25:
0.8542 → 0.8042

Hybrid:
0.8542 → 0.8042
```

Based on these results, the overlap configuration was rejected.

The splitter was restored to:

```text
Chunk size    = 1000 characters
Chunk overlap = 0 characters
```

The restored chunking tests passed:

```text
Tests run: 5
Failures: 0
Errors: 0
BUILD SUCCESS
```

The knowledge base returned to:

```text
21 documents
189 chunks
```

---

# 6. Dense Retrieval Baseline

Dense vector retrieval was used as the initial retrieval baseline.

The system uses:

```text
nomic-embed-text
```

through the existing embedding infrastructure, with vector similarity used to rank candidate chunks.

The fixed benchmark produced:

```text
Hit@1 = 80%
Hit@3 = 100%
Hit@5 = 100%
MRR   = 0.9000
```

This became the reference dense-retrieval result for subsequent comparisons.

---

# 7. BM25 Retrieval

BM25 was introduced as a lexical retrieval mechanism to complement semantic vector retrieval.

The intended distinction was:

```text
Dense Retrieval
→ semantic similarity

BM25
→ lexical / terminology matching
```

This is particularly relevant to DevOps knowledge because technical identifiers and configuration terms can carry substantial information.

Examples include:

```text
CrashLoopBackOff
ImagePullBackOff
ECONNREFUSED
SQLSTATE
502
504
OOMKilled
port 8080
```

The BM25 implementation uses:

```text
K1 = 1.5
B  = 0.75
```

and maintains the information required to calculate BM25 relevance scores, including:

```text
Term Frequency
Document Frequency
Average Document Length
```

The resulting architecture is:

```text
Knowledge Documents
       ↓
Document Splitter
       ↓
BM25 Index
       ↓
BM25 Retriever
       ↓
Ranked Results
```

A BM25 integration test was added and verified using the real knowledge documents.

---

# 8. Retrieval Benchmark

The retrieval benchmark contains:

```text
20 queries
```

distributed across:

```text
Spring Boot → 5
Docker      → 5
Kubernetes  → 5
Nginx       → 3
PostgreSQL  → 2
```

Each retrieval strategy was evaluated using the same benchmark.

The metrics were:

```text
Hit@1
Hit@3
Hit@5
MRR
```

Using the same queries and evaluation procedure provides a controlled basis for comparing retrieval strategies.

---

# 9. Benchmark Identifier Normalization

During benchmark development, a document identifier comparison issue was identified.

The benchmark expected a document identifier such as:

```text
spring-boot-datasource-error
```

while retrieved chunks could be represented as:

```text
spring-boot-datasource-error.md-chunk-8
```

The evaluation logic was updated to normalize chunk identifiers before comparison:

```text
spring-boot-datasource-error.md-chunk-8
                 ↓
spring-boot-datasource-error
```

This was a benchmark evaluation issue rather than a retrieval algorithm issue.

After normalization, the BM25 results were:

```text
Hit@1 = 75%
Hit@3 = 95%
Hit@5 = 100%
MRR   = 0.8542
```

---

# 10. Hybrid Retrieval

The next experiment combined:

```text
Dense Retrieval
+
BM25 Retrieval
```

The selected fusion method was:

```text
Reciprocal Rank Fusion (RRF)
```

The purpose of RRF is to combine independently ranked result lists without requiring the scores from the two retrieval systems to be directly comparable.

---

# 11. Reciprocal Rank Fusion

The implementation uses the standard RRF-style scoring relationship:

```text
RRF Score = 1 / (k + rank)
```

with:

```text
k = 60
```

A document receives a contribution based on its rank in each retrieval list.

If the same document appears in both the dense and BM25 result lists, the contributions are combined.

Conceptually:

```text
Dense Results
      +
BM25 Results
      ↓
RRF Score Calculation
      ↓
Combined Ranking
```

---

# 12. Hybrid Retriever

The `HybridRetriever` combines:

```text
VectorRetriever
      +
BM25Retriever
      ↓
RRF Fusion
```

The candidate count is determined using:

```java
int candidateK = Math.max(topK * 2, 10);
```

For example, when:

```text
topK = 3
```

the hybrid retrieval stage can consider up to:

```text
10 candidates
```

before producing the final result list.

---

# 13. Frozen Hybrid Baseline

After restoring the 1000-character, zero-overlap configuration, the Hybrid RRF benchmark was rerun.

The result was:

```text
Total queries : 20
Hit@1         : 15/20 = 75%
Hit@3         : 19/20 = 95%
Hit@5         : 20/20 = 100%
MRR           : 0.8542
```

This configuration was frozen as the **pre-reranker reference baseline**.

```text
Hybrid RRF Baseline

Hit@1 = 75%
Hit@3 = 95%
Hit@5 = 100%
MRR   = 0.8542
```

All subsequent reranking experiments were compared against this result.

---

# 14. Reranker Abstraction

A separate reranking abstraction was introduced so that retrieval and reranking remained independent responsibilities.

Conceptually:

```text
Dense Retrieval ──┐
                  │
                  ▼
              RRF Fusion
                  ↓
             Candidates
                  ↓
               Reranker
                  ↓
                Top-K
```

The reranker interface accepts:

```java
List<RetrievalResult> rerank(
        String query,
        List<RetrievalResult> candidates,
        int topK
);
```

The architectural responsibility is:

```text
Retriever
→ finds candidate documents

Reranker
→ scores/reorders candidate documents
```

The reranker does not perform the initial retrieval.

---

# 15. Lexical Reranker Experiment

The first reranking implementation was a simple lexical relevance approach.

The method scores candidates using query-term overlap.

For example:

```text
Query:
Docker container keeps restarting
```

can be tokenized into terms such as:

```text
docker
container
keeps
restarting
```

Candidate documents receive higher scores when more query terms occur in the document.

---

## 15.1 Lexical Reranker Tests

A dedicated test suite verified:

1. Relevant document ranking
2. `topK` behavior
3. Invalid input handling
4. Invalid `topK` handling

Result:

```text
Tests run: 4
Failures: 0
Errors: 0
BUILD SUCCESS
```

---

## 15.2 Lexical Reranker Benchmark

The pipeline was:

```text
Dense
  +
BM25
  ↓
RRF
  ↓
Lexical Reranker
  ↓
Top-K
```

Results:

```text
Hit@1 = 70%
Hit@3 = 95%
Hit@5 = 100%
MRR   = 0.8292
```

Compared with the frozen Hybrid RRF baseline:

| Metric | Hybrid RRF | Hybrid + Lexical Reranker |
|---|---:|---:|
| Hit@1 | 75% | 70% |
| Hit@3 | 95% | 95% |
| Hit@5 | 100% | 100% |
| MRR | 0.8542 | 0.8292 |

The lexical reranker therefore **did not improve retrieval quality** on the benchmark.

---

# 16. TF-IDF-Inspired Reranker Experiment

A second experiment modified the lexical scoring approach using document-frequency information.

The objective was to reduce the influence of common terms and give relatively greater weight to rarer terms.

Conceptually:

```text
Common terms
    ↓
Lower weight

Rare terms
    ↓
Higher weight
```

This produced a TF-IDF-inspired relevance score.

---

## 16.1 TF-IDF Reranker Tests

The reranker test suite was executed again.

Result:

```text
Tests run: 4
Failures: 0
Errors: 0
BUILD SUCCESS
```

The implementation was therefore functionally valid before benchmark evaluation.

---

## 16.2 TF-IDF Reranker Benchmark

The same 20-query benchmark was used.

Results:

```text
Hit@1 = 70%
Hit@3 = 90%
Hit@5 = 95%
MRR   = 0.8017
```

Comparison:

| Metric | Hybrid RRF | Hybrid + TF-IDF-Inspired Reranker |
|---|---:|---:|
| Hit@1 | 75% | 70% |
| Hit@3 | 95% | 90% |
| Hit@5 | 100% | 95% |
| MRR | 0.8542 | 0.8017 |

The TF-IDF-inspired reranker also **performed worse than the frozen Hybrid RRF baseline**.

---

# 17. Representative Reranking Observations

The benchmark exposed cases where lexical scoring was not sufficient to identify the most relevant DevOps document.

### Docker networking

Query:

```text
Docker container has networking problems
```

Expected document:

```text
docker-container-network-error
```

The lexical reranking stage did not consistently place the expected document first.

A more generic Docker document could receive a competitive lexical score because common terms such as:

```text
docker
container
error
```

occur across multiple documents.

This illustrates the limitation of treating term overlap as a direct proxy for semantic relevance.

---

### Kubernetes deployment

Query:

```text
Kubernetes deployment is failing
```

Expected document:

```text
kubernetes-deployment-failure
```

The expected document was pushed as low as rank 5 in the TF-IDF-inspired experiment.

Again, lexical similarity alone did not reliably distinguish closely related DevOps troubleshooting topics.

---

# 18. Overall Experimental Results

The complete set of evaluated configurations is:

| Retrieval Strategy | Hit@1 | Hit@3 | Hit@5 | MRR |
|---|---:|---:|---:|---:|
| Dense, no overlap | 80% | 100% | 100% | 0.9000 |
| BM25, no overlap | 75% | 95% | 100% | 0.8542 |
| Hybrid RRF, no overlap | 75% | 95% | 100% | 0.8542 |
| Dense, 200-character overlap | 80% | 95% | 95% | 0.8500 |
| BM25, 200-character overlap | 65% | 95% | 100% | 0.8042 |
| Hybrid, 200-character overlap | 65% | 95% | 100% | 0.8042 |
| Hybrid + lexical reranker | 70% | 95% | 100% | 0.8292 |
| Hybrid + TF-IDF-inspired reranker | 70% | 90% | 95% | 0.8017 |

---

# 19. Experimental Conclusions

## 19.1 Chunk overlap

The 200-character overlap configuration did not improve retrieval performance.

The measured MRR decreased for all evaluated retrieval configurations.

**Decision:**

```text
Keep:
1000-character chunks
0-character overlap
```

---

## 19.2 BM25

BM25 produced:

```text
Hit@1 = 75%
Hit@3 = 95%
Hit@5 = 100%
MRR   = 0.8542
```

It provides an independent lexical retrieval path that complements semantic retrieval.

---

## 19.3 Hybrid RRF

Hybrid retrieval combines:

```text
Dense semantic retrieval
+
BM25 lexical retrieval
```

using RRF.

The resulting reference configuration achieved:

```text
Hit@1 = 75%
Hit@3 = 95%
Hit@5 = 100%
MRR   = 0.8542
```

This became the frozen reference point for reranking experiments.

Importantly, on this particular benchmark, Hybrid RRF did **not** exceed the dense-only MRR of `0.9000`. It was retained because it provides a complementary retrieval strategy and serves as the project's selected hybrid architecture, not because the benchmark proves a universal improvement over dense retrieval.

---

## 19.4 Lexical reranking

The simple lexical reranker reduced MRR:

```text
0.8542 → 0.8292
```

and reduced Hit@1:

```text
75% → 70%
```

It was therefore rejected as an improvement.

---

## 19.5 TF-IDF-inspired reranking

The TF-IDF-inspired approach reduced performance further:

```text
MRR:
0.8542 → 0.8017
```

and:

```text
Hit@3:
95% → 90%

Hit@5:
100% → 95%
```

It was also rejected as an improvement.

---

# 20. Engineering Interpretation

The experiments demonstrate an important distinction between retrieval and reranking:

```text
Retrieval
→ identifies potentially relevant candidates

Reranking
→ determines the ordering of those candidates
```

The experiments showed that lexical similarity alone was not a reliable proxy for semantic relevance in this DevOps knowledge base.

Common terms such as:

```text
container
service
failed
connection
startup
error
```

can occur across multiple troubleshooting documents.

Therefore:

```text
Lexical similarity
        ≠
Semantic relevance
```

A future reranker should introduce a stronger semantic relevance signal rather than relying only on hand-designed lexical scoring.

---

# 21. Current Stable Architecture

Based on the completed experiments, the selected retrieval architecture is:

```text
Knowledge Documents
       ↓
1000-character Chunking
       ↓
       ┌───────────────────┐
       │                   │
       ▼                   ▼
Dense Retrieval        BM25 Retrieval
       │                   │
       └─────────┬─────────┘
                 ▼
            RRF Fusion
                 ↓
          Candidate Results
                 ↓
       Prompt Construction
                 ↓
                LLM
```

Current configuration:

```text
Chunk size    = 1000 characters
Chunk overlap = 0
Dense retrieval
BM25 retrieval
RRF fusion
```

The tested lexical rerankers are not part of the selected improvement path.

---

# 22. Future Experiment Direction

The next meaningful retrieval experiment would be a **semantic reranker**.

Potential approaches include:

```text
Hybrid RRF
    ↓
Candidate Set
    ↓
Semantic Reranker
    ↓
Final Top-K
```

Possible semantic relevance mechanisms include:

- embedding-based relevance
- a dedicated reranking model
- a cross-encoder
- an LLM-based relevance scorer

Any future approach should be evaluated against the frozen baseline rather than being assumed to improve retrieval.

---

# 23. Experimental Methodology

The following principles were used throughout the experiments:

### Fixed benchmark

The same 20-query benchmark was used for controlled comparison.

### Frozen reference

The Hybrid RRF configuration was frozen before reranking experiments:

```text
75% Hit@1
95% Hit@3
100% Hit@5
0.8542 MRR
```

### One major change at a time

The experiments were separated into:

```text
Dense baseline
      ↓
BM25
      ↓
Hybrid RRF
      ↓
Chunk overlap experiment
      ↓
Lexical reranker
      ↓
TF-IDF-inspired reranker
```

### Evidence-based decisions

A change was not retained simply because it added a commonly used retrieval technique.

If benchmark performance decreased, the experiment was recorded as a negative result and the previous configuration was retained.

### Benchmark limitations

The benchmark contains 20 queries and is therefore suitable for controlled internal comparison, but it is not large enough to support broad claims about RAG performance.

Future evaluation should include additional unseen queries to reduce the risk of benchmark-specific optimization.

---

# 24. Current Experimental Status

## Verified

- Knowledge document loading
- Document chunking
- Dense embeddings
- Vector retrieval
- BM25 retrieval
- Hybrid RRF retrieval
- Retrieval benchmark
- Reranker abstraction
- Lexical reranker tests
- TF-IDF-inspired reranker tests

## Evaluated but Not Retained

- 200-character chunk overlap
- Simple lexical reranking
- TF-IDF-inspired reranking

## Current Reference Configuration

```text
21 knowledge documents
1000-character chunks
0-character overlap
Dense retrieval
BM25 retrieval
RRF fusion

Hit@1 = 75%
Hit@3 = 95%
Hit@5 = 100%
MRR   = 0.8542
```

---

# 25. Final Decision

The experiments provide evidence for retaining the following RAG configuration:

```text
1000-character chunks
        +
Dense Retrieval
        +
BM25 Retrieval
        +
Reciprocal Rank Fusion
```

The experiments do **not** provide evidence that the tested lexical reranking strategies improve retrieval quality on the current benchmark.

The important outcome of this work is therefore not simply that multiple RAG techniques were implemented. The experiments establish a measured baseline and document which approaches improved, maintained, or degraded retrieval performance.

The next meaningful improvement should be evaluated using a semantic reranking approach and compared against the existing benchmark and frozen reference configuration.
