package by.findsport.service;

import by.findsport.domain.BaseEntity;
import by.findsport.domain.CardStatus;
import by.findsport.domain.ModerationDecision;
import by.findsport.domain.Provider;
import by.findsport.domain.ProviderModeration;
import by.findsport.domain.User;
import by.findsport.repository.ProviderModerationRepository;
import by.findsport.repository.ProviderRepository;
import by.findsport.service.command.ProviderCommand;
import by.findsport.service.exception.ConflictException;
import by.findsport.service.exception.ForbiddenException;
import by.findsport.service.exception.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Профили организаций и тренеров в личном кабинете поставщика (ТЗ п. 4.6.7, 5.2) и их отправка
 * на подтверждение модератору (п. 4.10.5). Жизненный цикл тот же, что у карточки мероприятия:
 * черновик → на модерации → опубликован / отклонен → архивирован. Существенные правки опубликованного
 * профиля до одобрения хранятся только в снимке отправки, публично виден прежний профиль.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProviderProfileService {

    private static final String OBJECT = "providerId";

    private final ProviderRepository providers;
    private final ProviderModerationRepository moderations;
    private final UserAccountService accounts;
    private final ProviderCardWriter writer;
    private final CardSnapshotService snapshots;
    private final ModerationAudit audit;

    /** Новый профиль сохраняется черновиком. */
    public ProviderProfileView create(String ownerEmail, ProviderCommand command) {
        User owner = accounts.requireByEmail(ownerEmail);
        Provider provider = new Provider(owner, command.type());
        writer.apply(provider, command);
        return new ProviderProfileView(providers.save(provider), null);
    }

    /**
     * Черновик и отклоненный профиль изменяются сразу. Существенные изменения опубликованного профиля
     * (наименование, описание, место — п. 4.14.1) направляются на модерацию, несущественные
     * (контакты, режим работы, виды спорта) применяются сразу.
     */
    public ProviderProfileView update(String userEmail, Long providerId, ProviderCommand command) {
        User user = accounts.requireByEmail(userEmail);
        Provider provider = loadManageable(providerId, user);
        if (!provider.isEditable()) {
            throw new ConflictException("Профиль в статусе " + provider.getStatus() + " редактировать нельзя");
        }
        if (moderations.findFirstByProviderIdAndDecisionIsNull(providerId).isPresent()) {
            throw new ConflictException("Предыдущие правки профиля еще на модерации");
        }
        if (provider.getStatus() == CardStatus.PUBLISHED && ProviderCardWriter.hasEssentialChanges(provider, command)) {
            writer.validate(command);
            moderations.save(new ProviderModeration(provider, user, snapshots.snapshot(command.normalized())));
            audit.submitted(OBJECT, providerId, user);
        } else {
            writer.apply(provider, command);
        }
        return view(provider);
    }

    /** Запрос на подтверждение профиля (п. 4.10.5): черновик или профиль, требующий доработки. */
    public ProviderProfileView submitForModeration(String userEmail, Long providerId) {
        User user = accounts.requireByEmail(userEmail);
        Provider provider = loadManageable(providerId, user);
        if (provider.getStatus() != CardStatus.DRAFT && provider.getStatus() != CardStatus.REJECTED) {
            throw new ConflictException("На подтверждение направляется только черновик или профиль, требующий доработки");
        }
        provider.setStatus(CardStatus.ON_MODERATION);
        moderations.save(new ProviderModeration(provider, user,
                snapshots.snapshot(ProviderCommand.of(provider).normalized())));
        audit.submitted(OBJECT, providerId, user);
        return new ProviderProfileView(provider, null);
    }

    /** Архивирование опубликованного профиля (п. 4.1.8, 4.5.5): профиль снимается с публикации. */
    public ProviderProfileView archive(String userEmail, Long providerId) {
        User user = accounts.requireByEmail(userEmail);
        Provider provider = loadManageable(providerId, user);
        if (provider.getStatus() != CardStatus.PUBLISHED) {
            throw new ConflictException("Архивировать можно только опубликованный профиль");
        }
        if (moderations.findFirstByProviderIdAndDecisionIsNull(providerId).isPresent()) {
            throw new ConflictException("Правки профиля на модерации — дождитесь решения модератора");
        }
        provider.setStatus(CardStatus.ARCHIVED);
        audit.archived(OBJECT, providerId, user);
        return new ProviderProfileView(provider, null);
    }

    @Transactional(readOnly = true)
    public ProviderProfileView get(String userEmail, Long providerId) {
        return view(loadManageable(providerId, accounts.requireByEmail(userEmail)));
    }

    @Transactional(readOnly = true)
    public List<ProviderProfileView> listOwn(String ownerEmail) {
        List<Provider> own = providers.findByOwnerIdOrderByIdDesc(accounts.requireByEmail(ownerEmail).getId());
        if (own.isEmpty()) {
            return List.of();
        }
        Map<Long, ProviderModeration> latest = moderations
                .findLatestByProviderIds(own.stream().map(BaseEntity::getId).toList()).stream()
                .collect(Collectors.toMap(m -> m.getProvider().getId(), Function.identity()));
        return own.stream()
                .map(provider -> new ProviderProfileView(provider, revisionOf(provider, latest.get(provider.getId()))))
                .toList();
    }

    /** Публичный профиль — только подтвержденный модератором (п. 4.10.5). */
    @Transactional(readOnly = true)
    public Provider getPublished(Long providerId) {
        return providers.findWithDetailsById(providerId)
                .filter(provider -> provider.getStatus() == CardStatus.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Организация или тренер не найдены"));
    }

    private ProviderProfileView view(Provider provider) {
        ProviderModeration latest = moderations.findLatestByProviderIds(List.of(provider.getId())).stream()
                .findFirst().orElse(null);
        return new ProviderProfileView(provider, revisionOf(provider, latest));
    }

    /** Неодобренные правки бывают только у опубликованного профиля. */
    private static ProviderModeration revisionOf(Provider provider, ProviderModeration latest) {
        boolean unapproved = latest != null && latest.getDecision() != ModerationDecision.APPROVED;
        return provider.getStatus() == CardStatus.PUBLISHED && unapproved ? latest : null;
    }

    private Provider loadManageable(Long providerId, User user) {
        Provider provider = providers.findWithDetailsById(providerId)
                .orElseThrow(() -> new NotFoundException("Профиль поставщика не найден"));
        if (!provider.isManageableBy(user)) {
            throw new ForbiddenException("Нет прав на управление этим профилем");
        }
        return provider;
    }
}
