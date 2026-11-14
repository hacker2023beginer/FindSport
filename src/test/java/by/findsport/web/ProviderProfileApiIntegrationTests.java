package by.findsport.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Профили организаций и тренеров (ТЗ п. 4.10.3–4.10.6, 5.2) и их подтверждение модератором (п. 4.10.5):
 * обязательные сведения, жизненный цикл статусов, повторная модерация существенных правок,
 * объем публичных контактов и запрет управления чужими профилями.
 */
class ProviderProfileApiIntegrationTests extends CardApiTestSupport {

    @Test
    void providerCreatesAndEditsDraftProfile() throws Exception {
        long id = createProfile(profile("Анна Ковалёва", "TRAINER"));

        mvc.perform(get("/api/v1/provider/profiles/{id}", id).with(as(PROVIDER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("TRAINER"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.statusLabel").value("Черновик"))
                .andExpect(jsonPath("$.serviceArea.ru").value("Минск и Минский район"))
                .andExpect(jsonPath("$.address").isEmpty());

        // Черновик изменяется сразу
        mvc.perform(put("/api/v1/provider/profiles/{id}", id).with(as(PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(profile("Анна Ковалёва, тренер по плаванию", "TRAINER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name.ru").value("Анна Ковалёва, тренер по плаванию"))
                .andExpect(jsonPath("$.revision").isEmpty());
        mvc.perform(get("/api/v1/provider/profiles").with(as(PROVIDER)))
                .andExpect(jsonPath("$[*].id", hasItem((int) id)));
        // Неподтвержденный профиль посетителям не виден
        mvc.perform(get("/api/v1/providers/{id}", id)).andExpect(status().isNotFound());
    }

    /** Обязательные сведения по п. 5.2: адрес или территория, контакты, режим работы, виды спорта. */
    @Test
    void requiredProfileFieldsAreValidated() throws Exception {
        ObjectNode body = profile("Клуб", "ORGANIZATION");
        body.remove("serviceArea");
        body.remove("contactEmail");
        body.remove("workingHours");
        body.putArray("sportIds");
        body.put("contactPhone", "80291234567");

        mvc.perform(post("/api/v1/provider/profiles").with(as(PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[*].field",
                        hasItems("locationPresent", "workingHours", "sportIds", "contactPhone")));

        body = profile("Клуб", "ORGANIZATION");
        body.put("regionId", 999_999);
        mvc.perform(post("/api/v1/provider/profiles").with(as(PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("regionId"));
    }

    @Test
    void profileIsConfirmedByModeratorAndShownWithAllowedContactsOnly() throws Exception {
        ObjectNode body = profile("Беговой клуб «Старт»", "ORGANIZATION");
        body.put("contactPhone", "+375291112233").put("phonePublic", true).put("emailPublic", false);
        long id = createProfile(body);

        mvc.perform(post("/api/v1/provider/profiles/{id}/submit", id).with(as(PROVIDER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_MODERATION"))
                .andExpect(jsonPath("$.statusLabel").value("На модерации"));
        // На модерации профиль не редактируется и повторно не отправляется
        mvc.perform(put("/api/v1/provider/profiles/{id}", id).with(as(PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/provider/profiles/{id}/submit", id).with(as(PROVIDER)))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/v1/moderation/providers").with(as(MODERATOR)))
                .andExpect(jsonPath("$.content[*].providerId", hasItem((int) id)))
                .andExpect(jsonPath("$.content[0].revision").value(false));
        mvc.perform(get("/api/v1/moderation/providers/{id}", id).with(as(MODERATOR)))
                .andExpect(jsonPath("$.approvedVersion").isEmpty())
                .andExpect(jsonPath("$.submittedVersion.name.ru").value("Беговой клуб «Старт»"))
                .andExpect(jsonPath("$.changedFields", hasItems("name", "description")));
        mvc.perform(post("/api/v1/moderation/providers/{id}/approve", id).with(as(MODERATOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // Контакты — только разрешенные владельцем (п. 4.10.6), учетная запись владельца не раскрывается
        mvc.perform(get("/api/v1/providers/{id}", id).header("Accept-Language", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Беговой клуб «Старт»"))
                .andExpect(jsonPath("$.region").value("Minsk"))
                .andExpect(jsonPath("$.sports[0].name").value("Football"))
                .andExpect(jsonPath("$.contactPhone").value("+375291112233"))
                .andExpect(jsonPath("$.contactEmail").doesNotExist())
                .andExpect(jsonPath("$.owner").doesNotExist());
    }

    @Test
    void rejectionRequiresReasonAndProfileCanBeResubmitted() throws Exception {
        long id = createProfile(profile("Клуб", "ORGANIZATION"));
        mvc.perform(post("/api/v1/provider/profiles/{id}/submit", id).with(as(PROVIDER)));

        reject(id, " ").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        reject(id, "Укажите полное наименование организации")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.statusLabel").value("Требует доработки"));

        mvc.perform(get("/api/v1/provider/profiles/{id}", id).with(as(PROVIDER)))
                .andExpect(jsonPath("$.statusLabel").value("Требует доработки"))
                .andExpect(jsonPath("$.rejectionReason").value("Укажите полное наименование организации"));

        mvc.perform(put("/api/v1/provider/profiles/{id}", id).with(as(PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(profile("ООО «Спортивный клуб»", "ORGANIZATION"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/provider/profiles/{id}/submit", id).with(as(PROVIDER)))
                .andExpect(jsonPath("$.status").value("ON_MODERATION"));
        mvc.perform(post("/api/v1/moderation/providers/{id}/approve", id).with(as(MODERATOR)))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(get("/api/v1/provider/profiles/{id}", id).with(as(PROVIDER)))
                .andExpect(jsonPath("$.rejectionReason").isEmpty());

        mvc.perform(get("/api/v1/moderation/providers/{id}/history", id).with(as(MODERATOR)))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].decision").value("APPROVED"))
                .andExpect(jsonPath("$[1].decision").value("REJECTED"))
                .andExpect(jsonPath("$[1].reason").value("Укажите полное наименование организации"));
    }

    @Test
    void essentialChangesOfPublishedProfileWaitForApproval() throws Exception {
        long id = publishProfile(profile("Клуб «Старт»", "ORGANIZATION"));

        // Режим работы — несущественное изменение: применяется сразу
        ObjectNode hours = profile("Клуб «Старт»", "ORGANIZATION");
        hours.putObject("workingHours").put("ru", "Ежедневно 8:00–22:00");
        mvc.perform(put("/api/v1/provider/profiles/{id}", id).with(as(PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(hours)))
                .andExpect(jsonPath("$.workingHours.ru").value("Ежедневно 8:00–22:00"))
                .andExpect(jsonPath("$.revision").isEmpty());

        // Наименование — существенное изменение (п. 4.14.1)
        ObjectNode renamed = hours.deepCopy();
        renamed.putObject("name").put("ru", "Клуб «Финиш»");
        mvc.perform(put("/api/v1/provider/profiles/{id}", id).with(as(PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(renamed)))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.name.ru").value("Клуб «Старт»"))
                .andExpect(jsonPath("$.revision.status").value("ON_MODERATION"))
                .andExpect(jsonPath("$.revision.proposedVersion.name.ru").value("Клуб «Финиш»"));
        mvc.perform(get("/api/v1/providers/{id}", id))
                .andExpect(jsonPath("$.name").value("Клуб «Старт»"))
                .andExpect(jsonPath("$.workingHours").value("Ежедневно 8:00–22:00"));
        // Пока правки ждут решения, архивировать и снова править нельзя
        mvc.perform(post("/api/v1/provider/profiles/{id}/archive", id).with(as(PROVIDER)))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/v1/moderation/providers/{id}", id).with(as(MODERATOR)))
                .andExpect(jsonPath("$.approvedVersion.name.ru").value("Клуб «Старт»"))
                .andExpect(jsonPath("$.changedFields", hasItem("name")))
                .andExpect(jsonPath("$.changedFields", not(hasItem("workingHours"))));

        // Отклонение правок не меняет публичный профиль
        reject(id, "Наименование не совпадает с документами").andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(get("/api/v1/providers/{id}", id)).andExpect(jsonPath("$.name").value("Клуб «Старт»"));
        mvc.perform(get("/api/v1/provider/profiles/{id}", id).with(as(PROVIDER)))
                .andExpect(jsonPath("$.revision.status").value("REJECTED"))
                .andExpect(jsonPath("$.revision.statusLabel").value("Требует доработки"));

        mvc.perform(put("/api/v1/provider/profiles/{id}", id).with(as(PROVIDER))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(renamed)));
        mvc.perform(post("/api/v1/moderation/providers/{id}/approve", id).with(as(MODERATOR)));
        mvc.perform(get("/api/v1/providers/{id}", id)).andExpect(jsonPath("$.name").value("Клуб «Финиш»"));
    }

    @Test
    void archivedProfileIsHiddenFromVisitors() throws Exception {
        long id = createProfile(profile("Клуб", "ORGANIZATION"));
        mvc.perform(post("/api/v1/provider/profiles/{id}/archive", id).with(as(PROVIDER)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/provider/profiles/{id}/submit", id).with(as(PROVIDER)));
        mvc.perform(post("/api/v1/moderation/providers/{id}/approve", id).with(as(MODERATOR)));

        mvc.perform(post("/api/v1/provider/profiles/{id}/archive", id).with(as(PROVIDER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
        mvc.perform(get("/api/v1/providers/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/provider/profiles/{id}", id).with(as(PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(profile("Клуб", "ORGANIZATION"))))
                .andExpect(status().isConflict());
    }

    @Test
    void foreignProfilesCannotBeManaged() throws Exception {
        long id = createProfile(profile("Клуб", "ORGANIZATION"));
        String body = json.writeValueAsString(profile("Чужой клуб", "ORGANIZATION"));

        mvc.perform(get("/api/v1/provider/profiles/{id}", id).with(as(OTHER_PROVIDER)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/provider/profiles/{id}", id).with(as(OTHER_PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/provider/profiles/{id}/submit", id).with(as(OTHER_PROVIDER)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/provider/profiles/{id}/archive", id).with(as(OTHER_PROVIDER)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/provider/profiles").with(as(OTHER_PROVIDER)))
                .andExpect(jsonPath("$.length()").value(0));

        // Пользователь без роли поставщика не создает профили; модерация — только модератору
        mvc.perform(post("/api/v1/provider/profiles").with(as(USER))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/moderation/providers/{id}/approve", id).with(as(PROVIDER)))
                .andExpect(status().isForbidden());
    }

    private long publishProfile(ObjectNode body) throws Exception {
        long id = createProfile(body);
        mvc.perform(post("/api/v1/provider/profiles/{id}/submit", id).with(as(PROVIDER)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/moderation/providers/{id}/approve", id).with(as(MODERATOR)))
                .andExpect(status().isOk());
        return id;
    }

    private long createProfile(ObjectNode body) throws Exception {
        String response = mvc.perform(post("/api/v1/provider/profiles").with(as(PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        return idOf(response);
    }

    private ResultActions reject(long id, String reason) throws Exception {
        return mvc.perform(post("/api/v1/moderation/providers/{id}/reject", id).with(as(MODERATOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(json.createObjectNode().put("reason", reason))));
    }

    private ObjectNode profile(String name, String type) {
        ObjectNode body = json.createObjectNode()
                .put("type", type)
                .put("regionId", minsk.getId())
                .put("contactEmail", "coach@example.by")
                .put("emailPublic", true);
        body.putObject("name").put("ru", name);
        body.putObject("description").put("ru", "Тренировки для детей и взрослых");
        body.putObject("workingHours").put("ru", "Пн–Пт 9:00–18:00");
        body.putObject("serviceArea").put("ru", "Минск и Минский район");
        body.putArray("sportIds").add(football.getId());
        return body;
    }
}
