package by.findsport.service.command;

import by.findsport.domain.Address;
import by.findsport.domain.LocalizedText;
import by.findsport.domain.ProviderType;
import java.util.Set;

/** Сведения профиля организации или тренера (ТЗ п. 5.2, 4.10.6). */
public record ProviderCommand(
        ProviderType type,
        LocalizedText name,
        LocalizedText description,
        Set<Long> sportIds,
        Long regionId,
        Address address,
        LocalizedText serviceArea,
        LocalizedText workingHours,
        String contactPhone,
        String contactEmail,
        boolean phonePublic,
        boolean emailPublic) {
}
