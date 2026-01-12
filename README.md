# OnVista Story Engine - Sample Consumer

This sample project demonstrates how to consume the `onvista-story-engine` library as a Maven dependency.

## What This Library Does

**Input:** ISIN (e.g., `"US67066G1040"` for NVIDIA)
**Output:** 3-7 clustered news stories with titles, summaries, sentiment analysis, and article references

The OnVista Story Engine:
- Fetches financial news from OnVista API for any ISIN
- Scores article relevance using LLM-based filtering (optional, parallel execution)
- Clusters related articles into coherent narrative stories
- Returns structured data ready for display in your application
- Includes built-in caching (Redis or in-memory) to reduce API costs

**Example Use Cases:**
- Portfolio news aggregation in wealth management apps
- Real-time market intelligence dashboards
- Automated investment research platforms
- Financial chatbots with contextual news insights

See [INTEGRATION_GUIDE.md](./INTEGRATION_GUIDE.md) for detailed integration patterns and API reference.

## Prerequisites

1. **Java 24** - Required for compilation
2. **Maven 3.9+** - For building
3. **GitHub PAT** - Personal Access Token with `read:packages` scope
4. **OpenAI API Key** - For LLM integration

## Setup

### 1. Configure Maven for GitHub Packages

Create or update `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_GITHUB_PAT</password>
    </server>
  </servers>
</settings>
```

### 2. Set Environment Variables

```bash
# Copy the example env file
cp .env.example .env

# Edit with your values
vim .env
```

Or export directly:

```bash
export OPENAI_API_KEY=sk-...
```

### 3. Publish the Library First (if not already published)

From the root project directory:

```bash
cd ../..
./mvnw clean install -DskipTests
```

This installs the library to your local Maven repository.

## Running Tests

```bash
# Run all integration tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=LibraryIntegrationTest

# Run with verbose output
./mvnw test -Dtest=LibraryIntegrationTest -X
```

## Running the Demo Application

```bash
# Run with demo mode (fetches real stories)
./mvnw spring-boot:run -Dspring-boot.run.arguments="--demo"

# Run with specific ISIN
./mvnw spring-boot:run -Dspring-boot.run.arguments="--demo --isin=US67066G1040"
```

## Project Structure

```
story-engine-consumer/
├── pom.xml                    # Maven config with library dependency
├── .env.example               # Environment variables template
├── src/
│   ├── main/
│   │   ├── kotlin/            # Application code
│   │   │   └── ConsumerApplication.kt
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── kotlin/            # Test code
│           └── LibraryIntegrationTest.kt
```

## Validating the Library

The tests validate:

1. **Model Classes** - All data classes are accessible and work correctly
2. **Configuration** - `ApplicationConfig` binds properties properly
3. **Service Integration** - `StoryService` can be injected and called

## Troubleshooting

### "Could not resolve dependencies"

Ensure the library is published or installed locally:

```bash
# Option A: Install to local Maven repo
cd ../.. && ./mvnw clean install -DskipTests

# Option B: Check GitHub Packages authentication
cat ~/.m2/settings.xml  # Verify credentials
```

### "OPENAI_API_KEY not set"

Export the environment variable or create `.env` file:

```bash
export OPENAI_API_KEY=sk-your-key-here
```

### Java Version Mismatch

Verify you're using Java 24:

```bash
java -version
# Should show: openjdk version "24" or similar
```
