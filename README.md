# ATS RAG Backend

Spring Boot backend for parsing PDF resumes, matching LinkedIn jobs via Apify, and scoring candidates using local LLMs (Ollama) and PostgreSQL (pgvector).

## Prerequisites

* Java 21
* Docker & Docker Compose
* [Ollama](https://ollama.com/) installed locally

---

## Setup & Run Instructions

1. Ensure Ollama daemon is running, then pull the required embedding and generation models:

```bash
ollama pull nomic-embed-text
ollama pull gemma4:e4b
```

2. Create a .env file in the root directory for storing the DB connection details:

```txt
DB_NAME=ats_db
DB_USERNAME=postgres
DB_PASSWORD=Pg@123456
APIFY_API_TOKEN=apify_api_token
```

3. Run the DB in docker

```bash
docker run -d \
  --name ats-postgres \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=Pg@123456 \
  -e POSTGRES_DB=ats_db \
  -p 5432:5432 \
  -v ats_db:/var/lib/postgresql/data \
  pgvector/pgvector:pg16
```

4. Run the Spring Boot application:

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

---

## High Level Architecture

### 1. Client Submission

The user uploads a resume (PDF) along with specific search parameters (location, experience level, job type, etc.) via
the frontend UI. The backend controller receives the multipart request.

### 2. Initialization & Immediate Response

The backend extracts raw text from the PDF using Apache Tika. It persists this text and the initial state (`PROCESSING`)
into the relational database (`MatchTask` table). It immediately returns a generated `taskId` to the frontend, allowing
the HTTP request thread to close.

### 3. Polling Mechanism

The frontend begins polling the backend's `/status/{taskId}` endpoint at regular intervals to check if the background
processing has reached a terminal state (`SUCCESS` or `FAILED`).

### 4. Asynchronous LLM & Scraping Execution

Operating on a background Java 21 Virtual Thread, the backend executes the following sequence:

* Prompts the local LLM (`gemma4:e4b`) to extract precise, optimized job search queries based on the resume text.
* Dispatches a REST call to the Apify API to trigger the LinkedIn Jobs Scraper actor using the LLM-generated search
  queries.

### 5. Non-Blocking Wait

The background virtual thread polls the Apify API for completion. Because it is a virtual thread, `Thread.sleep()`
unmounts the underlying OS thread, allowing the server to handle high concurrency and serve other incoming requests
without resource exhaustion.

### 6. Vectorization & Semantic Ranking

Once Apify completes processing:

* The backend downloads the JSON dataset of scraped jobs.
* It maps these jobs into Spring AI `Document` objects and calculates their embeddings via the `nomic-embed-text` model,
  inserting them into `pgvector`.
* The backend performs a cosine similarity search against `pgvector` using the resume text as the query, returning the
  top 5 closest job matches.

### 7. AI Reasoning & State Update

For the top 5 matched jobs:

* The backend calculates a normalized match score out of 100.
* It prompts the LLM to generate a concise 1-2 sentence justification explaining exactly why the candidate's resume fits
  the specific job description.
* The aggregated results (jobs, scores, reasons) are serialized to JSON and saved to the `MatchTask` record. The status
  is updated to `SUCCESS`.

### 8. Data Lifecycle & Privacy Cleanup

Strict data retention policies are enforced:

* **Vector Cleanup:** The ephemeral job vector embeddings are hard-deleted from `pgvector` immediately after the
  similarity search concludes.
* **Synchronous Cleanup (Happy Path):** Once the frontend polls and receives a `SUCCESS` or `FAILED` status, the backend
  immediately deletes the `MatchTask` record (including the parsed PDF text) from the database before returning the HTTP
  response.
* **Asynchronous Cleanup (Fallback):** A scheduled background task (`@Scheduled`) runs every 15 minutes to delete any
  orphaned `MatchTask` records older than 1 hour. This guarantees no resume data is kept indefinitely if the client
  disconnects.

### 9. Frontend Presentation

The frontend receives the finalized JSON payload from the polling endpoint and renders the tailored job recommendations
to the user.
