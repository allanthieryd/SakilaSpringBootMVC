# Sakila sample website in Spring Boot MVC

You should use the sakiladb/mysql docker image   

docker pull sakiladb/mysql:latest



This repository is a simple Spring Boot MVC project that gives you some basic functions on several entities in Sakila database.
This project is dependent on Spring Web, Lombok, Spring Data JPA, MySQL driver, and Thymeleaf engine.

When you run the project locally on your computer, don't forget to update MySQL connection in application.properties file.

Home page

![image](https://github.com/wooly905/SakilaSpringBootMVC/assets/18693681/bd9fc8d4-5023-4023-8fb2-bf3d7ef9b94f)

Actor list - display each actor and the movies that the actor played

![image](https://github.com/wooly905/SakilaSpringBootMVC/assets/18693681/79821356-f2f0-42bf-90c1-0bb06c650fd7)

Film list - display a list of films

![image](https://github.com/wooly905/SakilaSpringBootMVC/assets/18693681/f77c01b6-3b4f-47f1-adff-1d834f467f72)

Film list - display the detail of a film

![image](https://github.com/wooly905/SakilaSpringBootMVC/assets/18693681/aff9ab00-f137-470e-9cda-2c6bd82f7911)

Film list - add a film

![image](https://github.com/wooly905/SakilaSpringBootMVC/assets/18693681/213ed820-8cdf-4cf5-8ead-69a548f63cc4)

Film category list - display a list of film categories and the associated movies

![image](https://github.com/wooly905/SakilaSpringBootMVC/assets/18693681/7cf51c4e-4c25-4fbb-97bb-0500415679ae)

Actor pagination list

![image](https://github.com/wooly905/SakilaSpringBootMVC/assets/18693681/fcf40013-4e3b-4e97-b889-1b758900c657)

Countries and cities list

![image](https://github.com/wooly905/SakilaSpringBootMVC/assets/18693681/e8aaccc5-5a6f-4b65-9c6b-d42308f43162)

Language list with add, edit, and delete action

![image](https://github.com/wooly905/SakilaSpringBootMVC/assets/18693681/edfa899f-6adf-4c60-9db3-223dd89537a0)

Rental list by customer, film, and date interval

![image](https://github.com/wooly905/SakilaSpringBootMVC/assets/18693681/18775bc1-8d9d-4a6d-938d-d6495d30b8e0)

Staff list with home address and store address

![image](https://github.com/wooly905/SakilaSpringBootMVC/assets/18693681/1aad6069-c2b1-4fe8-94b6-05b2ab041207)

Specific staff 

![image](https://github.com/wooly905/SakilaSpringBootMVC/assets/18693681/edd849a9-0923-49b7-b958-1000728b6ea5)

Customer list with email and address

![image](https://github.com/wooly905/SakilaSpringBootMVC/assets/18693681/939601a2-163f-4a4b-8699-dd2bc51965d1)

## Load testing with Gatling

Load tests are written in Java with the [Gatling Java DSL](https://docs.gatling.io/reference/script/language/java/) and run through the Gatling Maven plugin.

Two simulations are provided in `src/test/java/sakila/demo`:

- `SakilaBrowseSimulation` — a realistic user journey through the read-only pages (default).
- `SakilaHeavyReadSimulation` — a constant-rate stress test of the heaviest pages.

### Run a load test

1. Start MySQL with the Sakila database and launch the application (`mvn spring-boot:run`).
2. In another terminal, run:

```bash
mvn gatling:test
```

Gatling writes an HTML report under `target/gatling/<simulation>-<timestamp>/index.html`. The build fails if the assertions are not met (p95 response time and success rate), or if the application is not reachable.

### Useful options

```bash
# Target a different host/port
mvn gatling:test -Dgatling.baseUrl=http://localhost:9090

# Change the injected load (default: 20 users / 20 users-per-second)
mvn gatling:test -Dgatling.users=50
mvn gatling:test -Dgatling.rate=50

# Shorten the injection profile (useful for a quick smoke test)
mvn gatling:test -Dgatling.rampSeconds=1 -Dgatling.holdSeconds=1
mvn gatling:test -Dgatling.durationSeconds=5

# Run only one simulation
mvn gatling:test -Dgatling.simulationClass=gatling.sakila.demo.SakilaHeavyReadSimulation
```

The film detail requests use IDs from `src/test/resources/film-ids.csv`. All load-test traffic is read-only (GET requests), so it does not modify the Sakila database.

## Observability (Prometheus + Grafana)

The application exposes its metrics with Spring Boot Actuator and Micrometer. Prometheus scrapes them every 5 seconds and Grafana displays them in a provisioned dashboard built from the KPIs of the `03_KPI.pdf` document.

```
Gatling --load--> Sakila (Spring Boot :8080) <--scrape-- Prometheus (:9090) --> Grafana (:3000)
```

### Setup

Prerequisites: JDK 21, Docker, and the Sakila MySQL database running on port 3306 (the application still uses MySQL).

```bash
# Start Prometheus, Grafana and Alertmanager (the "observability" profile is required)
docker compose --profile observability up -d prometheus grafana alertmanager

# Start the application (dev profile, port 8080)
mvn spring-boot:run
```

Do not run a plain `docker compose up -d`: the compose file also contains a `postgres` service for the lab environment, which Sakila does not use.

| Service      | URL                   | Credentials |
|--------------|-----------------------|-------------|
| Application  | http://localhost:8080 | -           |
| Prometheus   | http://localhost:9090 | -           |
| Grafana      | http://localhost:3000 | perf / perf |
| Alertmanager | http://localhost:9093 | -           |

### Exposed metrics

Configured in `application.properties` (Actuator endpoints `health`, `info`, `metrics`, `prometheus`, health probes, HTTP histograms with p50/p95/p99, Hibernate statistics).

| URL                                             | Content                          |
|-------------------------------------------------|----------------------------------|
| `/actuator/health` (`/liveness`, `/readiness`)  | Application health               |
| `/actuator/metrics`                             | List of available metrics        |
| `/actuator/prometheus`                          | All metrics in Prometheus format |

Custom metrics (`src/main/java/sakila/demo/metrics`):

| Metric                                                          | Meaning                                                    |
|-----------------------------------------------------------------|------------------------------------------------------------|
| `sakila_sql_execution_seconds{type,result}`                     | SQL duration, count and errors (JDBC proxy)                |
| `sakila_transaction_seconds{result}`                            | Transaction duration and count                             |
| `hibernate_*`                                                   | Hibernate statistics (queries, entities loaded, ...)       |
| `sakila_operation_total`, `_duration_seconds`, `_inflight`      | Business operations: count, duration, success/failure, in progress |
| `sakila_records_processed_total`                                | Volume of data processed                                   |

Instrumented operations: `film.list`, `film.view`, `film.add`, `rental.list`, `actor.preload`. HikariCP, JVM, GC and system metrics come from Micrometer.

### Dashboard and alerts

- Grafana: Dashboards > Sakila > *Sakila - KPI de performance*. It shows the 15 priority KPIs, then the API, JVM, GC, database, system, business and availability sections. It is generated from `docker/grafana/provisioning/dashboards/json/sakila-kpi.json`.
- Alert rules are in `docker/prometheus/alerts.yml` and use the thresholds of the document (P95 > 500 ms, P99 > 1 s, 5xx > 1 %, CPU > 80 %, heap > 85 %, GC > 10 %, DB pool > 85 %, pending connections > 0, availability < 99.9 %). They are visible at http://localhost:9090/alerts.
- Latency and throughput panels stay empty until there is traffic: run a Gatling simulation (see above) and watch the dashboard on *Last 15 minutes*.

### Limitations and troubleshooting

- RAM, disk I/O and network I/O require `node_exporter` or cAdvisor, which are not installed.
- Open file descriptors are not available on Windows.
- Alertmanager has no receiver configured: alerts are only visible in the Prometheus and Alertmanager UIs.
- Prometheus target DOWN: check that the application runs on port 8080 (the `production` profile uses 8082) at http://localhost:9090/targets.
- Grafana not reachable: the `observability` compose profile was probably not enabled.
