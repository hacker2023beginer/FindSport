package by.findsport.web.dto;

import by.findsport.domain.ProviderType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Публичная карточка организации или тренера (ТЗ п. 4.10.4, 5.2). Контакты передаются только
 * в объеме, разрешенном владельцем (п. 4.10.6); учетная запись владельца не раскрывается.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicProviderResponse(
        Long id,
        ProviderType type,
        String name,
        String description,
        List<DictionaryItemResponse> sports,
        String region,
        AddressDto address,
        String serviceArea,
        String workingHours,
        String contactPhone,
        String contactEmail) {
}
