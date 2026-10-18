# Volumes frontend

React + TypeScript + Vite, styled with Tailwind CSS v4. The catalog reads real books from the Java API. Registration, session login/logout, book creation and ISBN editing are available. Dedicated authentication screens are tracked in issue #17; personal-library ratings in #18.

## Run in WSL

```bash
cd ~/projects/biblioteca/frontend
nvm use
npm ci
npm run dev
```

Open http://localhost:5173. Vite binds to local loopback and fails if port 5173 is already occupied.

## Check and build

```bash
npm run lint
npm run build
npm run preview
```

Build runs TypeScript checks and generates production files in `dist/`. Preview serves those files locally; it is not a production server. `node_modules/` and `dist/` are ignored by Git. Commit `package-lock.json` so `npm ci` reproduces dependency versions.

## Files

- `index.html`: browser entry with the root element.
- `src/main.tsx`: mounts React inside that root and imports global CSS.
- `src/App.tsx`: the first UI component, written with JSX and TypeScript.
- `src/index.css`: Tailwind import, named design tokens, and base styles.
- `vite.config.ts`: React/Tailwind build plugins and local dev server settings.
- `package.json`: dependencies and commands, similar to the dependency/build roles of pom.xml.
- `tsconfig*.json`: TypeScript checking configuration.

Auth and catalog code live in `src/features/auth/` and `src/features/catalog/`. The shared API client handles requests, HTTP errors, timeouts and fresh CSRF tokens for mutations. Authentication currently uses an inline panel; issue #17 moves it onto dedicated screens.

## Visual direction

Open editorial rows, a large serif masthead, white background, slate text, and a burgundy accent. No rounded cards. Each book reserves a 2:3 cover slot with an explicit missing-cover placeholder; edition images load when a cover URL is available. Color names in `@theme` generate utilities such as `text-accent`; components should reuse those tokens. Keep motion minimal and preserve keyboard focus, readable contrast, and narrow-screen layouts.

Browser inspection uses the WSL Playwright tool environment. Catalog browser checks are described below.

## Catalog API connection

Start PostgreSQL and the Java API from the repository root as described in the main README, then run the frontend. Vite proxies `/books`, `/auth`, and `/me` to `http://127.0.0.1:8080` by default, so the session cookie stays same-origin. The browser uses a relative URL, so backend CORS changes are unnecessary. `API_TARGET` can override the backend address for isolated tests; it is a server-side Vite setting, not a browser secret.

The catalog distinguishes loading, successful empty results, loaded books, and failures. Retry/refresh starts another request. Effect cleanup cancels obsolete requests, including React Strict Mode's development checks. JSON is checked at runtime because TypeScript types do not validate network responses.

The proxy is development-only. Production hosting will need to route API paths to Java as well; `vite preview` is not that deployment setup.

## Catalog browser checks

With Vite running, execute the Playwright helper from WSL:

```bash
"$(uv tool dir)/playwright/bin/python" tests/catalog_browser.py
```

The deterministic checks intercept browser requests to exercise loading, loaded/empty catalogs, HTTP/network errors, malformed responses, recovery, narrow layouts, and escaped book titles. They do not write to a backend. Optional `--real-api` also verifies a nonempty running Java catalog through the real proxy. Use a seeded disposable test database for that integration check. `--screenshots /tmp/catalog-check` saves desktop/mobile images.

## Edition covers

The catalog reads optional edition metadata from Java. It renders uncropped Open Library or iTunes images in a fixed 2:3 area, with an Open Library edition link when available and a fallback for missing images. Signed-in readers can add, change or remove an ISBN using the editor below each book. The add-book form can prefill fields from title search and attach an optional ISBN. A missing cover can be looked up again by saving the same ISBN. Session, catalog and ISBN browser checks run in CI alongside the Java checks.

## Complete browser verification

From the repository root, with Docker running and `mvn verify` completed:

```bash
cd frontend && npm ci && cd ..
PYTHONDONTWRITEBYTECODE=1 "$(uv tool dir)/playwright/bin/python" frontend/tests/run_browser.py
```

Use the Playwright version pinned in `frontend/tests/requirements.txt`. The runner starts its own
PostgreSQL database, packaged Java API and Vite server on temporary ports, then cleans up only those
resources. Provider responses come from a local HTTP fixture; neither CI nor these tests call live
Open Library or iTunes. Chromium tests exercise registration, duplicate-email/wrong-password errors,
pending controls, cookie-session reload, fresh CSRF tokens, creating a book, ISBN save/removal,
logout and session loss. CI installs the pinned browser runtime, runs npm lint/build and invokes the
same runner; a local pass is separate from the GitHub Actions result on the PR.

## Testing approach

Prefer API integration tests with disposable PostgreSQL and a few browser journeys for user-visible behavior. Keep isolated unit tests only for meaningful pure logic, such as ISBN validation. Avoid repeating every validation case through the browser or adding tests that mirror implementation details. External book providers use local fixtures in automated tests.
