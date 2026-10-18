# Volumes frontend

React + TypeScript + Vite, styled with Tailwind CSS v4. This first increment is a static starter screen; authentication and catalog API integration follow in issue #15.

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

Use feature folders for auth and catalog when those features are introduced. No API requests, login controls, sample library data, or frontend test suite are implemented yet.

## Visual direction

White background, slate text, blue accent, and a serif reading headline. Color names in `@theme` generate utilities such as `text-accent`; components should reuse those tokens. Keep motion minimal and preserve keyboard focus, readable contrast, and narrow-screen layouts.

Browser inspection uses the WSL Playwright tool environment. Frontend end-to-end tests will be added with the first real user flow.
