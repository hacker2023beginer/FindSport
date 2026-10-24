package by.findsport.service;

import by.findsport.domain.CardStatus;
import by.findsport.domain.Event;
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

    private final EventRepository events;
    private final EventModerationRepository moderations;
    private final UserAccountService accounts;
    private final EventSnapshotService snapshots;
    private final EventCardWriter writer;
    private final Clock clock;

    /** Нерассмотренные отправки, первыми — давно ожидающие. */
    @Transactional(readOnly = true)
    public Page<EventModeration> queue(int page, int size) {
        return moderations.findByDecisionIsNull(PageRequest.of(page, size, Sort.by("createdAt", "id")));
    }

    /** Diff-режим: для правок опубликованной карточки сравнение идет с действующей (публичной) версией. */
    @Transactional(readOnly = true)
    public ModerationReview review(Long eventId) {
        Event event = requireEvent(eventId);
        EventModeration submission = requirePending(eventId);
        String approved = event.getStatus() == CardStatus.PUBLISHED
                ? snapshots.snapshot(EventCommand.of(event))
                : null;
        return new ModerationReview(event, submission, approved,
                snapshots.changedFields(approved, submission.getSnapshot()));
    }

    /** «Одобрить (Опубликовать)» — отправленная версия становится публичной. */
    public Event approve(String moderatorEmail, Long eventId) {
        User moderator = accounts.requireByEmail(moderatorEmail);
        Event event = requireEvent(eventId);
        EventModeration submission = requirePending(eventId);
        Instant now = clock.instant();
        submission.decide(ModerationDecision.APPROVED, null, moderator, now);
        writer.apply(event, snapshots.read(submission.getSnapshot()));
        event.setStatus(CardStatus.PUBLISHED);
        event.setRejectionReason(null);
        if (event.getPublishedAt() == null) {
            event.setPublishedAt(now);
        }
        return event;
    }

    /**
     * «Отклонить» с обязательной причиной. Новая карточка получает статус «Отклонена» (автору
     * показывается «Требует доработки»); у опубликованной отклоняются только правки, а публичная
     * версия не меняется. Причина видна автору в кабинете.
     */
    public Event reject(String moderatorEmail, Long eventId, String reason) {
        User moderator = accounts.requireByEmail(moderatorEmail);
        Event event = requireEvent(eventId);
        requirePending(eventId).decide(ModerationDecision.REJECTED, reason, moderator, clock.instant());
        if (event.getStatus() == CardStatus.ON_MODERATION) {
            event.setStatus(CardStatus.REJECTED);
            event.setRejectionReason(reason);
        }
        return event;
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
