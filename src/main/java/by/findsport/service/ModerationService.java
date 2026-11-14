package by.findsport.service;

import by.findsport.domain.CardStatus;
import by.findsport.domain.Event;
import by.findsport.domain.EventMedia;
import by.findsport.domain.EventModeration;
import by.findsport.domain.ModerationDecision;
import by.findsport.domain.User;
import by.findsport.repository.EventModerationRepository;
import by.findsport.repository.EventRepository;
import by.findsport.service.command.EventCommand;
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
 * Панель модератора (ТЗ п. 4.5.4, 4.14.3): очередь, сравнение версий, одобрение и отклонение с причиной.
 * <p>
 * Модерируются новые карточки (статус «На модерации») и правки опубликованных карточек: такая карточка
 * остается опубликованной в прежней версии, а предлагаемая версия хранится только в снимке отправки.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ModerationService {

    private static final String OBJECT = "eventId";

    private final EventRepository events;
    private final EventModerationRepository moderations;
    private final UserAccountService accounts;
    private final CardSnapshotService snapshots;
    private final EventCardWriter writer;
    private final EventMediaService media;
    private final ModerationChecks checks;
    private final ModerationAudit audit;
    private final Clock clock;

    /** Нерассмотренные отправки, первыми — давно ожидающие. */
    @Transactional(readOnly = true)
    public Page<EventModeration> queue(int page, int size) {
        return moderations.findByDecisionIsNull(PageRequest.of(page, size, Sort.by("createdAt", "id")));
    }

    /**
     * Diff-режим: для правок опубликованной карточки сравнение идет с действующей (публичной) версией.
     * Материалы показываются с отличием от публичной версии, результаты автоматических проверок — справочно.
     */
    @Transactional(readOnly = true)
    public ModerationReview review(Long eventId) {
        Event event = requireEvent(eventId);
        EventModeration submission = requirePending(eventId);
        String approved = event.getStatus() == CardStatus.PUBLISHED
                ? snapshots.snapshot(EventCommand.of(event).normalized())
                : null;
        List<EventMedia> materials = media.listAll(eventId);
        return new ModerationReview(event, submission, approved,
                snapshots.changedFields(approved, submission.getSnapshot()), materials,
                checks.check(snapshots.read(submission.getSnapshot(), EventCommand.class), materials));
    }

    /**
     * «Одобрить (Опубликовать)» — отправленная версия и материалы становятся публичными.
     * Карточка публикуется только от имени подтвержденного организатора (п. 4.10.5): его наименование
     * выводится в карточке, а непроверенные данные не должны попадать в публичный интерфейс (п. 4.14.1).
     */
    public Event approve(String moderatorEmail, Long eventId) {
        User moderator = accounts.requireByEmail(moderatorEmail);
        Event event = requireEvent(eventId);
        EventModeration submission = requirePending(eventId);
        if (!event.hasConfirmedProvider()) {
            throw new ConflictException("Профиль организатора не подтвержден — сначала одобрите профиль поставщика");
        }
        Instant now = clock.instant();
        submission.decide(ModerationDecision.APPROVED, null, moderator, now);
        writer.apply(event, snapshots.read(submission.getSnapshot(), EventCommand.class));
        media.publishPending(eventId, moderator);
        event.setStatus(CardStatus.PUBLISHED);
        event.setRejectionReason(null);
        if (event.getPublishedAt() == null) {
            event.setPublishedAt(now);
        }
        audit.approved(OBJECT, eventId, moderator);
        return event;
    }

    /**
     * «Отклонить» с обязательной причиной. Новая карточка получает статус «Отклонена» (автору
     * показывается «Требует доработки»); у опубликованной отклоняются только правки, а публичная
     * версия не меняется. Причина видна автору в кабинете. Неодобренные материалы остаются у автора
     * неопубликованными: он может заменить их и отправить карточку повторно.
     */
    public Event reject(String moderatorEmail, Long eventId, String reason) {
        User moderator = accounts.requireByEmail(moderatorEmail);
        Event event = requireEvent(eventId);
        requirePending(eventId).decide(ModerationDecision.REJECTED, reason, moderator, clock.instant());
        if (event.getStatus() == CardStatus.ON_MODERATION) {
            event.setStatus(CardStatus.REJECTED);
            event.setRejectionReason(reason);
        }
        audit.rejected(OBJECT, eventId, moderator);
        return event;
    }

    /** Журнал отправок и решений модераторов по карточке (п. 4.13.4). */
    @Transactional(readOnly = true)
    public List<EventModeration> history(Long eventId) {
        requireEvent(eventId);
        return moderations.findByEventIdOrderByIdDesc(eventId);
    }

    private Event requireEvent(Long eventId) {
        return events.findWithDetailsById(eventId)
                .orElseThrow(() -> new NotFoundException("Мероприятие не найдено"));
    }

    private EventModeration requirePending(Long eventId) {
        return moderations.findFirstByEventIdAndDecisionIsNull(eventId)
                .orElseThrow(() -> new ConflictException("Карточка не ожидает решения модератора"));
    }
}
