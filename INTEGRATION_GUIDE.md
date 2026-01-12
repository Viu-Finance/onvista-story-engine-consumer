# OnVista Story Engine - Integration Guide

This guide shows how to integrate the OnVista Story Engine library into your Spring Boot application.

## Table of Contents

- [Quick Start](#quick-start)
- [Sample Response](#sample-response)
- [Integration Patterns](#integration-patterns)
- [Data Models Reference](#data-models-reference)
- [Configuration Guide](#configuration-guide)
- [Error Handling](#error-handling)
- [API Reference](#api-reference)
- [FAQ](#faq)

---

## Quick Start

### 1. Add Maven Dependency

```xml
<dependency>
    <groupId>finance.viu</groupId>
    <artifactId>onvista-story-engine</artifactId>
    <version>0.0.1_alpha</version>
</dependency>
```

### 2. Configure Application

```yaml
# application.yml
app:
  openai-api-key: ${OPENAI_API_KEY}
  cache-backend: memory
  relevance-filter-enabled: false  # Pre-filter articles before clustering (adds ~10 API calls, reduces main LLM tokens)
```

### 3. Inject and Use StoryService

```kotlin
import finance.viu.onvistastoryengine.service.StoryService
import org.springframework.stereotype.Service

@Service
class MyFinancialService(
    private val storyService: StoryService
) {
    fun getStories(isin: String) =
        storyService.getStories(isin)
}
```

---

## Sample Response

### Input
```kotlin
storyService.getStories("US67066G1040") // NVIDIA
```

### Output (Shortened)

```json
{
  "stories": [
    {
      "title": "Basecamp Research veröffentlicht Gen-Insertion-KI-Modelle und arbeitet mit NVIDIA zusammen",
      "abstract": "Die Zusammenarbeit von Basecamp Research mit NVIDIA beim Training neuer Gen-Insertion-Modelle und eine NVentures-Investition unterstreichen NVIDIAs Position als bevorzugte KI-Computing-Plattform in Life Sciences.",
      "summary": "Basecamp Research bringt nach eigener Aussage die ersten KI-Modelle für programmierbare Gen-Insertion auf den Markt und betont, dass die Modelle in Zusammenarbeit mit NVIDIA trainiert wurden. Zusätzlich sicherte sich das Unternehmen eine Investition von NVentures im Rahmen einer Pre-Series-C-Runde.",
      "sentiment": "bullish",
      "references": [
        {
          "id": "26467927",
          "relevanz": 1.0,
          "reasoning": "Der Artikel nennt NVIDIA explizit als Trainingspartner für neue KI-Modelle zur programmierten Gen-Insertion. Das ist direkt relevant für NVIDIAs KI-/Rechenzentrumsgeschäft..."
        }
      ]
    },
    {
      "title": "EU bewegt sich in Richtung Mindestpreis-Regime für China-Elektroautos",
      "abstract": "Neue EU-Leitlinien zu Mindestpreisen für China-EVs und schwächere Absatzzahlen großer OEMs erhöhen die Unsicherheit im Automotive-Ökosystem.",
      "sentiment": "neutral",
      "references": [
        {
          "id": "26467907",
          "relevanz": 0.75,
          "reasoning": "Der Artikel erwähnt NVIDIA nicht direkt, behandelt aber EU‑Regelungen zu Elektroautos aus China. Das betrifft relevante Kunden und Märkte für NVIDIAs Automotive‑Plattformen..."
        },
        {
          "id": "26467873",
          "relevanz": 0.6
        }
      ]
    }
  ],
  "cached": false,
  "cache_ttl": 31536000
}
```

**Key Features:**
- **Title** - Neutral, journalistic headline describing the theme/event
- **Abstract** - One-sentence summary with company context
- **Summary** - 2-3 sentences with key details and investment implications
- **Sentiment** - `"bullish"`, `"bearish"`, or `"neutral"`
- **References** - List of source articles with relevance scores (0.0-1.0)
- **Reasoning** - Optional explanation of article relevance (when `includeReasoning=true`)

---

## Integration Patterns

### Pattern 1: Synchronous REST Endpoint

```kotlin
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class NewsController(
    private val storyService: StoryService
) {
    @GetMapping("/news/{isin}")
    fun getNews(@PathVariable isin: String): StoriesResponse {
        return storyService.getStories(isin)
            .block() // Block for synchronous response
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
    }
}
```

### Pattern 2: Async with WebFlux

```kotlin
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api")
class NewsController(
    private val storyService: StoryService
) {
    @GetMapping("/news/{isin}")
    fun getNews(@PathVariable isin: String): Mono<StoriesResponse> {
        return storyService.getStories(isin)
    }
}
```

### Pattern 3: Stream to WebSocket

```kotlin
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux

@Component
class NewsStreamer(
    private val storyService: StoryService
) {
    fun streamStories(isin: String): Flux<Story> {
        return storyService.getStories(isin)
            .flatMapMany { response ->
                Flux.fromIterable(response.stories)
            }
    }
}
```

### Pattern 4: Batch Processing Multiple ISINs

```kotlin
import reactor.core.publisher.Flux

@Service
class PortfolioNewsService(
    private val storyService: StoryService
) {
    fun getPortfolioNews(isins: List<String>): Mono<Map<String, StoriesResponse>> {
        return Flux.fromIterable(isins)
            .flatMap { isin ->
                storyService.getStories(isin)
                    .map { response -> isin to response }
            }
            .collectMap({ it.first }, { it.second })
    }
}
```

### Pattern 5: Cached with Fallback

```kotlin
@Service
class ResilientNewsService(
    private val storyService: StoryService
) {
    fun getStoriesSafe(isin: String): Mono<StoriesResponse> {
        return storyService.getStories(isin)
            .timeout(Duration.ofSeconds(30))
            .onErrorResume { error ->
                logger.warn("Failed to fetch stories for $isin", error)
                Mono.just(StoriesResponse(
                    stories = emptyList(),
                    cached = false
                ))
            }
    }
}
```

### Pattern 6: Force Fresh Data (Skip Cache)

```kotlin
@Service
class RefreshService(
    private val storyService: StoryService
) {
    fun refreshStories(isin: String): Mono<StoriesResponse> {
        return storyService.getStories(isin, skipCache = true)
    }
}
```

---

## Data Models Reference

### StoriesResponse

Top-level response object containing all stories for an ISIN.

```kotlin
data class StoriesResponse(
    val stories: List<Story>,        // List of clustered stories
    val cached: Boolean = false,     // Whether served from cache
    val cacheTtl: Int? = null        // Remaining cache TTL in seconds
)
```

### Story

A single clustered news story aggregating multiple related articles.

```kotlin
data class Story(
    val title: String,                        // Neutral headline (e.g., "Apple kündigt neues iPhone an")
    val abstract: String,                     // One-sentence summary with company context
    val summary: String,                      // 2-3 sentences with details and investment implications
    val sentiment: String? = null,            // "bullish", "bearish", or "neutral"
    val references: List<ArticleReference>    // Source articles
)
```

**Field Descriptions:**
- **title**: Neutral, journalistic headline without relevance commentary
- **abstract**: One-sentence summary linking the story to the company
- **summary**: Detailed explanation (2-3 sentences) with investment implications
- **sentiment**: Market sentiment indicator (`null` if not determinable)
- **references**: List of source articles with relevance scores

### ArticleReference

Reference to a source article within a story.

```kotlin
data class ArticleReference(
    val id: String,              // OnVista article ID (entityValue)
    val relevanz: Double,        // Relevance score (0.0 - 1.0)
    val reasoning: String? = null // Optional: explanation of relevance (when includeReasoning=true)
)
```

**Relevance Score Ranges:**
- `0.9 - 1.0` - Direct company mention with high impact
- `0.7 - 0.9` - Strong sector/technology relevance
- `0.5 - 0.7` - Moderate market/geographic relevance
- `0.3 - 0.5` - Indirect relevance (competitors, supply chain)
- `< 0.3` - Filtered out by default (adjust with `relevance-filter-threshold`)

---

## Configuration Guide

### Essential Configuration

```yaml
app:
  # Required: OpenAI API key for LLM calls
  openai-api-key: ${OPENAI_API_KEY}

  # Optional: Override default model (default: gpt-4o-2024-11-20)
  openai-model: gpt-4o-2024-11-20
```

### Cache Configuration

Choose your caching strategy based on deployment model:

```yaml
app:
  cache-enabled: true
  cache-backend: memory  # Options: memory, redis, tiered

  # For memory cache
  memory-cache-max-size: 1000
  memory-cache-ttl-seconds: 31536000  # 1 year (content-hash verified)

  # For Redis cache
  redis-host: localhost
  redis-port: 6379
  redis-db: 0
  redis-ttl-seconds: 31536000

  # For tiered cache (L1 memory + L2 Redis)
  # Uses both memory-cache-* and redis-* settings
```

**Cache Backend Comparison:**

| Backend | Best For | Pros | Cons |
|---------|----------|------|------|
| `memory` | Single-instance apps, development | Fast, simple, no dependencies | Lost on restart, not shared |
| `redis` | Multi-instance production | Shared cache, persistent | Requires Redis, network latency |
| `tiered` | High-traffic production | Best performance, shared | Most complex setup |

### Relevance Filter Configuration

Enable LLM-based pre-filtering to reduce noise and input tokens for the clustering step:

```yaml
app:
  relevance-filter-enabled: false  # Default: disabled (recommended for most use cases)
  relevance-filter-threshold: 0.3  # Min score to include (0.0 - 1.0)
  relevance-filter-model: gpt-5-mini  # Smaller model for filtering
  relevance-filter-concurrency: 10  # Parallel filtering requests
```

**What pre-filtering does:**
- **Reduces noise**: Removes irrelevant articles before clustering/synthesis
- **Reduces tokens**: Clustering+synthesis LLM processes fewer articles (lower cost on expensive model)
- **Trade-off**: Adds ~10 cheap API calls (gpt-5-mini) to save tokens on 1 expensive call (gpt-5.2)

**Note**: The main LLM does clustering + synthesis in one step - it does NOT filter. Pre-filtering prevents "garbage in, garbage out" by removing noise upfront.

**Should you enable relevance filtering?**

| Scenario | Recommendation | Reason |
|----------|----------------|--------|
| High-volume production | Disabled | Saves ~10x API calls; acceptable if articles are mostly relevant |
| Quality-critical research | Enabled (threshold: 0.4) | Maximum precision, removes noise before clustering |
| Development/testing | Disabled | Faster iteration, lower costs |
| Large-cap stocks | Disabled | Articles usually high-quality, less noise to filter |
| Small-cap stocks | Enabled (threshold: 0.2) | Helps find weak signals, filters generic market noise |

### Article Fetching Configuration

```yaml
app:
  max-articles-per-request: 100   # Max articles to fetch from OnVista (1-500)
  article-content-max-chars: 1500 # Truncate article content (saves LLM tokens)
```

### Complete Configuration Reference

| Property | Default | Description |
|----------|---------|-------------|
| `app.openai-api-key` | *(required)* | OpenAI API key |
| `app.openai-model` | `gpt-4o-2024-11-20` | LLM model for story clustering |
| `app.openai-temperature` | `0.3` | LLM temperature (0.0-1.0) |
| `app.cache-enabled` | `true` | Enable/disable caching |
| `app.cache-backend` | `memory` | Cache backend: `memory`, `redis`, or `tiered` |
| `app.memory-cache-max-size` | `1000` | Max entries in memory cache |
| `app.memory-cache-ttl-seconds` | `31536000` | Memory cache TTL (1 year, content-hash verified) |
| `app.redis-host` | `localhost` | Redis server host |
| `app.redis-port` | `6379` | Redis server port |
| `app.redis-db` | `0` | Redis database number |
| `app.redis-ttl-seconds` | `31536000` | Redis cache TTL (1 year, content-hash verified) |
| `app.relevance-filter-enabled` | `false` | Enable LLM pre-filtering |
| `app.relevance-filter-threshold` | `0.3` | Min relevance score (0.0-1.0) |
| `app.relevance-filter-model` | `gpt-4o-mini` | LLM model for filtering |
| `app.relevance-filter-concurrency` | `10` | Parallel filtering requests |
| `app.max-articles-per-request` | `100` | Max articles to fetch (1-500) |
| `app.article-content-max-chars` | `1500` | Article content truncation length |

---

## Error Handling

### Common Scenarios

The library is designed to handle errors gracefully:

```kotlin
storyService.getStories(isin)
    .subscribe(
        { response -> /* Success */ },
        { error ->
            when (error) {
                is IllegalArgumentException -> {
                    // Invalid ISIN format
                    logger.error("Invalid ISIN: ${error.message}")
                }
                is TimeoutException -> {
                    // LLM request timed out
                    logger.warn("Story generation timeout for $isin")
                }
                is WebClientResponseException -> {
                    // OnVista API error
                    logger.error("OnVista API error: ${error.statusCode}")
                }
                else -> {
                    // Unknown error
                    logger.error("Unexpected error", error)
                }
            }
        }
    )
```

### Handling Empty Results

```kotlin
storyService.getStories(isin)
    .map { response ->
        if (response.stories.isEmpty()) {
            logger.info("No relevant stories found for $isin")
            // Return default or fetch from alternative source
        }
        response
    }
```

### Retry Strategy

```kotlin
import reactor.util.retry.Retry
import java.time.Duration

storyService.getStories(isin)
    .retryWhen(
        Retry.backoff(3, Duration.ofSeconds(2))
            .filter { error ->
                // Only retry on transient errors
                error is TimeoutException ||
                error is WebClientResponseException.ServiceUnavailable
            }
    )
```

### Circuit Breaker Pattern

```kotlin
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator

@Service
class ResilientNewsService(
    private val storyService: StoryService,
    private val circuitBreaker: CircuitBreaker
) {
    fun getStories(isin: String): Mono<StoriesResponse> {
        return storyService.getStories(isin)
            .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
    }
}
```

---

## API Reference

### StoryService

Main service interface for fetching clustered stories.

#### `getStories(isin: String, skipCache: Boolean = false): Mono<StoriesResponse>`

Fetches and clusters news stories for the given ISIN.

**Parameters:**
- `isin` - ISIN identifier (e.g., `"US67066G1040"`)
- `skipCache` - Force fresh fetch, bypass cache (default: `false`)

**Returns:** `Mono<StoriesResponse>` - Reactive stream with clustered stories

**Example:**
```kotlin
storyService.getStories("DE0007164600") // Use cache if available
storyService.getStories("DE0007164600", skipCache = true) // Force refresh
```

#### `getStories(isin: String, includeReasoning: Boolean): Mono<StoriesResponse>`

Fetches stories with optional relevance reasoning in references.

**Parameters:**
- `isin` - ISIN identifier
- `includeReasoning` - Include explanation in `ArticleReference.reasoning` (default: `false`)

**Returns:** `Mono<StoriesResponse>` - Reactive stream with clustered stories

**Example:**
```kotlin
storyService.getStories("US67066G1040", includeReasoning = true)
```

**Note:** Including reasoning increases response size but provides transparency on why articles were selected.

---

## FAQ

### How much does it cost per request?

**Without relevance filtering (recommended):**
- ~$0.02-0.05 per ISIN (1 LLM call for story clustering)
- Scales with number of articles and story complexity

**With relevance filtering enabled:**
- ~$0.20-0.50 per ISIN (10-50 parallel LLM calls for article scoring + clustering)
- 10x more expensive, but higher precision

**Cost optimization:**
- Enable caching (reduces repeat requests)
- Use `max-articles-per-request: 50` for lower costs
- Disable `relevance-filter-enabled` for most use cases

### How long does a request take?

**Typical latency:**
- **Cache hit:** 5-50ms (depends on cache backend)
- **Cache miss (no filtering):** 3-8 seconds
- **Cache miss (with filtering):** 8-15 seconds

**Breakdown:**
1. OnVista API fetch: 1-2s
2. Company metadata fetch: 0.5-1s
3. Relevance filtering (optional): 3-5s (parallel)
4. Story clustering: 2-4s

### What happens if no articles are found?

The service returns an empty stories list:

```json
{
  "stories": [],
  "cached": false,
  "cache_ttl": null
}
```

This can happen if:
- ISIN has no recent news on OnVista
- All articles were filtered out by relevance threshold
- Invalid/unknown ISIN

### Can I use this without Spring Boot?

Yes, but you'll need to manually instantiate dependencies:

```kotlin
val config = ApplicationConfig(
    openaiApiKey = "sk-...",
    openaiModel = "gpt-4o-2024-11-20",
    cacheBackend = ApplicationConfig.CacheBackendType.MEMORY
)

val cacheService = MemoryCacheService(config)
val onVistaClient = OnVistaClient()
val companyMetadataClient = CompanyMetadataClient()
val llmClient = LlmClient(config)

val storyService = StoryService(
    config = config,
    cacheService = cacheService,
    onVistaClient = onVistaClient,
    companyMetadataClient = companyMetadataClient,
    llmClient = llmClient
)

// Use the service
val stories = storyService.getStories("US67066G1040").block()
```

### How do I invalidate the cache?

**Option 1: Skip cache for single request**
```kotlin
storyService.getStories(isin, skipCache = true)
```

**Option 2: Clear cache via CacheService**
```kotlin
@Service
class CacheManager(
    private val cacheService: CacheService
) {
    fun clearCache(isin: String) {
        cacheService.invalidate(isin).subscribe()
    }
}
```

**Option 3: Restart application (memory cache only)**

### Can I customize the story clustering prompt?

Not directly via configuration. The LLM prompts are embedded in `LlmClient` and `StoryService`.

To customize:
1. Fork the library
2. Modify prompts in `src/main/kotlin/finance/viu/onvistastoryengine/client/LlmClient.kt`
3. Rebuild and use your custom version

Alternatively, post-process the stories:
```kotlin
storyService.getStories(isin)
    .map { response ->
        response.copy(
            stories = response.stories.map { story ->
                // Customize story fields
                story.copy(title = customizeTitle(story.title))
            }
        )
    }
```

### Does this work with ISINs from all countries?

Yes, the library accepts any valid ISIN format. However:

- **OnVista coverage:** Better for German/European stocks, limited for some international markets
- **Article language:** Most articles are in German (may affect LLM story quality for non-German companies)
- **Company metadata:** Best coverage for publicly traded companies with data in holistic.capital API

Test coverage for your target ISINs during evaluation.

### How do I get access to the Maven repository?

Contact Rize Capital to request access:

1. Provide your GitHub username
2. Receive a Personal Access Token (PAT) with `read:packages` scope
3. Configure Maven (see [README.md](./README.md#setup))

### Can I run this in production?

Yes, the library is production-ready:

- ✅ Reactive architecture (non-blocking I/O)
- ✅ Built-in caching with cache stampede protection
- ✅ Comprehensive error handling
- ✅ Structured logging
- ✅ Tested with integration tests

**Production checklist:**
- [ ] Use Redis or tiered cache (not memory)
- [ ] Configure appropriate cache TTL
- [ ] Set up monitoring (cache hit rate, latency, error rate)
- [ ] Implement retry/circuit breaker patterns
- [ ] Budget for OpenAI API costs
- [ ] Test with your target ISINs

---

## Support

For questions, issues, or feature requests, contact the Rize Capital team.
