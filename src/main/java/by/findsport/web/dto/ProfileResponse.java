package by.findsport.web.dto;

import by.findsport.domain.Language;
import by.findsport.domain.UserRole;
import java.time.Instant;
import java.util.List;

/** Профиль и настройки текущего пользователя (ТЗ п. 4.2.5, 4.10.1, 4.10.2). */
public record ProfileResponse(
        Long id,
        String email,
        UserRole role,
        String displayName,
        String phone,
        DictionaryItemResponse region,
        List<DictionaryItemResponse> sportInterests,
        Language preferredLanguage,
        boolean emailVerified,
        boolean personalizationConsent,
        boolean emailNotifications,
        Instant consentsAcceptedAt,
        Instant deletionRequestedAt,
        Instant createdAt) {
}
