package by.findsport.web;

import by.findsport.domain.MediaType;
import by.findsport.service.StoredMedia;
import java.nio.charset.StandardCharsets;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;

/**
 * Выдача файлов материалов. Тип содержимого — сохраненный сервером, а не присланный клиентом;
 * изображения открываются в браузере, документы скачиваются (ТЗ п. 4.13.5, 4.14.2).
 */
final class MediaFiles {

    private MediaFiles() {
    }

    static ResponseEntity<Resource> response(StoredMedia stored, CacheControl cache) {
        var media = stored.media();
        ContentDisposition disposition = (media.getType() == MediaType.IMAGE
                ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(media.getOriginalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, media.getContentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentLength(media.getSizeBytes())
                .cacheControl(cache)
                .body(stored.content());
    }
}
