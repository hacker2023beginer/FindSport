# FindSport

Веб-платформа поиска спортивных мероприятий на территории Республики Беларусь.

## Запуск

```bash
cp src/main/resources/application-example.yml src/main/resources/application.yml
docker compose up -d        # PostgreSQL 17 и Mailpit (тестовый почтовый сервер)
ADMIN_EMAIL=admin@findsport.by ADMIN_PASSWORD='Admin-pass-2026' mvn spring-boot:run
```

- Веб-интерфейс: http://localhost:8080 (вход, регистрация, личный кабинет)
- Письма подтверждения и восстановления доступа: http://localhost:8025 (Mailpit)
- Документация API: http://localhost:8080/swagger-ui.html
- Тесты: `mvn test` (нужен запущенный Docker — Testcontainers)

`ADMIN_EMAIL`/`ADMIN_PASSWORD` нужны только при первом запуске: создается первичный администратор,
который назначает роли модератора и администратора (`/api/v1/admin/users`).

## Переменные окружения

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | как в `docker-compose.yml` | PostgreSQL |
| `MAIL_HOST`, `MAIL_PORT` | `localhost`, `1025` | SMTP (локально — Mailpit) |
| `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_STARTTLS` | пусто / `false` | SMTP с авторизацией |
| `MAIL_FROM` | `no-reply@findsport.by` | Отправитель писем |
| `PUBLIC_BASE_URL` | `http://localhost:8080` | Адрес интерфейса для ссылок в письмах |
| `SESSION_COOKIE_SECURE` | `false` | `true` в среде с HTTPS |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | пусто | Первичный администратор |
