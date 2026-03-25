# Free AI Agents for “Get AI Recommendations for Additional Suggestions”

This document lists **free, working AI agents** you can use to power the frontend button **“Get AI Recommendations for Additional Suggestions”**. That button calls the backend `/api/ai/recommend` endpoint, which sends a prompt (unmatched terms + matched SNOMED CT codes + context) and expects **JSON** with `recommendations` and `suggestedAdditional` (additional SNOMED codes that fit the code system).

The backend uses **Spring AI** with a `ChatClient`; the AI must return **valid JSON** in the shape your app expects.

---

## 1. Ollama (local, unlimited free)

| | |
|--|--|
| **Website** | https://ollama.com |
| **Docs / API** | https://github.com/ollama/ollama/blob/main/docs/api.md |
| **Get started** | Install locally: `curl -fsSL https://ollama.com/install.sh \| sh` then `ollama pull llama3` |

**Strengths**
- **Truly free**, no usage limits, no API key.
- **Data stays on your machine** (good for sensitive/health data).
- **Spring AI support**: `spring-ai-ollama-spring-boot-starter`; same `ChatClient` interface.
- Works offline once models are pulled.
- Good for structured JSON if you prompt clearly (e.g. “Only output valid JSON”).

**Weaknesses**
- You must run Ollama yourself (local or your server); not a hosted API.
- Needs enough RAM/CPU (e.g. 8GB+ RAM for smaller models).
- Slower than cloud APIs; quality depends on model (e.g. Llama 3, Mistral).

**Relevance for your button**
- Best for **development** and **production** if you can host Ollama (e.g. same server as backend or Docker).
- Set `spring.ai.ollama.base-url` (e.g. `http://localhost:11434`) and use a model that follows instructions well (e.g. `llama3`, `mistral`).

---

## 2. Google Gemini (cloud, free tier)

| | |
|--|--|
| **Website / API keys** | https://aistudio.google.com and https://aistudio.google.com/app/apikey |
| **Docs** | https://ai.google.dev/gemini-api/docs |

**Strengths**
- **Free tier** with daily request limits (e.g. hundreds to thousands/day depending on model; see [rate limits](https://ai.google.dev/gemini-api/docs/rate-limits)).
- No credit card required for free tier.
- **Spring AI support**: `spring-ai-google-ai-spring-boot-starter`; works with your existing `ChatClient` pattern.
- Strong at following instructions and returning JSON; good for “additional suggestions” style tasks.

**Weaknesses**
- Rate limits; not suitable for very high traffic without paid tier.
- Data is processed by Google (consider privacy/compliance).
- Requires Google account and API key.

**Relevance for your button**
- Good **hosted** option for production or demos when you don’t want to run Ollama.
- Configure `spring.ai.google.ai.api-key` and use e.g. `gemini-2.0-flash` or `gemini-2.5-flash` for a balance of speed and quality.

---

## 3. Groq (cloud, free tier)

| | |
|--|--|
| **Website** | https://groq.com |
| **Console / API** | https://console.groq.com |

**Strengths**
- **Free tier** with rate limits (e.g. 30 req/min); very fast inference.
- Hosted; no need to run your own GPU.
- OpenAI-compatible API; can be used with Spring AI’s OpenAI client by pointing the base URL at Groq.

**Weaknesses**
- Spring AI has no dedicated “Groq” starter; you use the OpenAI-compatible endpoint and possibly a different model name.
- Free tier has limits; not “unlimited” like local Ollama.
- Data is processed on Groq’s infrastructure.

**Relevance for your button**
- Useful if you want a **fast, hosted** free option and are okay wiring the OpenAI-compatible endpoint in Spring AI.

---

## 4. Mistral (cloud, free tier)

| | |
|--|--|
| **Website** | https://mistral.ai |
| **API / Console** | https://console.mistral.ai |

**Strengths**
- **Free tier** for smaller models (e.g. Mistral Small).
- Good instruction-following; can return JSON.
- Spring AI: `spring-ai-mistral-ai-spring-boot-starter` (if available for your Spring AI version).

**Weaknesses**
- Free tier is limited (rate and/or daily caps).
- Less documented in Spring AI than OpenAI/Google; may need to check compatibility with Spring AI 1.0.0-M3.

**Relevance for your button**
- Alternative **hosted** option if you prefer Mistral models or hit limits on Gemini/Groq.

---

## 5. Anthropic Claude (trial / low cost)

| | |
|--|--|
| **Website** | https://www.anthropic.com |
| **Console / API** | https://console.anthropic.com |

**Strengths**
- **Trial credits** for new accounts; then pay-per-use.
- **Spring AI support**: `spring-ai-anthropic-spring-boot-starter`.
- Very good at structured output and safe, consistent answers (helpful for clinical/terminology use).

**Weaknesses**
- Not “free forever”; after trial you pay (though often affordable for moderate use).
- Requires account and API key.

**Relevance for your button**
- Good when you want **high quality** and can accept small cost; useful as an optional “premium” backend behind the same button.

---

## 6. OpenRouter (multi-model, some free)

| | |
|--|--|
| **Website** | https://openrouter.ai |
| **Docs** | https://openrouter.ai/docs |

**Strengths**
- **One API** for many models; some models are free (e.g. certain Llama/Gemma).
- OpenAI-compatible API; can be used with Spring AI’s OpenAI client by setting the base URL to OpenRouter.

**Weaknesses**
- Free models may have lower quality or stricter rate limits.
- Extra indirection (OpenRouter → provider); need to pick a model that returns good JSON.

**Relevance for your button**
- Useful for **experimentation** or switching models without changing much code (same OpenAI-compatible client, different base URL and model name).

---

## 7. Hugging Face Inference API (free tier)

| | |
|--|--|
| **Website** | https://huggingface.co |
| **Inference API** | https://huggingface.co/inference-api |

**Strengths**
- **Free tier** for many open-source models.
- Many models (including some with medical/technical focus).
- No dedicated Spring AI starter; use HTTP client or OpenAI-compatible endpoints where available.

**Weaknesses**
- Integration is more custom (no drop-in Spring AI ChatClient).
- Quality and JSON compliance vary a lot by model; you need to test.

**Relevance for your button**
- Good for **trying** specific open-source models; more work to integrate and to get reliable JSON.

---

## Comparison (for your use case)

| Agent        | Free tier        | Spring AI / ease     | JSON / quality | Hosted | Best for                          |
|-------------|------------------|----------------------|----------------|--------|-----------------------------------|
| **Ollama**  | Unlimited        | Native, easy         | Good           | No     | Local/dev & self-hosted prod      |
| **Gemini**  | Generous daily   | Native, easy         | Very good      | Yes    | Hosted prod / demos               |
| **Groq**    | Rate-limited     | Via OpenAI compat    | Good           | Yes    | Fast hosted fallback             |
| **Mistral** | Limited          | Possible / check      | Good           | Yes    | Alternative cloud                 |
| **Claude**  | Trial then paid  | Native                | Very good      | Yes    | High quality, small cost         |
| **OpenRouter** | Some free     | Via OpenAI compat    | Varies         | Yes    | Multi-model experiments          |
| **Hugging Face** | Limited      | Custom                | Varies         | Yes    | Specific open-source models       |

---

## How the button uses the AI

1. User clicks **“Get AI Recommendations for Additional Suggestions”**.
2. Frontend sends **POST** to `/api/ai/recommend` with:
   - `unmatchedTerms`
   - `matchedSnomedIds`
   - `context` (e.g. “Swedish healthcare terminology”)
3. Backend (hybrid or pure AI path) builds a **prompt** asking for:
   - SNOMED recommendations for unmatched terms
   - **Additional complementary SNOMED codes** that fit the code system
4. Backend calls the configured **ChatClient** (e.g. OpenAI, Ollama, Gemini).
5. Response must be **JSON** with `recommendations` and `suggestedAdditional`; backend parses it and returns it to the frontend.

Any free agent above that can return **stable JSON** and is reachable from your backend (local or HTTPS) can power this button. Easiest to start: **Ollama** (local, free) or **Gemini** (hosted, free tier).

---

## Quick links

- Ollama: https://ollama.com  
- Google AI Studio (Gemini API key): https://aistudio.google.com/app/apikey  
- Groq: https://console.groq.com  
- Mistral: https://console.mistral.ai  
- Anthropic: https://console.anthropic.com  
- OpenRouter: https://openrouter.ai  
- Hugging Face: https://huggingface.co/inference-api  
- Spring AI reference: https://docs.spring.io/spring-ai/reference/
