# AI agent selection for “additional code suggestions” (analysis from your pasted ChatGPT discussions)

## Purpose

Your frontend button **“Get AI Recommendations for Additional Suggestions”** is meant to recommend **additional SNOMED-like codes** that fit what’s already present in a user’s *new code system*.

To be useful, suggestions must be:

- **Relevant** to the already present codes/terms
- **Non-duplicative**
- Ideally **grounded in your terminology data** (so codes exist and hierarchy rules are respected)
- Returned in a **strict machine-parseable JSON shape** (because the backend must reliably parse it)

This document analyzes what your pasted discussions recommended and then explains which parts are best for your use case, which parts are risky, and what I’d improve.

---

## 1) What the pasted discussions recommend

### Providers (“free AI agents”)
The discussions propose a set of providers that you can combine by role:

- **Primary LLM:** **Google AI Studio (Gemini API)**  
  Focus: instruction following, structured outputs, long context
- **Embeddings / similarity:** **Cohere** and/or **Hugging Face**  
  Focus: embeddings, similarity search, deduplication support
- **Speed for experiments/batch:** **Groq**
- **Alternative LLM:** **Mistral**
- **Multi-provider fallback:** **OpenRouter**
- **Local free option:** **Ollama** (run models yourself)

### Prompting + orchestration
They strongly emphasize a **pipeline** rather than “one prompt to do everything”, including:

- Prompt templates for:
  - generating structured code suggestions as JSON
  - hierarchy-aware generation (parent constraints)
  - gap analysis (find under-represented areas)
  - deduplication/validation
  - ranking outputs
- An architecture with an **Orchestrator** calling:
  - LLM for generation/ranking
  - embeddings for similarity/dedup
  - rule-based validation logic

---

## 2) What’s good (matches your button’s needs)

### A. The pipeline approach is correct
For your “additional suggestions” workflow, a pipeline like:

**candidate retrieval → dedup/validation → ranking**

is the right mental model. It reduces error propagation and makes results easier to trust.

### B. Structured JSON output is essential
Because your backend expects a specific JSON schema, the insistence on “return ONLY JSON” is the correct direction.

### C. Embeddings are a good tool for dedup and “fit”
Using embeddings to measure similarity and remove semantic near-duplicates is a practical way to keep suggestions from becoming repetitive.

---

## 3) Main weakness in the pasted discussions (important for SNOMED-like codes)

### The risk: letting the LLM invent codes
Your task is not just “create plausible medical text”.

For SNOMED-like code systems, the critical requirement is usually that suggested codes:

- **exist** in your terminology dataset
- comply with the **hierarchy and allowed granularity**

If the pipeline relies on the LLM to “generate new codes” freely (including IDs), you can get:

- invented/non-existent IDs
- IDs that don’t belong to the right part of the hierarchy
- definitions/labels that sound right but don’t match your stored terminology

This is the biggest gap: the discussions suggest prompt/hierarchy constraints, but they do not strongly enforce **grounding** in a deterministic candidate set.

### The risk: hierarchy “correctness” needs deterministic validation
Even if you ask an LLM to “return the correct parent”, LLMs can be wrong.

To make this reliable, your backend should:

- validate parent/level/structure against your stored hierarchy graph
- reject or correct invalid suggestions

---

## 4) Improved approach for your specific “additional codes” button

### Core improvement: Retrieval + grounding before LLM ranking
To avoid hallucinated or invalid codes, I recommend:

1. **Build a grounded candidate set from your own data**
   - Use embeddings to retrieve the top K candidate codes from your terminology store
   - Optionally expand by hierarchy neighbors (siblings/children/granularity neighbors), if you have that graph available
2. **Deterministic filtering**
   - Remove codes already present in the user-selected system
   - Ensure suggested codes exist in your dataset
   - Enforce hierarchy constraints with deterministic rules
3. **Use the LLM for ranking and rationale**
   - Give the LLM only the grounded candidate list (not the entire code system)
   - Ask for strict JSON ranking/reasoning
4. **Validate JSON + validate membership**
   - Validate that the returned JSON matches your schema
   - Validate that every returned code is inside the grounded candidate set
   - If invalid, retry with a stricter instruction like:
     “Return JSON only; use ONLY codes from the provided candidate list.”

This changes the LLM role from “creator of codes” to “ranker of candidates”.

### Why this is better
- You avoid invalid/non-existent codes
- You reduce the burden on prompt engineering
- You can reason about correctness and failure modes

---

## 5) Concrete agent choices for your use case

### Recommended division of labor

1. **Embeddings provider** (candidate retrieval + dedup):
   - **Cohere embeddings** or **Hugging Face embeddings**, or local embeddings if you want self-hosting.
2. **LLM provider** (ranking/rationale and strict JSON):
   - **Gemini** is a strong default because it’s good at instruction-following and structured outputs.
3. **Fallback / resilience**:
   - **OpenRouter** for multi-provider fallback (if it fits your deployment constraints)
   - **Ollama** if you want local fallback and “free forever” style operation

### Suggested minimal configuration
- **Primary LLM:** Gemini (ranking step)
- **Primary embeddings:** Cohere or Hugging Face (retrieval/dedup step)
- **Fallback:** OpenRouter or Ollama

---

## 6) Practical provider notes (URLs you may want)

- Google AI Studio (Gemini): https://aistudio.google.com/  
  (API docs: https://ai.google.dev/gemini-api/docs ; rate limits: https://ai.google.dev/gemini-api/docs/rate-limits )
- Ollama: https://ollama.com/
- OpenRouter: https://openrouter.ai
- Groq: https://groq.com
- Mistral: https://mistral.ai
- Hugging Face: https://huggingface.co/inference-api

---

## 7) How to apply this to your button (prompting guidance)

### Prompt focus
When the LLM ranks/rationalizes, prompts should include:

- the already present codes/terms
- the grounded candidate list (codes + descriptions/definitions)
- strict JSON output schema

### Prompt style
Prefer:

- “Use ONLY the candidate codes provided”
- “Return JSON only”

and rely on backend validation to enforce correctness.

---

## Conclusion

The pasted discussions are directionally correct on:

- using multiple providers by role
- building a pipeline
- returning strict JSON
- using embeddings for similarity/dedup

The key upgrade for your SNOMED-like “additional codes” button is:

**ground suggestions in a deterministic candidate set from your own terminology data, and use the LLM mainly for ranking, not for inventing codes.**

