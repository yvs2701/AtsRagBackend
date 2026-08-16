# ATS RAG Backend

Spring Boot backend for parsing PDF resumes, matching LinkedIn jobs via Apify, and scoring candidates using local LLMs (Ollama) and PostgreSQL (pgvector).

## Prerequisites

* Java 21
* Docker & Docker Compose
* [Ollama](https://ollama.com/) installed locally

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
