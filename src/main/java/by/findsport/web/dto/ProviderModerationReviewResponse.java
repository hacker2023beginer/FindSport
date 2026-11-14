package by.findsport.web.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import java.time.Instant;
import java.util.List;

/**
 * Данные для подтверждения профиля поставщика в Diff-режиме (ТЗ п. 4.10.5, 4.14.3).
 * {@code approvedVersion} — {@code null}, если профиль еще не публиковался.
 */
public record ProviderModerationReviewResponse(
        Long providerId,
        Long submissionId,
        Instant submittedAt,
        String submittedBy,
        List<String> changedFields,
        @JsonRawValue String submittedVersion,
        @JsonRawValue String approvedVersion) {
}
