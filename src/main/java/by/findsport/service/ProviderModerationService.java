package by.findsport.service;

import by.findsport.domain.CardStatus;
import by.findsport.domain.ModerationDecision;
import by.findsport.domain.Provider;
import by.findsport.domain.ProviderModeration;
import by.findsport.domain.User;
import by.findsport.repository.ProviderModerationRepository;
import by.findsport.repository.ProviderRepository;
import by.findsport.service.command.ProviderCommand;
import by.findsport.service.exception.ConflictException;
import by.findsport.service.exception.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Подтверждение профилей организаций и тренеров модератором (ТЗ п. 4.10.5, 4.14.3):
 * очередь, сравнение версий, одобрение и отклонение с обязательной причиной, журнал решений.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProviderModerationService {

    private static final String OBJECT = "providerId";

    private final ProviderRepository providers;
    private final ProviderModerationRepository moderations;
    private final UserAccountService accounts;
    private final CardSnapshotService snapshots;
    private final ProviderCardWriter writer;
    private final ModerationAudit audit;
    private final Clock clock;

    /** Нерассмотренные отправки, первыми — давно ожидающие. */
    @Transactional(readOnly = true)
    public Page<ProviderModeration> queue(int page, int size) {
        return moderations.findByDecisionIsNull(PageRequest.of(page, size, Sort.by("createdAt", "id")));
    }

    /** Diff-режим: правки опубликованного профиля сравниваются с действующей (публичной) версией. */
    @Transactional(readOnly = true)
    public ProviderModerationReview review(Long providerId) {
        Provider provider = requireProvider(providerId);
        ProviderModeration submission = requirePending(providerId);
        String approved = provider.getStatus() == CardStatus.PUBLISHED
                ? snapshots.snapshot(ProviderCommand.of(provider).normalized())
                : null;
        return new ProviderModerationReview(provider, submission, approved,
                snapshots.changedFields(approved, submission.getSnapshot()));
    }

    /** Профиль подтвержден: отправленная версия становится публичной. */
    public Provider approve(String moderatorEmail, Long providerId) {
        User moderator = accounts.requireByEmail(moderatorEmail);
        Provider provider = requireProvider(providerId);
        ProviderModeration submission = requirePending(providerId);
        Instant now = clock.instant();
        submission.decide(ModerationDecision.APPROVED, null, moderator, now);
        writer.apply(provider, snapshots.read(submission.getSnapshot(), ProviderCommand.class));
        provider.setStatus(CardStatus.PUBLISHED);
        provider.setRejectionReason(null);
        if (provider.getPublishedAt() == null) {
            provider.setPublishedAt(now);
        }
        audit.approved(OBJECT, providerId, moderator);
        return provider;
    }

    /**
     * Отклонение с обязательной причиной: новый профиль получает статус «Отклонен» («Требует доработки»
     * для владельца); у опубликованного отклоняются только правки, публичная версия не меняется.
     */
    public Provider reject(String moderatorEmail, Long providerId, String reason) {
        User moderator = accounts.requireByEmail(moderatorEmail);
        Provider provider = requireProvider(providerId);
        requirePending(providerId).decide(ModerationDecision.REJECTED, reason, moderator, clock.instant());
        if (provider.getStatus() == CardStatus.ON_MODERATION) {
            provider.setStatus(CardStatus.REJECTED);
            provider.setRejectionReason(reason);
        }
        audit.rejected(OBJECT, providerId, moderator);
        return provider;
    }

    /** Журнал отправок и решений по профилю (п. 4.13.4). */
    @Transactional(readOnly = true)
    public List<ProviderModeration> history(Long providerId) {
        requireProvider(providerId);
        return moderations.findByProviderIdOrderByIdDesc(providerId);
    }

    private Provider requireProvider(Long providerId) {
        return providers.findWithDetailsById(providerId)
                .orElseThrow(() -> new NotFoundException("Профиль поставщика не найден"));
    }

    private ProviderModeration requirePending(Long providerId) {
        return moderations.findFirstByProviderIdAndDecisionIsNull(providerId)
                .orElseThrow(() -> new ConflictException("Профиль не ожидает решения модератора"));
    }
}
