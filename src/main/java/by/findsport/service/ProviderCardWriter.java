package by.findsport.service;

import by.findsport.domain.Address;
import by.findsport.domain.LocalizedText;
import by.findsport.domain.Provider;
import by.findsport.domain.Sport;
import by.findsport.service.command.ProviderCommand;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Применение версии профиля поставщика к сущности: при редактировании владельцем
 * и при одобрении правок модератором.
 */
@Component
@RequiredArgsConstructor
class ProviderCardWriter {

    private final ReferenceResolver references;

    void apply(Provider provider, ProviderCommand command) {
        provider.setType(command.type());
        provider.setName(command.name());
        provider.setDescription(command.description());
        provider.setRegion(references.region(command.regionId()));
        provider.setAddress(command.address());
        provider.setServiceArea(command.serviceArea());
        provider.setWorkingHours(command.workingHours());
        provider.setContactPhone(command.contactPhone());
        provider.setContactEmail(command.contactEmail());
        provider.setPhonePublic(command.phonePublic());
        provider.setEmailPublic(command.emailPublic());

        Set<Sport> sports = references.sports(command.sportIds());
        provider.getSports().retainAll(sports);
        provider.getSports().addAll(sports);
    }

    /** Проверка ссылок версии без изменения профиля — перед сохранением правок на модерацию. */
    void validate(ProviderCommand command) {
        references.region(command.regionId());
        references.sports(command.sportIds());
    }

    /**
     * Существенные изменения по п. 4.14.1 применительно к профилю: наименование, основное описание
     * и место — регион, адрес или территория оказания услуг.
     */
    static boolean hasEssentialChanges(Provider provider, ProviderCommand command) {
        return !EssentialFields.of(provider).equals(EssentialFields.of(command));
    }

    private record EssentialFields(LocalizedText name, LocalizedText description, Long regionId, Address address,
                                   LocalizedText serviceArea) {

        static EssentialFields of(Provider provider) {
            return new EssentialFields(provider.getName(), provider.getDescription(),
                    provider.getRegion().getId(), provider.getAddress(),
                    provider.getServiceArea());
        }

        static EssentialFields of(ProviderCommand command) {
            return new EssentialFields(command.name(), command.description(), command.regionId(), command.address(),
                    command.serviceArea());
        }
    }
}
