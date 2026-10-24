package by.findsport.web.dto;

import by.findsport.domain.CardStatus;
import java.time.Instant;
import java.time.OffsetDateTime;

public record ProviderEventSummaryResponse(
        Long id,
        Long providerId,
        String providerName,
        String title,
        CardStatus status,
        String statusLabel,
        String rejectionReason,
        RevisionResponse revision,
        OffsetDateTime startsAt,
        Instant updatedAt) {
}
