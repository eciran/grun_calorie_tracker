# GRun Admin UI

Separate React/Vite admin workspace for internal operations.

## Run Locally

Start the Spring Boot backend first:

```powershell
.\scripts\run-local.ps1
```

Then run the admin UI:

```powershell
cd admin-ui
npm install
npm run dev
```

Open:

```text
http://127.0.0.1:5174
```

The Vite dev server proxies `/api/...` requests to `http://localhost:8080`, so the browser does not need direct CORS changes for local development.

## Initial Scope

- Admin login with backend JWT.
- Dashboard summary.
- Food product review queue.
- User list.
- Subscription feature matrix.
- AI meal draft review queue.
- Admin audit log.
- Notifications.
- System health payload.

The legacy static admin page under `src/main/resources/static/admin-ui` remains available as a temporary local fallback.
