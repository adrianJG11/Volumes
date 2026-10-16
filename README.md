# Volumes

A personal book catalog and reading library built with Java 25 and Spring Boot. Includes a REST API and a console application; the API persists its catalog in PostgreSQL, while the console keeps its own in-memory library.

## Requirements

- JDK 25
- Maven
- Docker with Compose for the local PostgreSQL database

## Local PostgreSQL

Spring Boot connects to PostgreSQL, runs Flyway migrations on startup, and stores the API catalog in the database.

On a fresh checkout, copy `.env.example` to `.env` and set a local `POSTGRES_PASSWORD`. Do not overwrite an existing `.env`; it is ignored by Git. Compose reads this file automatically. Spring Boot explicitly imports it as a properties file; use simple KEY=value entries without shell quoting. Environment variables can override these settings.

```bash
cp .env.example .env
# Edit .env and fill in POSTGRES_PASSWORD before starting.
docker compose up -d --wait
docker compose ps
docker compose exec postgres psql -U volumes -d volumes -c 'SELECT current_database(), current_user;'
```

PostgreSQL is available on `127.0.0.1:5432`. If that port is occupied, change `POSTGRES_PORT` in `.env`. The `volumes` database and user are created on first initialization. This user is a local development administrator, not a production application account.

`compose.yaml` uses the official PostgreSQL 18 image, a readiness health check, and the named `postgres_data` volume. For PostgreSQL 18, the volume mounts at `/var/lib/postgresql`, as described in the [official image documentation](https://hub.docker.com/_/postgres). The major-version tag receives patch updates when pulled; major upgrades require an explicit migration plan.

```bash
docker compose stop          # Stop the database; keep its container and data.
docker compose up -d --wait  # Start it again.
docker compose down          # Remove this project's containers/network; keep data.
```

Do not add `--volumes` (`-v`) to `down` unless you intend to erase the local database. Changing the initialization password in `.env` after the volume exists does not change the database user's password.

## API

```bash
docker compose up -d --wait
mvn spring-boot:run
```

In another WSL terminal:

```bash
curl -i http://localhost:8080/books
curl -i http://localhost:8080/books \
  -H 'Content-Type: application/json' \
  -d '{"title":"Crime and Punishment","author":"Fyodor Dostoevsky","genre":"Fiction","pages":350}'
```

POST returns `201 Created`, a UUID, and a `Location` header. Use the returned ID to retrieve or update the book:

```bash
curl -i http://localhost:8080/books/BOOK_ID
curl -i -X PUT http://localhost:8080/books/BOOK_ID \
  -H 'Content-Type: application/json' \
  -d '{"title":"Crime and Punishment","author":"Fyodor Dostoevsky","genre":"Classic fiction","pages":400}'
```

Replace `BOOK_ID` with the actual UUID. PUT requires all four fields and preserves the ID. Invalid requests return `400` with JSON error details; unknown IDs return `404`. Invalid updates leave the existing book unchanged. Duplicate metadata is allowed with distinct IDs.

Created and updated books survive API restarts. Authentication is planned; this is a local learning application.

## Console

```bash
mvn compile && java -cp target/classes com.adrian.volumes.Main
```

Choose add, list, or exit. The console has its own in-memory library, separate from the API.

## Tests and packaging

```bash
mvn verify
java -jar target/volumes-0.1.0-SNAPSHOT.jar
```

`mvn test` runs the tests without packaging. Docker must be running: Catalog, HTTP, and schema tests use disposable PostgreSQL containers through Testcontainers, and the packaged smoke check runs the real Compose configuration under a unique temporary project and checks persistence across an API restart and database container recreation. It removes only that temporary project and its volume afterward. None of these checks uses the Compose database. Tests cover domain rules, catalog concurrency and updates, console behavior, and real HTTP requests. CI also runs scripted console and packaged-API smoke checks.

## Structure

Under `src/main/java/com/adrian/volumes`:

- `VolumesApplication.java`: Spring Boot API entry point.
- `Main.java`: console entry point.
- `book/`: validated book data, catalog service, and SQL repository.
- `book/api/`: HTTP controller, request/response records, and error handling.
- `library/`: console library collection with protected snapshots.
- `cli/`: menu, prompts, and output.

Tests mirror these packages under `src/test/java`. Generated files are in `target/`.

## Database migrations

`src/main/resources/db/migration/V1__create_books.sql` creates the `books` table. Flyway applies pending migrations at startup and records them in `flyway_schema_history`. Once applied, keep a migration unchanged and add a new version for the next schema change.

The UUID primary key identifies each book. `NOT NULL` and `CHECK` constraints reject missing fields, empty text, and non-positive page counts at the database boundary. Java validation remains responsible for the full input rules. A generated `insertion_order` number provides a deterministic ordering key; queries must explicitly use `ORDER BY insertion_order`.

The PostgreSQL JDBC driver connects Java to the database. Spring JDBC provides SQL execution and mapping support, and Flyway manages schema versions. Dependency versions are managed by the existing Spring Boot parent.

Requests flow through `BookController` (HTTP), `BookCatalog` (validation and book identity), and `BookRepository` (parameterized SQL). The repository maps each database row into a new Java object. Updates use one atomic `UPDATE ... RETURNING` statement, preserving the UUID and insertion order. Concurrent updates use last-write-wins behavior; no optimistic locking is implemented yet.
