package by.findsport.web.dto;

import by.findsport.domain.MediaChange;
import by.findsport.domain.MediaType;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Медиаматериал карточки (ТЗ п. 4.1.7): файл с адресом скачивания или видеоссылка на Google Drive.
 *
 * @param change отличие от публичной версии — для автора и модератора (Diff-режим, п. 4.14.3);
 *               в публичной карточке не передается
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MediaResponse(
        Long id,
        MediaType type,
        String fileName,
        String contentType,
        Long sizeBytes,
        String url,
        MediaChange change) {
}
