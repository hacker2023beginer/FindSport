package by.findsport.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import by.findsport.TestFiles;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Фотографии, документы и видеоссылки карточки (ТЗ п. 4.1.7, 4.11.6, 4.12.6, 4.13.7): проверка файлов
 * по типу, расширению, размеру и содержимому; добавление и удаление материалов опубликованной карточки —
 * существенное изменение (п. 4.14.1), до одобрения посетители видят прежний состав материалов.
 */
class EventMediaApiIntegrationTests extends CardApiTestSupport {

    private static final String DOCX_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final byte[] PHOTO = TestFiles.jpeg(150 * 1024);

    @Test
    void uploadedFilesAreValidatedBySizeExtensionAndContent() throws Exception {
        long eventId = createDraft(eventJson("Турнир", "+375291234567", null));

        // Меньше 0.1 МБ (п. 4.11.6)
        upload(eventId, "images", "photo.jpg", "image/jpeg", fakeJpeg(10 * 1024))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("file"))
                .andExpect(jsonPath("$.errors[0].message").value("Размер файла должен быть от 100 КБ до 5 МБ"));
        // Расширение jpg, но содержимое — не JPEG
        upload(eventId, "images", "photo.jpg", "image/jpeg", new byte[200 * 1024])
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].message").value("Содержимое файла не соответствует его формату"));
        // Сигнатура JPEG есть, но изображение не читается
        upload(eventId, "images", "photo.jpg", "image/jpeg", fakeJpeg(200 * 1024))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].message").value("Содержимое файла не соответствует его формату"));
        // Документы — только docx (п. 4.14.2)
        upload(eventId, "documents", "rules.pdf", "application/pdf", new byte[1024])
                .andExpect(status().isUnprocessableEntity());
        // ZIP без документа Word не выдается за docx
        upload(eventId, "documents", "rules.docx", DOCX_TYPE, zipWithout())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].message").value("Содержимое файла не соответствует его формату"));
        // Видео — только ссылки на Google Drive (п. 4.1.7)
        mvc.perform(post("/api/v1/provider/events/{id}/videos", eventId).with(as(PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"url\": \"https://youtube.com/watch?v=1\"}"))
                .andExpect(status().isUnprocessableEntity());

        upload(eventId, "images", "photo.JPG", "image/jpeg", PHOTO)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/provider/events/" + eventId + "/media/")))
                .andExpect(jsonPath("$.type").value("IMAGE"))
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.sizeBytes").value(PHOTO.length))
                .andExpect(jsonPath("$.change").value("ADDED"));
        upload(eventId, "documents", "Положение.docx", DOCX_TYPE, TestFiles.docx())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("Положение.docx"));
        mvc.perform(post("/api/v1/provider/events/{id}/videos", eventId).with(as(PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://drive.google.com/file/d/abc/view\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value("https://drive.google.com/file/d/abc/view"));

        mvc.perform(get("/api/v1/provider/events/{id}", eventId).with(as(PROVIDER)))
                .andExpect(jsonPath("$.media.length()").value(3));
    }

    /** Имя файла клиента используется только для отображения: путь и каталоги из него отбрасываются. */
    @Test
    void clientFileNameDoesNotAffectStoragePath() throws Exception {
        long eventId = createDraft(eventJson("Турнир", "+375291234567", null));

        String response = upload(eventId, "images", "../../static/evil.jpg", "image/jpeg", PHOTO)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("evil.jpg"))
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/v1/provider/events/{id}/media/{mediaId}", eventId, idOf(response)).with(as(PROVIDER)))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PHOTO));
    }

    @Test
    void mediaBecomesPublicOnlyAfterModeratorApproval() throws Exception {
        long eventId = createDraft(eventJson("Турнир", "+375291234567", null));
        long photoId = idOf(upload(eventId, "images", "afisha.jpg", "image/jpeg", PHOTO)
                .andReturn().getResponse().getContentAsString());
        long docId = idOf(upload(eventId, "documents", "rules.docx", DOCX_TYPE, TestFiles.docx())
                .andReturn().getResponse().getContentAsString());

        // Неопубликованный файл не отдается посетителю, но доступен автору и модератору
        mvc.perform(get("/api/v1/events/{id}/media/{mediaId}", eventId, photoId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/provider/events/{id}/media/{mediaId}", eventId, photoId).with(as(MODERATOR)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/provider/events/{id}/media/{mediaId}", eventId, photoId).with(as(OTHER_PROVIDER)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/provider/events/{id}/media/{mediaId}", eventId, photoId))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/provider/events/{id}/submit", eventId).with(as(PROVIDER)));
        mvc.perform(get("/api/v1/moderation/events/{id}", eventId).with(as(MODERATOR)))
                .andExpect(jsonPath("$.media[*].change", hasItem("ADDED")))
                .andExpect(jsonPath("$.media.length()").value(2))
                .andExpect(jsonPath("$.checks[?(@.code == 'FILES')].message").value(
                        hasItem("Файлов: 2 — формат и размер соответствуют требованиям")));
        mvc.perform(post("/api/v1/moderation/events/{id}/approve", eventId).with(as(MODERATOR)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/events/{id}", eventId))
                .andExpect(jsonPath("$.media.length()").value(2))
                .andExpect(jsonPath("$.media[0].url").value("/api/v1/events/" + eventId + "/media/" + photoId))
                .andExpect(jsonPath("$.media[0].change").doesNotExist());
        mvc.perform(get("/api/v1/events/{id}/media/{mediaId}", eventId, photoId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Content-Disposition", startsWith("inline")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(PHOTO));
        mvc.perform(get("/api/v1/events/{id}/media/{mediaId}", eventId, docId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("rules.docx")));
    }

    @Test
    void newMediaOfPublishedCardIsModeratedWhilePublicVersionStays() throws Exception {
        long eventId = createDraft(eventJson("Турнир", "+375291234567", null));
        long oldPhoto = idOf(upload(eventId, "images", "old.jpg", "image/jpeg", PHOTO)
                .andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/v1/provider/events/{id}/submit", eventId).with(as(PROVIDER)));
        mvc.perform(post("/api/v1/moderation/events/{id}/approve", eventId).with(as(MODERATOR)));

        // Замена фото: новое добавлено, старое отмечено к удалению
        long newPhoto = idOf(upload(eventId, "images", "new.jpg", "image/jpeg", PHOTO)
                .andReturn().getResponse().getContentAsString());
        mvc.perform(delete("/api/v1/provider/events/{id}/media/{mediaId}", eventId, oldPhoto).with(as(PROVIDER)))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/provider/events/{id}/media/{mediaId}", eventId, oldPhoto).with(as(PROVIDER)))
                .andExpect(status().isConflict());

        // Посетитель до одобрения видит прежний состав материалов
        mvc.perform(get("/api/v1/events/{id}", eventId))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.media.length()").value(1))
                .andExpect(jsonPath("$.media[0].id").value(oldPhoto));
        mvc.perform(get("/api/v1/events/{id}/media/{mediaId}", eventId, newPhoto)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/provider/events/{id}", eventId).with(as(PROVIDER)))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.media[?(@.id == " + oldPhoto + ")].change").value(hasItem("REMOVED")))
                .andExpect(jsonPath("$.media[?(@.id == " + newPhoto + ")].change").value(hasItem("ADDED")));

        // Изменения материалов автор отправляет на модерацию; карточка остается опубликованной
        mvc.perform(post("/api/v1/provider/events/{id}/submit", eventId).with(as(PROVIDER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.revision.status").value("ON_MODERATION"));
        // Пока изменения на модерации, материалы не меняются
        upload(eventId, "images", "more.jpg", "image/jpeg", PHOTO).andExpect(status().isConflict());

        mvc.perform(get("/api/v1/moderation/events").with(as(MODERATOR)))
                .andExpect(jsonPath("$.content[0].eventId").value((int) eventId))
                .andExpect(jsonPath("$.content[0].revision").value(true));
        // Diff-режим: текст не менялся, файлы — добавлен и удален
        mvc.perform(get("/api/v1/moderation/events/{id}", eventId).with(as(MODERATOR)))
                .andExpect(jsonPath("$.changedFields.length()").value(0))
                .andExpect(jsonPath("$.media[?(@.id == " + oldPhoto + ")].change").value(hasItem("REMOVED")))
                .andExpect(jsonPath("$.media[?(@.id == " + newPhoto + ")].change").value(hasItem("ADDED")));

        mvc.perform(post("/api/v1/moderation/events/{id}/approve", eventId).with(as(MODERATOR)))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(get("/api/v1/events/{id}", eventId))
                .andExpect(jsonPath("$.media.length()").value(1))
                .andExpect(jsonPath("$.media[0].id").value(newPhoto));
        mvc.perform(get("/api/v1/events/{id}/media/{mediaId}", eventId, oldPhoto)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/provider/events/{id}", eventId).with(as(PROVIDER)))
                .andExpect(jsonPath("$.media[*].change", not(hasItem("ADDED"))))
                .andExpect(jsonPath("$.revision").isEmpty());
    }

    @Test
    void rejectedMediaChangesKeepPublicVersion() throws Exception {
        long eventId = publish(eventJson("Турнир", "+375291234567", null));
        long photo = idOf(upload(eventId, "images", "watermark.jpg", "image/jpeg", PHOTO)
                .andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/v1/provider/events/{id}/submit", eventId).with(as(PROVIDER)));

        mvc.perform(post("/api/v1/moderation/events/{id}/reject", eventId).with(as(MODERATOR))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"На фото водяной знак\"}"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mvc.perform(get("/api/v1/events/{id}", eventId)).andExpect(jsonPath("$.media.length()").value(0));
        mvc.perform(get("/api/v1/provider/events/{id}", eventId).with(as(PROVIDER)))
                .andExpect(jsonPath("$.revision.status").value("REJECTED"))
                .andExpect(jsonPath("$.revision.rejectionReason").value("На фото водяной знак"))
                .andExpect(jsonPath("$.media[0].change").value("ADDED"));

        // Автор удаляет неодобренное фото — оно удаляется сразу, отправлять на модерацию больше нечего
        mvc.perform(delete("/api/v1/provider/events/{id}/media/{mediaId}", eventId, photo).with(as(PROVIDER)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/provider/events/{id}", eventId).with(as(PROVIDER)))
                .andExpect(jsonPath("$.media.length()").value(0));
        mvc.perform(post("/api/v1/provider/events/{id}/submit", eventId).with(as(PROVIDER)))
                .andExpect(status().isConflict());
    }

    @Test
    void archivedCardMediaIsNotPublic() throws Exception {
        long eventId = createDraft(eventJson("Турнир", "+375291234567", null));
        long photo = idOf(upload(eventId, "images", "afisha.jpg", "image/jpeg", PHOTO)
                .andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/v1/provider/events/{id}/submit", eventId).with(as(PROVIDER)));
        mvc.perform(post("/api/v1/moderation/events/{id}/approve", eventId).with(as(MODERATOR)));
        mvc.perform(get("/api/v1/events/{id}/media/{mediaId}", eventId, photo)).andExpect(status().isOk());

        mvc.perform(post("/api/v1/provider/events/{id}/archive", eventId).with(as(PROVIDER)));

        mvc.perform(get("/api/v1/events/{id}/media/{mediaId}", eventId, photo)).andExpect(status().isNotFound());
        upload(eventId, "images", "new.jpg", "image/jpeg", PHOTO).andExpect(status().isConflict());
    }

    private ResultActions upload(long eventId, String kind, String name, String type, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/v1/provider/events/{id}/" + kind, eventId)
                .file(new MockMultipartFile("file", name, type, bytes))
                .with(as(PROVIDER)));
    }

    /** Сигнатура JPEG без читаемого изображения. */
    private static byte[] fakeJpeg(int size) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) 1);
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        return bytes;
    }

    private static byte[] zipWithout() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("readme.txt"));
            zip.write("not a document".getBytes());
            zip.closeEntry();
        }
        return out.toByteArray();
    }
}
