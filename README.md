# User Service

Микросервис аутентификации и управления пользователями: регистрация, JWT access/refresh токены, роли.

## Быстрый старт

### Dev

Запустить инфраструктуру:

```bash
docker-compose up -d postgres redis
```

Запустить сервис:

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### Prod

> Перед запуском замените `jwt.secret` в `application.yml` на надёжный секрет (≥32
> символа).

```bash
./gradlew bootJar
docker-compose --profile app up -d
```

## Адреса

| Сервис       | URL                                         |
|--------------|---------------------------------------------|
| User Service | http://localhost:8083                       |
| Swagger UI   | http://localhost:8083/swagger-ui/index.html |
| PostgreSQL   | localhost:5436                              |
| Redis        | localhost:6381                              |
| Prometheus   | http://localhost:9094                       |
| Grafana      | http://localhost:3002                       |

## Тесты

```bash
./gradlew test                # unit
./gradlew integrationTest     # integration (требует Docker)
```