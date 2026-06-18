# Helianthus Admin UI

React + TypeScript admin interface for browsing and running Helianthus operations.

## Development

```bash
cp .env.example .env
npm install
npm run dev
```

The application reads:

- `VITE_API_BASE_URL`
- `VITE_KEYCLOAK_URL`
- `VITE_KEYCLOAK_REALM`
- `VITE_KEYCLOAK_CLIENT_ID`

The defaults match `docker-compose.starter.yml`.

## Commands

```bash
npm run dev
npm run build
npm run preview
npm run lint
```

Keycloak uses authorization code flow with PKCE. Authenticated API calls include a
Bearer token. Guest mode can call public health and falls back to the mock catalog
when the protected catalog endpoint returns 401 or 403.

The starter realm provides:

- `admin` / `admin` with the `ADMIN` role
- `guest` / `guest` with the `GUEST` role
