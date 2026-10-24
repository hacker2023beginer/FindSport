package by.findsport.web.dto;

import by.findsport.domain.CardStatus;
import by.findsport.domain.ProviderType;
import java.time.Instant;
import java.util.List;

public record ProviderProfileResponse(
        Long id,
        ProviderType type,
        CardStatus status,
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
