package by.findsport.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import by.findsport.domain.CardStatus;
import by.findsport.domain.Provider;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Карточки мероприятий на реальном PostgreSQL: приемочный сценарий «Создание карточки» (ТЗ Приложение 3),
 * повторная модерация, допустимые переходы статусов, архивирование, журнал решений,
 * матрица ролей (Приложение 2) и валидация входящих данных (п. 4.2.13, 6.7).
 */
class EventApiIntegrationTests extends CardApiTestSupport {

    @Test
    void guestCanSearchAndSeeDictionaries() throws Exception {
        mvc.perform(get("/api/v1/events")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/regions").header("Accept-Language", "en")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Brest Region")));
    }

    @Test
    void cardGoesFromDraftThroughModerationToPublicSearch() throws Exception {
        long eventId = createDraft(eventJson("Турнир по мини-футболу", "+375291234567", "org@example.by"));

        // Черновик не виден гостю
        mvc.perform(get("/api/v1/events/{id}", eventId)).andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/provider/events/{id}/submit", eventId).with(as(PROVIDER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_MODERATION"));

        mvc.perform(get("/api/v1/moderation/events").with(as(MODERATOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].eventId", hasItem((int) eventId)));
        mvc.perform(get("/api/v1/moderation/events/{id}", eventId).with(as(MODERATOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvedVersion").isEmpty())
                .andExpect(jsonPath("$.submittedVersion.title.ru").value("Турнир по мини-футболу"))
                .andExpect(jsonPath("$.changedFields", hasItems("title", "startsAt")));

        mvc.perform(post("/api/v1/moderation/events/{id}/approve", eventId).with(as(MODERATOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // Регистронезависимый поиск по частичному совпадению (п. 4.7.4) + фильтры (п. 4.7.2)
        mvc.perform(get("/api/v1/events")
                        .param("q", "МИНИ-ФУТ")
                        .param("city", "минск")
                        .param("sportId", football.getId().toString())
                        .param("age", "12")
                        .param("priceMax", "30")
                        .param("hasFreeSeats", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Турнир по мини-футболу"))
                .andExpect(jsonPath("$.content[0].organizer").value("ФК Звезда"))
                .andExpect(jsonPath("$.content[0].minPrice").value(25.00))
                .andExpect(jsonPath("$.content[0].currency").value("BYN"));
        // Поиск по названию вида спорта (п. 4.7.1)
        mvc.perform(get("/api/v1/events").param("q", "футбол"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/events").param("age", "40"))
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/api/v1/events/{id}", eventId).header("Accept-Language", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Mini-football tournament"))
                // Английского описания нет — используется русское (п. 5.8)
                .andExpect(jsonPath("$.description").value("Открытый турнир для детских команд"))
                .andExpect(jsonPath("$.freeSeats").value(20))
                .andExpect(jsonPath("$.offers[0].currency").value("BYN"))
                .andExpect(jsonPath("$.registrationOpen").value(true));
    }

    @Test
    void publishedVersionStaysPublicWhileEssentialChangesAreModerated() throws Exception {
        long eventId = publish(eventJson("Турнир", "+375291234567", null));

        // Контакты — несущественное изменение: применяется сразу
        mvc.perform(put("/api/v1/provider/events/{id}", eventId).with(as(PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("Турнир", "+375297654321", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.contactPhone").value("+375297654321"))
                .andExpect(jsonPath("$.revision").isEmpty());

        // Название — существенное изменение (п. 4.14.1): уходит на модерацию, карточка остается опубликованной
        mvc.perform(put("/api/v1/provider/events/{id}", eventId).with(as(PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("Кубок города", "+375297654321", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.title.ru").value("Турнир"))
                .andExpect(jsonPath("$.revision.status").value("ON_MODERATION"))
                .andExpect(jsonPath("$.revision.statusLabel").value("На модерации"))
                .andExpect(jsonPath("$.revision.proposedVersion.title.ru").value("Кубок города"));

        // Гость до одобрения видит прежнюю версию
        mvc.perform(get("/api/v1/events/{id}", eventId))
                .andExpect(jsonPath("$.title").value("Турнир"));

        // Повторные правки до решения модератора запрещены
        mvc.perform(put("/api/v1/provider/events/{id}", eventId).with(as(PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("Кубок области", "+375297654321", null)))
                .andExpect(status().isConflict());

        // Diff-режим сравнивает с действующей публичной версией
        mvc.perform(get("/api/v1/moderation/events").with(as(MODERATOR)))
                .andExpect(jsonPath("$.content[0].eventId").value((int) eventId))
                .andExpect(jsonPath("$.content[0].revision").value(true));
        mvc.perform(get("/api/v1/moderation/events/{id}", eventId).with(as(MODERATOR)))
                .andExpect(jsonPath("$.approvedVersion.title.ru").value("Турнир"))
                .andExpect(jsonPath("$.submittedVersion.title.ru").value("Кубок города"))
                // Контакты уже применены к публичной версии и в отличия не попадают
                .andExpect(jsonPath("$.changedFields", hasItem("title")))
                .andExpect(jsonPath("$.changedFields", not(hasItem("contactPhone"))));

        mvc.perform(post("/api/v1/moderation/events/{id}/approve", eventId).with(as(MODERATOR)))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(get("/api/v1/events/{id}", eventId))
                .andExpect(jsonPath("$.title").value("Кубок города"))
                .andExpect(jsonPath("$.offers.length()").value(2));
        mvc.perform(get("/api/v1/provider/events/{id}", eventId).with(as(PROVIDER)))
                .andExpect(jsonPath("$.revision").isEmpty());
    }

    @Test
    void rejectedRevisionKeepsPublicVersionAndNeedsRevisionForAuthor() throws Exception {
        long eventId = publish(eventJson("Турнир", "+375291234567", null));
        mvc.perform(put("/api/v1/provider/events/{id}", eventId).with(as(PROVIDER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventJson("Кубок города", "+375291234567", null)));

        mvc.perform(post("/api/v1/moderation/events/{id}/reject", eventId).with(as(MODERATOR))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Название вводит в заблуждение\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mvc.perform(get("/api/v1/events/{id}", eventId))
                .andExpect(jsonPath("$.title").value("Турнир"));
        mvc.perform(get("/api/v1/provider/events").with(as(PROVIDER)))
                .andExpect(jsonPath("$.content[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.content[0].revision.status").value("REJECTED"))
                .andExpect(jsonPath("$.content[0].revision.statusLabel").value("Требует доработки"))
                .andExpect(jsonPath("$.content[0].revision.rejectionReason").value("Название вводит в заблуждение"));

        // После отклонения автор может прислать исправленные правки
        mvc.perform(put("/api/v1/provider/events/{id}", eventId).with(as(PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("Кубок Минска", "+375291234567", null)))
                .andExpect(jsonPath("$.revision.status").value("ON_MODERATION"));
    }

    @Test
    void rejectionRequiresReasonAndIsShownToAuthor() throws Exception {
        long eventId = createDraft(eventJson("Турнир", "+375291234567", null));
        mvc.perform(post("/api/v1/provider/events/{id}/submit", eventId).with(as(PROVIDER)));

        mvc.perform(post("/api/v1/moderation/events/{id}/reject", eventId).with(as(MODERATOR))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"  \"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));

        mvc.perform(post("/api/v1/moderation/events/{id}/reject", eventId).with(as(MODERATOR))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Нет программы турнира\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        // В БД — «Отклонена», автору — «Требует доработки» (решение PM)
        mvc.perform(get("/api/v1/provider/events").with(as(PROVIDER)))
                .andExpect(jsonPath("$.content[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.content[0].statusLabel").value("Требует доработки"))
                .andExpect(jsonPath("$.content[0].rejectionReason").value("Нет программы турнира"));
        mvc.perform(get("/api/v1/provider/events").with(as(PROVIDER)).header("Accept-Language", "en"))
                .andExpect(jsonPath("$.content[0].statusLabel").value("Needs revision"));
    }

    @Test
    void invalidContactsAreRejectedWith422() throws Exception {
        mvc.perform(post("/api/v1/provider/events").with(as(PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("Турнир", "80291234567", "not-an-email")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[*].field", hasItems("contactPhone", "contactEmail")));

        mvc.perform(post("/api/v1/provider/events").with(as(PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("Турнир", null, null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[*].field", hasItem("contactPresent")));
    }

    @Test
    void roleMatrixIsEnforced() throws Exception {
        String body = eventJson("Турнир", "+375291234567", null);
        mvc.perform(post("/api/v1/provider/events").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/provider/events").with(as(USER))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/moderation/events").with(as(PROVIDER)))
                .andExpect(status().isForbidden());
        // Изменяющий запрос без CSRF-токена отклоняется даже при действующей сессии (п. 4.13.5)
        mvc.perform(post("/api/v1/provider/events").with(user(PROVIDER).roles("PROVIDER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    /** Допустимые переходы статусов (ТЗ п. 4.2.8): недопустимое действие — 409 без изменения карточки. */
    @Test
    void onlyAllowedStatusTransitionsArePossible() throws Exception {
        long eventId = createDraft(eventJson("Турнир", "+375291234567", null));

        // Черновик нельзя опубликовать или отклонить, минуя отправку на модерацию
        mvc.perform(post("/api/v1/moderation/events/{id}/approve", eventId).with(as(MODERATOR)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/moderation/events/{id}/reject", eventId).with(as(MODERATOR))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Нет программы\"}"))
                .andExpect(status().isConflict());
        // Архивируется только опубликованная карточка
        mvc.perform(post("/api/v1/provider/events/{id}/archive", eventId).with(as(PROVIDER)))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/provider/events/{id}/submit", eventId).with(as(PROVIDER)))
                .andExpect(jsonPath("$.status").value("ON_MODERATION"));
        // На модерации карточку нельзя менять или отправлять повторно
        mvc.perform(put("/api/v1/provider/events/{id}", eventId).with(as(PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON).content(eventJson("Кубок", "+375291234567", null)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/provider/events/{id}/submit", eventId).with(as(PROVIDER)))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/v1/moderation/events/{id}/approve", eventId).with(as(MODERATOR)))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        // Повторное решение по уже рассмотренной отправке невозможно
        mvc.perform(post("/api/v1/moderation/events/{id}/approve", eventId).with(as(MODERATOR)))
                .andExpect(status().isConflict());
        // У опубликованной карточки без изменений материалов отправлять на модерацию нечего
        mvc.perform(post("/api/v1/provider/events/{id}/submit", eventId).with(as(PROVIDER)))
                .andExpect(status().isConflict());
    }

    @Test
    void archivedCardIsHiddenFromVisitorsAndFrozen() throws Exception {
        long eventId = publish(eventJson("Турнир", "+375291234567", null));

        // Пока правки на модерации, архивировать нельзя — решение модератора не должно потеряться
        mvc.perform(put("/api/v1/provider/events/{id}", eventId).with(as(PROVIDER))
                .contentType(MediaType.APPLICATION_JSON).content(eventJson("Кубок", "+375291234567", null)));
        mvc.perform(post("/api/v1/provider/events/{id}/archive", eventId).with(as(PROVIDER)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/moderation/events/{id}/reject", eventId).with(as(MODERATOR))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Не меняйте название\"}"));

        mvc.perform(post("/api/v1/provider/events/{id}/archive", eventId).with(as(PROVIDER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.statusLabel").value("Архивирована"));

        mvc.perform(get("/api/v1/events/{id}", eventId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/events")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(put("/api/v1/provider/events/{id}", eventId).with(as(PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON).content(eventJson("Турнир", "+375291234567", null)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/provider/events/{id}/submit", eventId).with(as(PROVIDER)))
                .andExpect(status().isConflict());
    }

    /** Модератор и администратор могут архивировать чужую карточку (п. 5.7, Приложение 2 *). */
    @Test
    void moderatorCanArchivePublishedCard() throws Exception {
        long eventId = publish(eventJson("Турнир", "+375291234567", null));

        mvc.perform(post("/api/v1/provider/events/{id}/archive", eventId).with(as(MODERATOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    /** Запрет управления чужими карточками (п. 4.13.1): проверка на сервере по владельцу профиля. */
    @Test
    void providerCannotManageForeignCards() throws Exception {
        long eventId = publish(eventJson("Турнир", "+375291234567", null));
        String body = eventJson("Захват карточки", "+375291234567", null);

        mvc.perform(get("/api/v1/provider/events/{id}", eventId).with(as(OTHER_PROVIDER)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/provider/events/{id}", eventId).with(as(OTHER_PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/provider/events/{id}/submit", eventId).with(as(OTHER_PROVIDER)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/provider/events/{id}/archive", eventId).with(as(OTHER_PROVIDER)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/provider/events/{id}/videos", eventId).with(as(OTHER_PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://drive.google.com/file/d/abc/view\"}"))
                .andExpect(status().isForbidden());
        // Чужие карточки не попадают в список кабинета
        mvc.perform(get("/api/v1/provider/events").with(as(OTHER_PROVIDER)))
                .andExpect(jsonPath("$.totalElements").value(0));
        // Создать карточку от имени чужого профиля нельзя
        mvc.perform(post("/api/v1/provider/events").with(as(OTHER_PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/events/{id}", eventId)).andExpect(jsonPath("$.title").value("Турнир"));
    }

    /** Карточка публикуется только от имени подтвержденного организатора (п. 4.10.5). */
    @Test
    void cardOfUnconfirmedProviderIsNotPublished() throws Exception {
        Provider unconfirmed = createProvider(users.findByEmail(PROVIDER).orElseThrow(), CardStatus.DRAFT);
        long eventId = createDraft(eventJson(unconfirmed.getId(), "Турнир", "+375291234567", null));
        mvc.perform(post("/api/v1/provider/events/{id}/submit", eventId).with(as(PROVIDER)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/moderation/events/{id}/approve", eventId).with(as(MODERATOR)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Профиль организатора не подтвержден — сначала одобрите профиль поставщика"));
        mvc.perform(get("/api/v1/events/{id}", eventId)).andExpect(status().isNotFound());

        // Отклонить с причиной модератор может и до подтверждения организатора
        mvc.perform(post("/api/v1/moderation/events/{id}/reject", eventId).with(as(MODERATOR))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Организатор не подтвержден\"}"))
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    /** Журнал решений модератора (п. 4.13.4) и автоматические проверки отправки (п. 4.14.2). */
    @Test
    void moderatorDecisionsAreJournaledAndSubmissionsAreAutoChecked() throws Exception {
        long eventId = createDraft(eventJson("Турнир", "+375291234567", null));
        mvc.perform(post("/api/v1/provider/events/{id}/submit", eventId).with(as(PROVIDER)));

        mvc.perform(get("/api/v1/moderation/events/{id}", eventId).with(as(MODERATOR)))
                .andExpect(jsonPath("$.checks[?(@.code == 'REQUIRED_FIELDS')].passed").value(true))
                .andExpect(jsonPath("$.checks[?(@.code == 'STARTS_IN_FUTURE')].passed").value(true))
                .andExpect(jsonPath("$.checks[?(@.code == 'FILES')].passed").value(true))
                .andExpect(jsonPath("$.media.length()").value(0));

        mvc.perform(post("/api/v1/moderation/events/{id}/reject", eventId).with(as(MODERATOR))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Добавьте программу\"}"));
        mvc.perform(post("/api/v1/provider/events/{id}/submit", eventId).with(as(PROVIDER)));
        mvc.perform(post("/api/v1/moderation/events/{id}/approve", eventId).with(as(MODERATOR)));

        long moderatorId = users.findByEmail(MODERATOR).orElseThrow().getId();
        long authorId = users.findByEmail(PROVIDER).orElseThrow().getId();
        mvc.perform(get("/api/v1/moderation/events/{id}/history", eventId).with(as(MODERATOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].decision").value("APPROVED"))
                .andExpect(jsonPath("$[0].moderatorId").value(moderatorId))
                .andExpect(jsonPath("$[0].submittedById").value(authorId))
                .andExpect(jsonPath("$[0].decidedAt").isNotEmpty())
                .andExpect(jsonPath("$[1].decision").value("REJECTED"))
                .andExpect(jsonPath("$[1].reason").value("Добавьте программу"));
        // Журнал — только для модераторов и администраторов
        mvc.perform(get("/api/v1/moderation/events/{id}/history", eventId).with(as(PROVIDER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void autoCheckReportsMissingAgeCensus() throws Exception {
        String body = eventJson("Турнир", "+375291234567", null).replace("\"ageMin\":10,", "").replace("\"ageMax\":14,", "");
        long eventId = createDraft(body);
        mvc.perform(post("/api/v1/provider/events/{id}/submit", eventId).with(as(PROVIDER)));

        mvc.perform(get("/api/v1/moderation/events/{id}", eventId).with(as(MODERATOR)))
                .andExpect(jsonPath("$.checks[?(@.code == 'REQUIRED_FIELDS')].passed").value(false))
                .andExpect(jsonPath("$.checks[?(@.code == 'REQUIRED_FIELDS')].message").value("Не заполнено: возрастной ценз"));
    }

    /** Обязательные поля карточки (п. 5.3, 4.14.2): без них черновик не сохраняется. */
    @Test
    void requiredEventFieldsAreValidated() throws Exception {
        var body = json.createObjectNode().put("providerId", provider.getId());
        body.putObject("title").put("ru", " ");
        mvc.perform(post("/api/v1/provider/events").with(as(PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[*].field", hasItems("type", "title.ru", "description", "conditions",
                        "sportIds", "startsAt", "regionId", "address", "offers")));
    }

    @Test
    void swaggerDocumentationIsPublic() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("FindSport API"))
                .andExpect(jsonPath("$.paths['/api/v1/events']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/moderation/events/{id}/reject']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/moderation/providers/{id}/approve']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/provider/events/{id}/images']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.sessionCookie.name").value("JSESSIONID"));
    }
}
