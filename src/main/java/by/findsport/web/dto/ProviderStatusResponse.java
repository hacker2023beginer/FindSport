package by.findsport.web.dto;

import by.findsport.domain.CardStatus;

public record ProviderStatusResponse(Long id, CardStatus status, String statusLabel, String rejectionReason) {
}
