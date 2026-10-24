package by.findsport.web.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import java.time.Instant;
import java.util.List;

/**
 * Данные для Diff-режима (ТЗ п. 4.14.3): отправленная версия, последняя одобренная версия
 * ({@code null}, если карточка не публиковалась) и список измененных полей для подсветки.
 */
public record ModerationReviewResponse(
        Long eventId,
        Long submissionId,
        Instant submittedAt,
        String submittedBy,
        List<String> changedFields,
        @JsonRawValue String submittedVersion,
        @JsonRawValue String approvedVersion) {
}
