package by.findsport.service.command;

import by.findsport.domain.Address;
import by.findsport.domain.BaseEntity;
import by.findsport.domain.LocalizedText;
import by.findsport.domain.Provider;
import by.findsport.domain.ProviderType;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Сведения профиля организации или тренера (ТЗ п. 5.2, 4.10.6).
 * Он же — формат JSON-снимка версии профиля в журнале подтверждения (п. 4.10.5).
 */
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

    /** Текущее состояние профиля в виде команды — для снимка отправляемой или опубликованной версии. */
    public static ProviderCommand of(Provider provider) {
        return new ProviderCommand(provider.getType(), provider.getName(), provider.getDescription(),
                provider.getSports().stream().map(BaseEntity::getId).collect(Collectors.toSet()),
                provider.getRegion().getId(), provider.getAddress(), provider.getServiceArea(),
                provider.getWorkingHours(), provider.getContactPhone(), provider.getContactEmail(),
                provider.isPhonePublic(), provider.isEmailPublic());
    }

    /** Канонический вид для сравнения версий: порядок видов спорта не дает ложных отличий. */
    public ProviderCommand normalized() {
        return new ProviderCommand(type, name, description, new TreeSet<>(sportIds), regionId, address, serviceArea,
                workingHours, contactPhone, contactEmail, phonePublic, emailPublic);
    }
}
