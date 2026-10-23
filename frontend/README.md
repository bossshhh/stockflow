# StockFlow web UI

React 19 + TypeScript + Vite. Talks to the Spring Boot API through the Vite dev proxy (`/api` → `127.0.0.1:8080`).

```bash
npm install
npm run dev        # http://localhost:5173 (start the backend first: docker compose up -d)
npm test           # unit tests (Vitest)
npm run typecheck  # TypeScript, strict
npm run lint       # oxlint
npm run build      # production build in dist/
npm run gen:api    # regenerate src/api/schema.d.ts from the running backend's OpenAPI spec
```

Design notes and decisions: `../docs/GUIDE.md`, section "The web UI".
