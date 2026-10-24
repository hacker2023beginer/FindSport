package by.findsport.service;

import by.findsport.domain.Provider;
import by.findsport.domain.User;
import by.findsport.repository.ProviderRepository;
import by.findsport.service.command.ProviderCommand;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Профили организаций и тренеров в личном кабинете поставщика (ТЗ п. 4.6.7, 5.2).
 * Профиль создается черновиком; модерация профилей (п. 4.10.5) — следующий этап.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProviderProfileService {

    private final ProviderRepository providers;
    private final UserAccountService accounts;
    private final ReferenceResolver references;

    public Provider create(String ownerEmail, ProviderCommand command) {
        User owner = accounts.requireByEmail(ownerEmail);
        Provider provider = new Provider(owner, command.type());
        provider.setName(command.name());
        provider.setDescription(command.description());
        provider.setSports(references.sports(command.sportIds()));
        provider.setRegion(references.region(command.regionId()));
        provider.setAddress(command.address());
        provider.setServiceArea(command.serviceArea());
        provider.setWorkingHours(command.workingHours());
        provider.setContactPhone(command.contactPhone());
        provider.setContactEmail(command.contactEmail());
        provider.setPhonePublic(command.phonePublic());
        provider.setEmailPublic(command.emailPublic());
        return providers.save(provider);
    }

    @Transactional(readOnly = true)
    public List<Provider> listOwn(String ownerEmail) {
        return providers.findByOwnerIdOrderByIdDesc(accounts.requireByEmail(ownerEmail).getId());
    }
}
