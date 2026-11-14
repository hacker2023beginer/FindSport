package by.findsport.web.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import java.time.Instant;
import java.util.List;

/**
 * Данные для Diff-режима (ТЗ п. 4.14.3): отправленная версия, последняя одобренная версия
 * ({@code null}, если карточка не публиковалась), список измененных полей для подсветки,
 * материалы с отметкой «добавлен / удален / без изменений» и результаты автоматических проверок (п. 4.14.2).
 */
public record ModerationReviewResponse(
        Long eventId,
        Long submissionId,
        Instant submittedAt,
        String submittedBy,
        List<String> changedFields,
        @JsonRawValue String submittedVersion,
        @JsonRawValue String approvedVersion,
        List<MediaResponse> media,
        List<ModerationCheckResponse> checks) {
}
