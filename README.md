# Product Service

REST API for managing products, built as a hands-on lab covering the full path from code to production-like operation:
OAuth2 security, observability, containers, PostgreSQL, Kubernetes and an API gateway.

```
                      ┌──────────────────────────── kind cluster "products" ─────────────────────────────┐
 Postman / curl       │                                                                                    │
 localhost:8000 ──────┼─▶ Kong gateway (ns kong) ──▶ product-service ×2 (ns products) ──▶ PostgreSQL     │
                      │    request IDs, API keys,      Spring Boot, JWT roles,             StatefulSet+PVC │
                      │    rate limits, tracing        Flyway, OTel                                        │
 localhost:8081 ──────┼─▶ Keycloak (ns platform) ◀── JWKS ──┘                                              │
 localhost:3000 ──────┼─▶ Grafana LGTM (ns platform) ◀── OTLP traces/metrics/logs (app + Kong)            │
                      │    Mailpit (ns platform) ◀── alert emails                                          │
                      └────────────────────────────────────────────────────────────────────────────────────┘
```

## Tech stack

| Area | Technology |
|---|---|
| Language / framework | Java 21 (virtual threads), Spring Boot 3.5 (Web, Data JPA, Validation, Security, Actuator) |
| Security | OAuth2 resource server (JWT) with **Keycloak** 26; role-based access (USER read, ADMIN write) |
| Persistence | **PostgreSQL** 17 with **Flyway** migrations; H2 (PostgreSQL mode) for local runs and tests |
| API docs | springdoc-openapi: OpenAPI 3 + Swagger UI with OAuth2 (PKCE) login |
| Observability | Micrometer + **OpenTelemetry** (OTLP) → **Grafana LGTM** (Tempo traces, Prometheus metrics, Loki logs); Grafana alerting with Mailpit |
| Containers | Multi-stage, layered **Docker** image (non-root, container-aware JVM); **docker-compose** |
| Orchestration | **Kubernetes** on **kind** (3 nodes), Kustomize manifests |
| API gateway | **Kong** Ingress Controller (DB-less, Helm): routing, correlation IDs, key-auth, per-plan rate limiting, OpenTelemetry |
| Build / tests | Maven (wrapper), JUnit 5, Mockito, AssertJ, MockMvc, spring-security-test |

## Project structure

```
src/main/java/com/example/productservice/
├── controller/   REST endpoints
├── service/      business logic
├── repository/   Spring Data JPA repositories
├── entity/       JPA entities
├── dto/          request/response records
├── mapper/       DTO <-> entity mapping
├── exception/    domain exceptions + global handler (RFC 7807 ProblemDetail)
├── security/     Keycloak role mapping, 401/403 ProblemDetail handlers
├── config/       security, OpenAPI and OpenTelemetry log appender configuration
└── filter/       X-Request-ID → logging MDC
src/main/resources/
├── application.yml            base config (H2, security, observability)
├── application-postgres.yml   "postgres" profile (env-based datasource, Hikari pool)
├── db/migration/              Flyway migrations
└── logback-spring.xml         console + OTLP log export

k8s/
├── kind-cluster.yaml          3-node cluster + host port mappings (8000, 8081, 3000)
├── kustomization.yaml         everything except Kong (installed with Helm)
├── namespaces.yaml            products, platform
├── postgres/                  StatefulSet, headless Service, Secret
├── keycloak/                  Deployment, NodePort Service, realm/ (also used by docker-compose)
├── observability/             Grafana LGTM, Mailpit, grafana/alerting/ (also used by docker-compose)
├── product-service/           Deployment, Service, ConfigMap
└── gateway/                   Kong Helm values, Ingress, plugins, consumers
postman/                       Postman collection
Dockerfile, docker-compose.yml
```

## API

Base path: `/api/v1/products`

| Method | Path | Description | Role | Responses |
|---|---|---|---|---|
| POST | `/` | Create a product | ADMIN | 201, 400, 401, 403, 409 |
| GET | `/{id}` | Get a product by id | USER or ADMIN | 200, 401, 404 |
| GET | `/?page=0&size=20&sort=name,asc` | List products (paginated, sortable) | USER or ADMIN | 200, 400, 401 |
| PUT | `/{id}` | Replace a product | ADMIN | 200, 400, 401, 403, 404, 409 |
| DELETE | `/{id}` | Delete a product | ADMIN | 204, 401, 403, 404 |

Request body:

```json
{
  "name": "Wireless Mouse",
  "description": "Ergonomic 2.4GHz wireless mouse",
  "price": 19.99,
  "stock": 10,
  "sku": "MS-001"
}
```

`name`, `price`, `stock` and `sku` are required; `sku` must be unique. All errors (including 401/403) are returned as
`application/problem+json`. Unexpected errors return a generic 500 without internal details; the stack trace is logged.

Public endpoints (no token): Swagger UI, `/v3/api-docs`, `/actuator/health/**` (liveness/readiness probes), `/actuator/info`.

### Users and tokens

The Keycloak realm `product-service` is imported from `k8s/keycloak/realm/product-service-realm.json`.

| User | Password | Role |
|---|---|---|
| `admin` | `admin` | ADMIN (read + write) |
| `alice` | `alice` | USER (read only) |

Get a token (password grant, enabled for local development only):

```bash
curl -s -d grant_type=password -d client_id=product-service-client \
     -d username=admin -d password=admin \
     http://localhost:8081/realms/product-service/protocol/openid-connect/token
```

Swagger UI's **Authorize** button logs in through Keycloak (authorization code + PKCE). The Keycloak admin console is at
http://localhost:8081 (master realm `admin` / `admin`).

## Running

There are three ways to run the service. Docker-compose and the kind cluster use the same host ports (8081, 3000),
so run only one of them at a time.

### 1. Locally with Maven (H2)

Needs Keycloak for authentication; telemetry export is optional (it only logs warnings when nothing listens on 4318).

```bash
docker compose up -d keycloak          # optionally also: otel-lgtm mailpit
./mvnw spring-boot:run
```

The app starts on http://localhost:8080 with an in-memory H2 database (schema created by Flyway; reset on restart).

### 2. Full stack with docker-compose

Builds the image and runs the app with PostgreSQL, Keycloak, Grafana LGTM and Mailpit.

```bash
docker compose up -d --build
docker compose ps                      # wait until product-service is (healthy)
```

| Resource | URL |
|---|---|
| API / Swagger UI | http://localhost:8080 / http://localhost:8080/swagger-ui.html |
| Keycloak | http://localhost:8081 |
| Grafana | http://localhost:3000 |
| Mailpit inbox | http://localhost:8025 |
| PostgreSQL | `localhost:5432`, database/user/password `products` |

Data is kept in the `pgdata` volume. Stop with `docker compose stop` (keeps data) or `docker compose down -v` (deletes it).

### 3. Kubernetes (kind) with the Kong gateway

Prerequisites: Docker Desktop (12 GB memory recommended), `kubectl`, `kind`, `helm`.

```bash
# 1) Cluster: 1 control plane + 2 workers, host ports 8000 (Kong), 8081 (Keycloak), 3000 (Grafana)
kind create cluster --config k8s/kind-cluster.yaml

# 2) Images: kind nodes don't share the host image cache. With Docker Desktop's containerd image store,
#    `kind load docker-image` fails on multi-arch images, so load single-platform archives instead.
docker build -t product-service:0.2.0 .
for img in product-service:0.2.0 postgres:17.6 quay.io/keycloak/keycloak:26.3 \
           grafana/otel-lgtm:0.11.10 axllent/mailpit:v1.27.0; do
  docker pull -q "$img" 2>/dev/null || true
  docker save --platform linux/arm64 -o /tmp/img.tar "$img" && kind load image-archive /tmp/img.tar --name products
done
rm -f /tmp/img.tar        # on Intel/AMD machines use --platform linux/amd64

# 3) Kong gateway (Ingress Controller + Gateway, DB-less)
helm repo add kong https://charts.konghq.com && helm repo update
helm upgrade --install kong kong/ingress -n kong --create-namespace \
  -f k8s/gateway/kong-values.yaml --version 0.24.0
kubectl rollout status deploy/kong-gateway -n kong

# 4) Everything else: namespaces, Postgres, Keycloak, Grafana LGTM, Mailpit, product-service, Ingress, plugins, consumers
kubectl apply -k k8s/
kubectl rollout status deploy/product-service -n products
```

| Resource | URL |
|---|---|
| API through Kong | http://localhost:8000/api/v1/products |
| Swagger UI through Kong | http://localhost:8000/swagger-ui.html |
| Keycloak | http://localhost:8081 |
| Grafana | http://localhost:3000 |
| Mailpit inbox | `kubectl port-forward -n platform svc/mailpit 8025:8025`, then http://localhost:8025 |
| PostgreSQL | `kubectl exec -it postgres-0 -n products -- psql -U products -d products` |

Actuator endpoints are deliberately not routed through the gateway.

Useful commands:

```bash
kubectl get pods -A -o wide                                    # what runs where
kubectl logs -n products -l app=product-service --prefix -f    # logs of all app pods
kubectl rollout restart deploy/product-service -n products     # zero-downtime rolling restart
kubectl rollout undo deploy/product-service -n products        # roll back to the previous revision
kubectl diff -k k8s/                                           # drift between the cluster and the manifests
```

Pause and resume the cluster (Postgres data is kept on its PersistentVolume; Grafana history is reset):

```bash
docker stop products-control-plane products-worker products-worker2
docker start products-control-plane products-worker products-worker2
```

Delete it completely with `kind delete cluster --name products`.

#### Kubernetes setup highlights

- **product-service**: 2 replicas spread across nodes (`topologySpreadConstraints`), rolling updates with
  `maxSurge: 1` / `maxUnavailable: 0`, startup/readiness/liveness probes on the actuator health groups, a `preStop`
  drain before graceful shutdown, CPU/memory requests and a memory limit (no CPU limit), and a restricted security
  context (non-root UID 10001, read-only root filesystem, all capabilities dropped).
- **Configuration**: environment variables from a ConfigMap; database credentials from the same Secret Postgres uses.
  The same image runs locally, in docker-compose and in Kubernetes.
- **Keycloak issuer**: tokens are issued as `http://localhost:8081/...` (`KC_HOSTNAME`), while pods fetch the signing keys
  from the internal `keycloak.platform.svc.cluster.local` JWKS URI.
- **Generated ConfigMaps**: the Keycloak realm and Grafana alert rules get a content hash in their name, so editing
  them rolls the consuming pods automatically.

## API gateway (Kong)

Kong is configured declaratively with Kubernetes objects in `k8s/gateway/`:

| Plugin | Behavior |
|---|---|
| `correlation-id` | Adds `X-Request-ID` (UUID) to every request and response; keeps one sent by the client. The app copies it into its logs. |
| `key-auth` | Identifies the API client from the `apikey` header and strips the header before forwarding. Requests without a valid key use the `anonymous` consumer. |
| `rate-limiting` | Anonymous: **5 requests/minute per IP**. Partner `acme`: **100 requests/minute per consumer**. Excess requests get `429` with `RateLimit-*` and `Retry-After` headers and never reach the service. |
| `opentelemetry` | Starts a trace at the gateway (service `kong-gateway`, 100% sampling) and propagates W3C `traceparent`, so Kong and product-service spans share one trace. |

| Consumer | API key (dev only) | Quota |
|---|---|---|
| anonymous | none | 5 / minute per IP |
| acme | `acme-dev-key-123` | 100 / minute |

The API key identifies the client application (plan and quota); the JWT identifies the user (roles). Both are needed to write.

```bash
curl -s -D - -o /dev/null localhost:8000/api/v1/products | grep -i ratelimit-limit                                  # 5
curl -s -D - -o /dev/null -H 'apikey: acme-dev-key-123' localhost:8000/api/v1/products | grep -i ratelimit-limit   # 100
```

Response headers `X-Kong-Proxy-Latency` (time in Kong) and `X-Kong-Upstream-Latency` (time in the service) show where
time is spent.

## Observability

The service and Kong export traces, metrics and logs over OTLP to Grafana LGTM. Open Grafana → **Explore**:

| Signal | Datasource | Example query |
|---|---|---|
| Traces | Tempo | Search by service `kong-gateway` or `product-service`; TraceQL `{ resource.service.name = "kong-gateway" && span.http.status_code = 429 }` |
| Metrics | Prometheus | `sum by (status) (rate(http_server_requests_milliseconds_count{job="product-service"}[1m]))` |
| p95 latency | Prometheus | `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_milliseconds_bucket{job="product-service"}[5m])))` |
| Requests per pod | Prometheus | `sum by (instance) (http_server_requests_milliseconds_count{job="product-service"})` |
| Logs | Loki | `{service_name="product-service"} \| severity_text="ERROR"` |
| Logs of one request | Loki | `{service_name="product-service"} \| request_id="<X-Request-ID value>"` |

- Every log line carries `trace_id`/`span_id`, so Grafana links logs and traces in both directions.
- Telemetry is tagged with the pod name (`service.instance.id`, the `instance` label in Prometheus).
- SQL statements and connection acquisition appear as spans inside each request trace.
- Only requests that write a log line can be found by `request_id`: creates, updates, deletes and handled errors.
  Reads log at DEBUG, and requests rejected by security or by Kong never reach the application code.

### Alerting

Grafana alerting is provisioned as code in `k8s/observability/grafana/alerting/product-service-alerts.yaml`
(used by both docker-compose and Kubernetes).

| Alert | Condition | Notifies |
|---|---|---|
| Auth failures (401/403) >= 3 in 1m | `sum(increase(http_server_requests_milliseconds_count{status=~"401\|403"}[1m])) > 2.5`, evaluated every 10s | Email to `alerts@product-service.local`, re-sent every 1m while firing (10s group interval), plus a resolved email |

Emails are delivered to [Mailpit](https://mailpit.axllent.org/), a local SMTP server that catches all mail. To deliver
to a real inbox, point the `GF_SMTP_*` variables to a real SMTP server and change `addresses` in the alerting file.

Trigger the alert with unauthenticated requests (on Kubernetes use port 8000; mind the anonymous 5/min limit, which
still produces enough 401s):

```bash
for i in 1 2 3 4 5; do curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/api/v1/products; done
```

Within ~30s the rule turns *Firing* and a `[FIRING:1] Auth failures ...` email appears in Mailpit.

## Postman

Import `postman/product-service.postman_collection.json`.

- `baseUrl` defaults to `http://localhost:8000` (Kong on Kubernetes). Set it to `http://localhost:8080` for the local
  or docker-compose setups.
- Run the **Auth** folder first: it stores the admin and alice tokens in collection variables.
- A collection-level pre-request script sends `apikey: {{partnerApiKey}}`, so full collection runs use the partner plan
  (100 requests/minute). The **Gateway plans** folder compares it with anonymous access.
- **Create product** stores the new id in `productId` for the following requests.

## Testing

```bash
./mvnw clean verify
```

Unit tests cover the service, mapper, role converter and request-ID filter; `@WebMvcTest` controller tests run with the
real security configuration and mocked JWTs (401/403 rules, validation, error mapping). Tests use H2 and do not need
Keycloak, PostgreSQL or Docker.

## Local development only

The credentials, API keys and Keycloak settings in this repository (`admin/admin`, `alice/alice`, `products`,
`acme-dev-key-123`, the password grant, HTTP without TLS, `start-dev` Keycloak) are for local development. In production
they come from a secrets manager, Keycloak runs in production mode with TLS and a database, and secrets are protected
with RBAC and encryption at rest.
