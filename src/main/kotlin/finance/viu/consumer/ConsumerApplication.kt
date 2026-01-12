package finance.viu.consumer

import finance.viu.onvistastoryengine.config.ApplicationConfig
import finance.viu.onvistastoryengine.service.StoryService
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan

/**
 * Sample application demonstrating onvista-story-engine library usage.
 *
 * This application shows how to:
 * 1. Import the library as a Maven dependency
 * 2. Configure the required properties
 * 3. Use the StoryService to generate stories
 */
@SpringBootApplication
@EnableConfigurationProperties(ApplicationConfig::class)
@ComponentScan(basePackages = ["finance.viu.onvistastoryengine", "finance.viu.consumer"])
class ConsumerApplication {

    @Bean
    fun demoRunner(storyService: StoryService): CommandLineRunner = CommandLineRunner { args ->
        if (args.contains("--demo")) {
            println("\n=== OnVista Story Engine Demo ===\n")

            val isin = args.find { it.startsWith("--isin=") }?.substringAfter("=") ?: "DE0007164600"

            println("Fetching stories for ISIN: $isin")
            println("This may take a moment...\n")

            storyService.getStories(isin, skipCache = true)
                .subscribe(
                    { response ->
                        println("Successfully generated ${response.stories.size} stories!")
                        println("Cached: ${response.cached}")
                        println()

                        response.stories.forEachIndexed { index, story ->
                            println("--- Story ${index + 1}: ${story.title} ---")
                            println("Abstract: ${story.abstract}")
                            println("Summary: ${story.summary}")
                            println("Sentiment: ${story.sentiment ?: "N/A"}")
                            println("References: ${story.references.size}")
                            println()
                        }
                    },
                    { error ->
                        System.err.println("Error generating stories: ${error.message}")
                    },
                    {
                        println("=== Demo Complete ===")
                    }
                )

            // Keep alive for async completion
            Thread.sleep(60000)
        }
    }
}

fun main(args: Array<String>) {
    runApplication<ConsumerApplication>(*args)
}
