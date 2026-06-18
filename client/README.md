# Helianthus Admin UI

React + TypeScript admin interface for browsing and running Helianthus operations.

## Development

```bash
cp .env.example .env
npm install
npm run dev
```

The application defaults to `http://localhost:8080` for API requests. Override it
with `VITE_API_BASE_URL`.

## Commands

```bash
npm run dev
npm run build
npm run preview
npm run lint
```

Authentication and the catalog are placeholders. Both login paths are local-only,
and the operation tree currently reads from `src/data/mockCatalog.ts`.
