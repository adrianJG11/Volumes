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

In another WSL terminal (GET is public; POST and PUT below require the session cookie and CSRF header described under Sessions):

```bash
curl -i http://localhost:8080/books
curl -i http://localhost:8080/books \
  -b /tmp/volumes-cookies.txt -H "X-CSRF-TOKEN: $CSRF" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Crime and Punishment","author":"Fyodor Dostoevsky","genre":"Fiction","pages":350}'
```

POST returns `201 Created`, a UUID, and a `Location` header. Use the returned ID to retrieve or update the book:

```bash
curl -i http://localhost:8080/books/BOOK_ID
curl -i -X PUT http://localhost:8080/books/BOOK_ID \
  -b /tmp/volumes-cookies.txt -H "X-CSRF-TOKEN: $CSRF" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Crime and Punishment","author":"Fyodor Dostoevsky","genre":"Classic fiction","pages":400}'
```

Replace `BOOK_ID` with the actual UUID. PUT requires all four fields and preserves the ID. Invalid requests return `400` with JSON error details; unknown IDs return `404`. Invalid updates leave the existing book unchanged. Duplicate metadata is allowed with distinct IDs.

Created and updated books survive API restarts. Catalog reads are public; creating or updating shared books requires a signed-in account and a valid CSRF token. Any signed-in account may edit shared catalog metadata in this collaborative MVP. Personal libraries enforce separate owner checks.

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

`mvn test` runs the tests without packaging. Docker must be running: Catalog, HTTP, and schema tests use disposable PostgreSQL containers through Testcontainers, and the packaged smoke check runs the real Compose configuration under a unique temporary project and checks persistence across an API restart and database container recreation. It removes only that temporary project and its volume afterward. None of these checks uses the development Compose database. Tests cover domain rules, catalog concurrency and updates, console behavior, and real HTTP requests. CI also runs scripted console and packaged-API smoke checks.

## Structure

Under `src/main/java/com/adrian/volumes`:

- `VolumesApplication.java`: Spring Boot API entry point.
- `Main.java`: console entry point.
- `book/`: validated book data, catalog service, and SQL repository.
- `book/api/`: HTTP controller, request/response records, and error handling.
- `library/`: personal-library SQL repository, reading statuses, API endpoints, and the independent console collection.
- `user/`: accounts, registration, password hashing, and Spring Security session configuration.
- `cli/`: menu, prompts, and output.

Tests mirror these packages under `src/test/java`. Generated files are in `target/`.

## Database migrations

`src/main/resources/db/migration/V1__create_books.sql` creates the `books` table. Flyway applies pending migrations at startup and records them in `flyway_schema_history`. Once applied, keep a migration unchanged and add a new version for the next schema change.

The UUID primary key identifies each book. `NOT NULL` and `CHECK` constraints reject missing fields, empty text, and non-positive page counts at the database boundary. Java validation remains responsible for the full input rules. A generated `insertion_order` number provides a deterministic ordering key; queries must explicitly use `ORDER BY insertion_order`.

The PostgreSQL JDBC driver connects Java to the database. Spring JDBC provides SQL execution and mapping support, and Flyway manages schema versions. Dependency versions are managed by the existing Spring Boot parent.

Requests flow through `BookController` (HTTP), `BookCatalog` (validation and book identity), and `BookRepository` (parameterized SQL). The repository maps each database row into a new Java object. Updates use one atomic `UPDATE ... RETURNING` statement, preserving the UUID and insertion order. Concurrent updates use last-write-wins behavior; no optimistic locking is implemented yet.

## Accounts

Accounts use an identity model (`user/User.java`) and `V2__create_users.sql`. Users have a UUID and an email normalized by stripping surrounding whitespace and lowercasing with `Locale.ROOT`. Volumes treats email addresses as case-insensitive account identifiers; basic format validation does not verify mailbox ownership.

The database requires a unique normalized email and a nonempty `password_hash`. The public identity model contains no password or hash. Registration generates salted bcrypt hashes through Spring Security (cost 12), stored with an algorithm prefix. The schema itself cannot prove that a string is a secure hash. Session login and catalog access rules are implemented. Personal-library endpoints use the signed-in account as their owner.

The migration test upgrades a populated V1 database to V2, verifies existing books survive, and checks account constraints in an isolated PostgreSQL container.

### Registration

`POST /auth/register` accepts JSON fields `email` and `password`. Success returns `201` with only the account `id` and normalized `email`. Invalid requests return `400`; a duplicate normalized email returns `409`, including concurrent registrations. Passwords must contain at least 15 Unicode code points and at most 72 UTF-8 bytes (bcrypt's limit). Passwords are never trimmed or lowercased.

The request object redacts its `toString()` and marks the password as write-only for JSON. UserRepository receives only an encoded hash. `ON CONFLICT (email) DO NOTHING` relies on PostgreSQL uniqueness to prevent duplicate accounts atomically.

Registration does not sign the user in. Email ownership verification, password reset, and public-deployment abuse controls are outside this increment.

### Sessions

| Endpoint | Request | Result |
| --- | --- | --- |
| GET /auth/csrf | No authentication required | JSON `headerName` and `token`; starts a session when needed |
| POST /auth/register | JSON email/password + CSRF header | 201 account identity; does not sign in |
| POST /auth/login | Form-encoded email/password + CSRF header | 204 and session cookie, or generic 401 |
| GET /auth/me | Session cookie | 200 account ID/email, or 401 |
| POST /auth/logout | Session cookie + CSRF header | 204; invalidates the session |

Use a client that retains cookies. Fetch a token from `/auth/csrf`, then send it as the `X-CSRF-TOKEN` header with each POST, PUT, PATCH, or DELETE. Registration and login also require a token. Fetch a new token after login or logout because Spring invalidates the previous token. A missing or invalid token returns 403 before controller logic; a valid token alone does not authenticate a user.

Login uses `application/x-www-form-urlencoded` rather than JSON so Spring Security can handle authentication, session fixation protection, and credential erasure through its standard filter. Registration and book endpoints still use JSON. Fetch `/auth/me` after login to obtain account details.

The session cookie is HttpOnly and SameSite=Lax. Sessions expire after 30 minutes of inactivity and are held in application memory: restarting the API signs users out, while accounts and books remain in PostgreSQL. Local HTTP development does not set Secure; an HTTPS deployment must enable `server.servlet.session.cookie.secure=true`. Authentication currently assumes a same-origin frontend; no cross-origin cookie access is configured.

The shared catalog is collaborative: signed-in accounts can create or edit its metadata. This does not give them access to another user's personal-library entries. The console remains an independent local program.

For local manual checks, obtain a token and retain the session cookie:

```bash
CSRF=$(curl -fsS -c /tmp/volumes-cookies.txt -b /tmp/volumes-cookies.txt http://localhost:8080/auth/csrf | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')
```

Register a local test account using JSON, the cookie file, and `-H "X-CSRF-TOKEN: $CSRF"`. To log in with that account, use `curl -b /tmp/volumes-cookies.txt -c /tmp/volumes-cookies.txt -H "X-CSRF-TOKEN: $CSRF" --data-urlencode 'email=reader@example.com' --data-urlencode 'password=YOUR_TEST_PASSPHRASE' http://localhost:8080/auth/login`. Fetch a fresh CSRF token using the command above before running the book POST/PUT examples. Use test credentials only in shell examples; commands may be retained in shell history.

## Personal libraries

Each user has a private list of shared-catalog books. All endpoints below require a session; mutations also require a CSRF header. Ownership always comes from the authenticated principal, never a request field or query parameter.

| Method | Path | Body | Success |
| --- | --- | --- | --- |
| POST | /me/library | `{"bookId":"UUID"}` | 201 entry, initially PENDING |
| GET | /me/library | None | 200 array in addition order |
| PATCH | /me/library/{bookId} | `{"status":"READING"}` | 200 updated entry |
| DELETE | /me/library/{bookId} | None | 204 |

Entries return `bookId`, `title`, `author`, `genre`, `pages`, and `status`. Status must be PENDING, READING, or FINISHED; numeric values and unknown names return 400. Updates preserve addition order. Missing books on add return 404; duplicate entries return 409. Updating or removing an entry absent from your own library returns 404, even if another reader has that book.

`V3__create_library_entries.sql` adds foreign keys to users and books, a composite primary key `(user_id, book_id)`, an allowed-status constraint, and a generated ordering key. Each SQL mutation is atomic. The repository scopes reads, updates, and deletes by user ID. Removing an entry never removes the shared book. Book details are joined from the catalog, so metadata edits are reflected for every reader while reading statuses remain independent.

The packaged smoke check uses two accounts and verifies that accounts, books, and personal entries survive API restart and database container recreation. Tests also cover attempted cross-user changes, malformed input, CSRF, and simultaneous duplicate additions.
