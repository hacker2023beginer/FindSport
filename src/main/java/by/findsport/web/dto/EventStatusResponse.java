package by.findsport.web.dto;

import by.findsport.domain.CardStatus;

public record EventStatusResponse(Long id, CardStatus status, String rejectionReason) {
}
