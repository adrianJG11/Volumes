# Volumes frontend

React + TypeScript + Vite, styled with Tailwind CSS v4. The app opens with sign-in, then shows only the signed-in reader’s personal library. Readers search for a book, choose an edition/cover, and add it directly with a reading status. Existing sessions open the library immediately. The shared catalog remains an API implementation detail.

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

Auth and catalog code live in `src/features/auth/` and `src/features/catalog/`. The shared API client handles requests, HTTP errors, timeouts and fresh CSRF tokens for mutations. Authentication uses dedicated /login and /register screens. Native links preserve browser history and reload behavior; successful login replaces the form with the personal library. Signed-out visits to / and /library redirect to /login; logout and expired sessions return to login. The included nginx configuration serves index.html for frontend routes while forwarding API paths to Java.

## Visual direction

Open editorial rows, a large serif masthead, white background, slate text, and a burgundy accent. No rounded cards. Each book reserves a 2:3 cover slot with an explicit missing-cover placeholder; edition images load when a cover URL is available. Color names in `@theme` generate utilities such as `text-accent`; components should reuse those tokens. Keep motion minimal and preserve keyboard focus, readable contrast, and narrow-screen layouts.

Browser inspection uses the WSL Playwright tool environment. Catalog browser checks are described below.

## Catalog API connection

Start PostgreSQL and the Java API from the repository root as described in the main README, then run the frontend. Vite proxies `/books`, `/auth`, and `/me` to `http://127.0.0.1:8080` by default, so the session cookie stays same-origin. The browser uses a relative URL, so backend CORS changes are unnecessary. `API_TARGET` can override the backend address for isolated tests; it is a server-side Vite setting, not a browser secret.

The catalog distinguishes loading, successful empty results, loaded books, and failures. Retry/refresh starts another request. Effect cleanup cancels obsolete requests, including React Strict Mode's development checks. JSON is checked at runtime because TypeScript types do not validate network responses.

The Vite proxy is development-only. Docker Compose now serves the built frontend through nginx on localhost:8081, including API proxying; `vite preview` is not that deployment setup.

## Books, covers and reading status

Use **Add a book** in your library. Search suggestions prefill editable title, author, genre,
page count and ISBN. Choose **Want to read**, **Reading** or **Finished**, then add the book.
Java stores the shared book record and the frontend links it to the signed-in reader through
the existing personal-library API. A failed library/status request keeps the created book ID
in the form so retrying does not create another shared book. A cover lookup failure keeps the
book available and can be retried using its ISBN editor.

Library rows show the cover, personal reading status and rating. Status changes use PATCH
/me/library/{bookId}; personal ratings still use the existing rating endpoints. Account ownership
always comes from the Java session. The public catalog has no separate screen or navigation item.

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
pending controls, cookie-session reload, fresh CSRF tokens, searching/adding a book directly to the library, reading statuses, ISBN cover/removal,
logout and session loss. CI installs the pinned browser runtime, runs npm lint/build and invokes the
same runner; a local pass is separate from the GitHub Actions result on the PR.

## Testing approach

Prefer API integration tests with disposable PostgreSQL and a few browser journeys for user-visible behavior. Keep isolated unit tests only for meaningful pure logic, such as ISBN validation. Avoid repeating every validation case through the browser or adding tests that mirror implementation details. External book providers use local fixtures in automated tests.

## Personal library

The home screen (/) and legacy /library URL show the same personal library after login.
Readers can save a rating from 1 to 5 or clear it. Saved values change only after Java confirms
success. Clearing a rating preserves the entry and shared book. Reading status and rating belong
to each reader independently. Private entries unmount on logout/session refresh and are keyed by
account identity. Browser checks use two accounts to verify independent statuses and ratings,
reloading, failed-save recovery and session expiry.
