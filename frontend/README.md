# Dispatch console (frontend)

Next.js 16 · React 19 · TypeScript · Tailwind CSS 4 · TanStack Query · Zustand · STOMP over WebSocket.
See the [project README](../README.md) for the full picture.

```bash
npm install
npm run dev          # http://localhost:3000 — expects the API at http://localhost:8080
npm test             # Vitest + React Testing Library
npm run lint
npm run typecheck    # generates Next.js route types, then tsc
npm run build
```

| Variable | Default | Purpose |
|---|---|---|
| `NEXT_PUBLIC_API_URL` | `http://localhost:8080` | API address used by the browser (REST and WebSocket) |
| `NEXT_PUBLIC_SHOW_DEMO_CREDENTIALS` | unset | `true` shows the demo-account panel on the login page |

Layout:

```text
src/
├── app/               routes; (console)/ is the signed-in area, proxy.ts guards it
├── components/        feature components (dashboard, incidents, events, …) and ui/ primitives
├── hooks/             useMe, useFlash, useDebouncedValue, useElementWidth
├── lib/               api/ client & types, realtime/ message handling, domain helpers
├── stores/            Zustand: realtime connection + live buffer, toasts, shared clock
└── test/              fixtures and render helpers for tests
```
