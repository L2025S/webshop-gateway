# Webshop Gateway

The gateway is the single entry point to the webshop backend. The frontend talks **only** to the gateway, and the gateway forwards each request to the right service (auth, product or order).

## Contents

- [Responsibilities](#responsibilities)
- [System overview](#system-overview)
- [Technologies](#technologies)
- [Database](#database)
- [Port](#port)
- [Environment variables](#environment-variables)
- [Running the application locally](#running-the-application-locally)
- [Endpoints](#endpoints)
- [Login and cookie](#login-and-cookie)
- [Tests](#tests)
- [Docker and CI](#docker-and-ci)
- [Other repositories](#other-repositories)

## Responsibilities

The gateway has four jobs:

1. **Single entry point for the frontend.** The frontend only calls the gateway under `/gateway/...`.
2. **Forwards requests** to auth-service, product-service and order-service and returns their responses (status, content type and body are preserved).
3. **Handles the login cookie.** On login, the JWT is stored in an `HttpOnly` cookie named `accessToken`. The gateway then forwards the JWT to the services as `Authorization: Bearer <token>`.
4. **Handles CORS.** Only the frontend addresses listed in `CORS_ALLOWED_ORIGINS` may call the gateway from a browser.

The gateway contains no business logic and has no database of its own.

## System overview

```
                        +--------------------+
                        |   Auth service     |  :8080
                    +-->|  (login, JWT)      |
                    |   +--------------------+
+----------+   +----+----+
| Frontend |-->| Gateway |   +--------------------+
|  :5173   |   |  :8090  |-->|  Product service   |  :8081
+----------+   +----+----+   +--------------------+
                    |
                    |   +--------------------+
                    +-->|   Order service    |  :8082
                        +--------------------+
```

The ports are the default values the gateway uses locally. They can be changed with environment variables, see below.

The mail service is part of the system but is not called through the gateway.

## Technologies

| Area | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.1.1 (`spring-boot-starter-webmvc`) |
| Build tool | Maven (Maven Wrapper: `mvnw` / `mvnw.cmd`) |
| Calls to other services | `java.net.http.HttpClient` (HTTP/1.1, 5 s connect timeout, 60 s timeout per request) |
| JSON | Jackson |
| Tests | JUnit with `spring-boot-starter-webmvc-test` |
| Container | Docker (multi-stage build) |
| CI | GitHub Actions (build, test and push of the Docker image) |

## Database

The gateway uses **no database**. All data is stored in the services the gateway calls.

## Port

The gateway listens on port **8090** by default. The value is controlled by the `PORT` environment variable (hosting platforms such as Render set it automatically).

## Environment variables

Every variable has a default value that works for local development.

| Variable | Default | Description |
|---|---|---|
| `PORT` | `8090` | Port the gateway listens on |
| `AUTH_SERVER_URL` | `http://localhost:8080` | Address of the auth service |
| `PRODUCT_SERVICE_URL` | `http://localhost:8081` | Address of the product service |
| `ORDER_SERVICE_URL` | `http://localhost:8082` | Address of the order service |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:5174,http://localhost:5175,https://frontend.onrender.com` | Comma-separated list of frontend addresses allowed to call the gateway |
| `COOKIE_SECURE` | `true` | Whether the cookie may only be sent over HTTPS |
| `COOKIE_SAME_SITE` | `None` | `SameSite` setting of the cookie (`None`, `Lax` or `Strict`) |

**Important in production (for example Render):**

- Set `AUTH_SERVER_URL`, `PRODUCT_SERVICE_URL` and `ORDER_SERVICE_URL` to the deployed addresses.
- Set `CORS_ALLOWED_ORIGINS` to the deployed frontend address. The address must not end with a trailing slash.
- If the frontend and the gateway are on different domains, `COOKIE_SECURE=true` and `COOKIE_SAME_SITE=None` are required, otherwise the browser will not send the cookie.

**Recommendation for local development over `http://localhost`:**

- `COOKIE_SECURE=false`
- `COOKIE_SAME_SITE=Lax`

The values in `application.properties` are defaults. Environment variables take precedence.

## Running the application locally

### Prerequisites

- Java 17 (JDK)
- Auth service, product service and order service are running locally (or the environment variables point to deployed services)

### Recommended start order

1. Start the **auth service** (port 8080)
2. Start the **product service** (port 8081)
3. Start the **order service** (port 8082)
4. Start the **gateway** (port 8090)
5. Start the **frontend** (port 5173)

### Start the gateway

Run the command in the project root folder.

macOS and Linux:

```bash
./mvnw spring-boot:run
```

Windows:

```bash
mvnw.cmd spring-boot:run
```

The gateway is now available at `http://localhost:8090`.

### Start with custom settings (example)

macOS and Linux:

```bash
AUTH_SERVER_URL=http://localhost:8080 \
COOKIE_SECURE=false \
COOKIE_SAME_SITE=Lax \
./mvnw spring-boot:run
```

Windows (PowerShell):

```powershell
$env:COOKIE_SECURE="false"
$env:COOKIE_SAME_SITE="Lax"
mvnw.cmd spring-boot:run
```

### Start the frontend

The frontend is started from its own repository (see [Other repositories](#other-repositories)):

```bash
npm install
npm run dev
```

The frontend should point to the gateway address (`http://localhost:8090`) and not directly to the other services.

## Endpoints

All endpoints are under `/gateway`. The gateway forwards the request to the service in the "Goes to" column.

| Method | Path | Goes to | Description |
|---|---|---|---|
| POST | `/gateway/auth/login` | Auth | Logs in and sets the `accessToken` cookie |
| POST | `/gateway/auth/logout` | Gateway | Removes the `accessToken` cookie |
| POST | `/gateway/appusers` | Auth | Registers a user |
| GET | `/gateway/appusers/me` | Auth | Gets the logged-in user |
| GET | `/gateway/appusers` | Auth | Gets all users |
| GET | `/gateway/appusers/{id}` | Auth | Gets one user |
| GET | `/gateway/products/all` | Product | Gets all products |
| GET | `/gateway/products/all-paged` | Product | Gets products with pagination (query parameters are forwarded) |
| GET | `/gateway/products/{id}` | Product | Gets a product by id |
| GET | `/gateway/products/uuid/{uuid}` | Product | Gets a product by UUID |
| GET | `/gateway/products/category/{categoryId}` | Product | Gets products in a category |
| GET | `/gateway/categories/all` | Product | Gets all categories |
| POST | `/gateway/products/new` | Product | Creates a product |
| POST | `/gateway/categories/new` | Product | Creates a category |
| PATCH | `/gateway/products/{uuid}/stock/reduce` | Product | Reduces stock |
| PATCH | `/gateway/products/{uuid}/stock/increase` | Product | Increases stock |
| PATCH | `/gateway/products/{uuid}/basic-info` | Product | Updates basic information |
| PATCH | `/gateway/products/{uuid}/image` | Product | Updates the image |
| PATCH | `/gateway/products/{uuid}/category` | Product | Changes the category |
| DELETE | `/gateway/products/{id}` | Product | Deletes a product |
| POST | `/gateway/order` | Order | Creates an order |
| GET | `/gateway/order/orders` | Order | Gets the logged-in user's orders |

Permissions (for example, only administrators may create products) are checked by each service, not by the gateway.

## Login and cookie

1. The frontend sends the login credentials to `POST /gateway/auth/login` (with `credentials: "include"`).
2. The gateway forwards the request to the auth service and receives a JWT.
3. The gateway puts the JWT in the `accessToken` cookie (`HttpOnly`, `Path=/`, lifetime according to `expiresIn`) and removes the token from the response body. The response only contains `expiresIn`, `subject` and `roles`.
4. The browser sends the cookie automatically on later requests.
5. The gateway reads the cookie, builds the header `Authorization: Bearer <token>` and forwards it to the service. If the request already has an `Authorization` header, that header is used instead.
6. `POST /gateway/auth/logout` removes the cookie.

Errors from the other services are forwarded with their original status code. If a service cannot be reached, the gateway responds with **502 Bad Gateway**.

## Tests

```bash
./mvnw test
```

On Windows: `mvnw.cmd test`.

## Docker and CI

Build and run with Docker:

```bash
docker build -t webshop-gateway .
docker run -p 8090:8090 \
  -e AUTH_SERVER_URL=http://host.docker.internal:8080 \
  -e PRODUCT_SERVICE_URL=http://host.docker.internal:8081 \
  -e ORDER_SERVICE_URL=http://host.docker.internal:8082 \
  webshop-gateway
```

GitHub Actions (`.github/workflows/maven.yml`) builds and tests the project on push and pull request to `master`. On push to `master`, a Docker image is also built and pushed to Docker Hub.

## Other repositories

| Part | Repository |
|---|---|
| Gateway (this repo) | https://github.com/L2025S/webshop-gateway |
| Auth service | https://github.com/OP-Devloop/Micro-Project-Auth-Server |
| Product service | https://github.com/L2025S/micro-project-product-service |
| Order service | https://github.com/Yunus-CanYucel/Micro-ProjectOrder-Service |
| Mail service | https://github.com/FredrikAlmen93/Mail-Service |
| Frontend | https://github.com/L2025S/webshop-frontend |