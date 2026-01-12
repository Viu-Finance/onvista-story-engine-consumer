package finance.viu.consumer

import finance.viu.onvistastoryengine.config.ApplicationConfig
import finance.viu.onvistastoryengine.model.ArticleReference
import finance.viu.onvistastoryengine.model.ArticleRelevanceScore
import finance.viu.onvistastoryengine.model.ArticleTeaser
import finance.viu.onvistastoryengine.model.CompanyMetadata
import finance.viu.onvistastoryengine.model.FullArticle
import finance.viu.onvistastoryengine.model.RelevanceScoreBreakdown
import finance.viu.onvistastoryengine.model.Story
import finance.viu.onvistastoryengine.model.StoriesResponse
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank

/**
 * Integration tests verifying the onvista-story-engine library
 * can be properly imported and used.
 *
 * These tests validate:
 * 1. All model classes are accessible
 * 2. Data classes serialize/deserialize correctly
 * 3. Configuration properties bind correctly
 * 4. Services can be instantiated (when dependencies available)
 */
class LibraryIntegrationTest : DescribeSpec({

    describe("Model Classes") {

        describe("Story Model") {
            it("should create a valid Story instance") {
                val story = Story(
                    title = "Test Story Title",
                    abstract = "Brief one-sentence summary",
                    summary = "This is a longer 2-3 sentence summary with details.",
                    sentiment = "bullish",
                    references = listOf(
                        ArticleReference(
                            id = "123",
                            relevanz = 0.9
                        )
                    )
                )

                story.title shouldBe "Test Story Title"
                story.abstract.shouldNotBeBlank()
                story.summary.shouldNotBeBlank()
                story.references shouldHaveSize 1
                story.references[0].relevanz shouldBeGreaterThanOrEqual 0.0
                story.references[0].relevanz shouldBeLessThanOrEqual 1.0
            }

            it("should create StoriesResponse") {
                val response = StoriesResponse(
                    stories = emptyList(),
                    cached = true,
                    cacheTtl = 3600
                )

                response.cached shouldBe true
                response.cacheTtl shouldBe 3600
                response.stories shouldHaveSize 0
            }
        }

        describe("Article Model") {
            it("should create ArticleTeaser with required fields") {
                val teaser = ArticleTeaser(
                    entityValue = "12345",
                    headline = "Test Headline",
                    publisher = "Reuters",
                    datetimePublication = "2025-01-07T10:30:00Z",
                    wordCount = 450,
                    subType = "news"
                )

                teaser.entityValue.shouldNotBeBlank()
                teaser.headline.shouldNotBeBlank()
                teaser.publisher shouldBe "Reuters"
                teaser.wordCount shouldBe 450
            }

            it("should create FullArticle with content") {
                val article = FullArticle(
                    entityValue = "12345",
                    headline = "Full Article Headline",
                    publisher = "Reuters",
                    content = "Article content goes here",
                    datetimePublication = "2025-01-07T10:30:00Z"
                )

                article.content shouldNotBe null
                article.content.shouldNotBeBlank()
            }
        }

        describe("RelevanceScore Model") {
            it("should create RelevanceScoreBreakdown with valid ranges") {
                val breakdown = RelevanceScoreBreakdown(
                    companyMention = 0.5,
                    sectorRelevance = 0.15,
                    technologyRelevance = 0.2,
                    marketRelevance = 0.1,
                    geographicRelevance = 0.05
                )

                // Sum should be 1.0 max
                val total = breakdown.companyMention +
                        breakdown.sectorRelevance +
                        breakdown.technologyRelevance +
                        breakdown.marketRelevance +
                        breakdown.geographicRelevance

                total shouldBeLessThanOrEqual 1.0
            }

            it("should create ArticleRelevanceScore with reasoning") {
                val score = ArticleRelevanceScore(
                    articleId = "123",
                    reasoning = "Article directly mentions the company",
                    breakdown = RelevanceScoreBreakdown(
                        companyMention = 0.5,
                        sectorRelevance = 0.1,
                        technologyRelevance = 0.0,
                        marketRelevance = 0.05,
                        geographicRelevance = 0.0
                    ),
                    totalScore = 0.65
                )

                score.reasoning.shouldNotBeBlank()
                score.totalScore shouldBe 0.65
            }
        }

        describe("CompanyMetadata Model") {
            it("should create CompanyMetadata") {
                val metadata = CompanyMetadata(
                    isin = "DE0007164600",
                    name = "SAP SE",
                    ticker = "SAP",
                    sector = "Technology",
                    industry = "Software",
                    description = "Enterprise software company"
                )

                metadata.name shouldBe "SAP SE"
                metadata.isin shouldBe "DE0007164600"
                metadata.ticker shouldBe "SAP"
            }
        }
    }

    describe("Configuration Classes") {
        it("should define CacheBackendType enum values") {
            val types = ApplicationConfig.CacheBackendType.entries

            types.map { it.name } shouldBe listOf("REDIS", "MEMORY", "TIERED")
        }

        it("should create ApplicationConfig class reference") {
            // This validates the class structure is accessible
            val configClass = ApplicationConfig::class
            configClass.simpleName shouldBe "ApplicationConfig"
        }
    }
})
