package by.findsport.web.dto;

import by.findsport.domain.CardStatus;
import by.findsport.domain.ProviderType;
import java.time.Instant;
import java.util.List;

/**
 * Профиль в кабинете владельца: статус с подписью («Требует доработки» для отклоненного), причина отклонения
 * и неодобренные правки опубликованного профиля (ТЗ п. 4.10.5, 4.14.3).
 */
public record ProviderProfileResponse(
        Long id,
        ProviderType type,
        CardStatus status,
        String statusLabel,
        String rejectionReason,
        RevisionResponse revision,
        LocalizedTextDto name,
        LocalizedTextDto description,
        List<Long> sportIds,
        Long regionId,
        AddressDto address,
        LocalizedTextDto serviceArea,
        LocalizedTextDto workingHours,
        String contactPhone,
        String contactEmail,
        boolean phonePublic,
        boolean emailPublic,
        Instant createdAt) {
}
