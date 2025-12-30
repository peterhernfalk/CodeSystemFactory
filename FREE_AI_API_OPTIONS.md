# Free AI API Options for Code System Factory

This document outlines free AI API alternatives that can be used for AI recommendations in the Code System Factory application. The application currently uses OpenAI (GPT-4o-mini) via Spring AI framework.

## Current Setup

- **Framework**: Spring AI 1.0.0-M3
- **Current Provider**: OpenAI (GPT-4o-mini)
- **Use Case**: Medical terminology recommendations for SNOMED CT code matching
- **Requirements**: JSON-structured responses, medical domain knowledge, Swedish language support

## Recommended Free AI API Options

### 1. **Ollama (Local/Free) - ⭐ TOP RECOMMENDATION**

**Overview**: Ollama is a free, open-source tool that runs large language models locally on your machine or server. It's completely free with no usage limits.

**Free Tier**: 
- ✅ Completely free
- ✅ No API rate limits
- ✅ No internet required (runs locally)
- ✅ Full privacy (data never leaves your server)

**Spring AI Support**: 
- ✅ Native support via `spring-ai-ollama-spring-boot-starter`
- ✅ Compatible with Spring AI ChatClient interface

**Models Available**:
- `llama3` (8B, 70B) - General purpose
- `mistral` (7B) - Good for structured outputs
- `codellama` - Code-focused
- `phi` - Microsoft's efficient model
- Many others available

**Pros**:
- Completely free with no limits
- Full data privacy
- Works offline
- No API keys needed
- Good for structured JSON outputs
- Easy to deploy alongside your backend

**Cons**:
- Requires local installation and sufficient hardware (RAM/CPU)
- May be slower than cloud APIs
- Model quality may vary compared to GPT-4
- Requires server resources

**Setup Complexity**: Medium (requires Docker or local installation)

**Best For**: 
- Development and testing
- Production deployments where privacy is critical
- Applications with predictable usage patterns
- Cost-sensitive deployments

**Integration Steps**:
1. Install Ollama on server: `curl -fsSL https://ollama.com/install.sh | sh`
2. Pull a model: `ollama pull llama3`
3. Add dependency: `spring-ai-ollama-spring-boot-starter`
4. Configure in `application.yml`:
   ```yaml
   spring:
     ai:
       ollama:
         base-url: http://localhost:11434
         chat:
           options:
             model: llama3
   ```

---

### 2. **Google Gemini API - ⭐ RECOMMENDED**

**Overview**: Google's Gemini API offers a generous free tier with access to their latest models.

**Free Tier**:
- ✅ 15 requests per minute (RPM)
- ✅ 1,500 requests per day (RPD)
- ✅ 1 million tokens per minute
- ✅ No credit card required initially

**Spring AI Support**:
- ✅ Native support via `spring-ai-google-ai-spring-boot-starter`
- ✅ Compatible with Spring AI ChatClient interface

**Models Available**:
- `gemini-pro` - General purpose
- `gemini-pro-vision` - Multimodal

**Pros**:
- Generous free tier
- High-quality models
- Good JSON structure following
- Fast response times
- Reliable uptime
- Good documentation

**Cons**:
- Rate limits on free tier
- Requires Google Cloud account
- May need credit card for higher limits
- Data processed by Google

**Setup Complexity**: Low (just API key)

**Best For**:
- Production applications with moderate usage
- Applications needing high-quality responses
- When you need cloud-based reliability

**Integration Steps**:
1. Get API key from [Google AI Studio](https://makersuite.google.com/app/apikey)
2. Add dependency: `spring-ai-google-ai-spring-boot-starter`
3. Configure in `application.yml`:
   ```yaml
   spring:
     ai:
       google:
         ai:
           api-key: ${GOOGLE_AI_API_KEY}
         chat:
           options:
             model: gemini-pro
   ```

---

### 3. **Anthropic Claude API**

**Overview**: Anthropic's Claude models are known for safety and helpfulness, with a free tier available.

**Free Tier**:
- ✅ $5 free credits for new users
- ✅ After credits: Pay-as-you-go (very affordable)
- ✅ No credit card required for initial credits

**Spring AI Support**:
- ✅ Native support via `spring-ai-anthropic-spring-boot-starter`
- ✅ Compatible with Spring AI ChatClient interface

**Models Available**:
- `claude-3-haiku` - Fast and affordable
- `claude-3-sonnet` - Balanced
- `claude-3-opus` - Most capable

**Pros**:
- Excellent for structured outputs
- Very safe and helpful responses
- Good medical domain understanding
- Affordable pricing after free credits
- High quality responses

**Cons**:
- Limited free tier (mostly trial credits)
- Requires credit card for continued use
- Slightly more expensive than some alternatives

**Setup Complexity**: Low

**Best For**:
- Applications requiring high-quality, safe responses
- When you can accept minimal costs after free tier
- Medical/healthcare applications

**Integration Steps**:
1. Get API key from [Anthropic Console](https://console.anthropic.com/)
2. Add dependency: `spring-ai-anthropic-spring-boot-starter`
3. Configure in `application.yml`:
   ```yaml
   spring:
     ai:
       anthropic:
         api-key: ${ANTHROPIC_API_KEY}
         chat:
           options:
             model: claude-3-haiku
   ```

---

### 4. **Hugging Face Inference API**

**Overview**: Hugging Face provides access to thousands of open-source models through their Inference API.

**Free Tier**:
- ✅ Limited requests per day (varies by model)
- ✅ Some models completely free
- ✅ No credit card required

**Spring AI Support**:
- ⚠️ Limited native support (may need custom implementation)
- ⚠️ May require using HTTP client directly

**Models Available**:
- Thousands of models
- Medical-specific models available
- Multilingual models

**Pros**:
- Access to many specialized models
- Some models are completely free
- Medical domain models available
- Open source models

**Cons**:
- Limited Spring AI integration
- Rate limits vary by model
- Quality varies significantly
- May require more custom code

**Setup Complexity**: Medium-High (less Spring AI integration)

**Best For**:
- Experimentation with different models
- When you need specialized domain models
- Research and development

---

### 5. **OpenRouter**

**Overview**: OpenRouter is a unified API that provides access to multiple AI models, including some free options.

**Free Tier**:
- ✅ Some models completely free
- ✅ Daily credits for paid models
- ✅ No credit card required for free models

**Spring AI Support**:
- ⚠️ Limited (may need to use OpenAI-compatible endpoint)
- ⚠️ May require custom configuration

**Models Available**:
- Free: `google/gemini-pro`, `meta-llama/llama-3-8b`, etc.
- Paid: GPT-4, Claude, etc.

**Pros**:
- Access to multiple models through one API
- Some free models available
- Easy model switching
- Good for A/B testing

**Cons**:
- Less direct Spring AI support
- Free models may have lower quality
- Rate limits on free tier
- Additional abstraction layer

**Setup Complexity**: Medium

**Best For**:
- Testing multiple models
- When you want flexibility to switch models
- A/B testing different providers

---

## Comparison Table

| Provider | Free Tier | Spring AI Support | Setup | Quality | Privacy | Best Use Case |
|----------|-----------|------------------|-------|---------|---------|---------------|
| **Ollama** | ✅ Unlimited | ✅ Native | Medium | Good | ✅ Full | Local/Private |
| **Google Gemini** | ✅ 1,500/day | ✅ Native | Low | Excellent | ⚠️ Cloud | Production |
| **Anthropic Claude** | ⚠️ Trial credits | ✅ Native | Low | Excellent | ⚠️ Cloud | High Quality |
| **Hugging Face** | ⚠️ Limited | ⚠️ Limited | High | Varies | ⚠️ Cloud | Experimentation |
| **OpenRouter** | ⚠️ Some free | ⚠️ Limited | Medium | Varies | ⚠️ Cloud | Multi-model |

## Recommendations by Scenario

### **For Development & Testing**
1. **Ollama** - Best choice for unlimited free testing
2. **Google Gemini** - Good for testing cloud integration

### **For Production (Free/Low Cost)**
1. **Ollama** - If you can run it on your server
2. **Google Gemini** - Best cloud option with generous free tier
3. **Anthropic Claude** - If you can accept minimal costs

### **For Maximum Privacy**
1. **Ollama** - Only option that keeps data completely local

### **For Ease of Integration**
1. **Google Gemini** - Easiest cloud option with Spring AI
2. **Anthropic Claude** - Also very easy
3. **Ollama** - Easy once installed

## Implementation Recommendations

### **Recommended Approach: Multi-Provider Support**

The application can be enhanced to support multiple AI providers with a simple configuration switch:

```yaml
spring:
  ai:
    provider: ${AI_PROVIDER:ollama}  # ollama, google, anthropic, openai
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      chat:
        options:
          model: ${OLLAMA_MODEL:llama3}
    google:
      ai:
        api-key: ${GOOGLE_AI_API_KEY:}
      chat:
        options:
          model: gemini-pro
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:}
      chat:
        options:
          model: claude-3-haiku
    openai:
      api-key: ${OPENAI_API_KEY:}
      chat:
        options:
        model: gpt-4o-mini
```

### **Fallback Strategy**

Implement a fallback chain:
1. Primary: Ollama (local, free)
2. Fallback: Google Gemini (cloud, free tier)
3. Last resort: Mock responses (when all fail)

## Cost Analysis

| Provider | Free Tier | Cost After Free Tier | Estimated Monthly Cost (1000 requests) |
|----------|-----------|---------------------|--------------------------------------|
| Ollama | Unlimited | $0 | $0 (server costs only) |
| Google Gemini | 1,500/day | ~$0.001/1K tokens | ~$1-5 |
| Anthropic Claude | $5 credits | ~$0.25/1M tokens | ~$5-10 |
| OpenAI GPT-4o-mini | Very limited | ~$0.15/1M tokens | ~$10-20 |
| Hugging Face | Limited | Varies | $0-5 |

## Next Steps

1. **For Immediate Free Solution**: Set up Ollama locally or on your server
2. **For Cloud Solution**: Get Google Gemini API key and configure
3. **For Flexibility**: Implement multi-provider support with configuration switching

## Additional Resources

- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [Ollama Documentation](https://ollama.ai/docs)
- [Google AI Studio](https://makersuite.google.com/app/apikey)
- [Anthropic Console](https://console.anthropic.com/)
- [Hugging Face Models](https://huggingface.co/models)

---

**Last Updated**: 2024
**Application Version**: 2.0.0

