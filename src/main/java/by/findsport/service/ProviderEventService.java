package by.findsport.service;

import by.findsport.domain.BaseEntity;
import by.findsport.domain.CardStatus;
import by.findsport.domain.Event;
import by.findsport.domain.EventModeration;
import by.findsport.domain.ModerationDecision;
import by.findsport.domain.Provider;
import by.findsport.domain.User;
import by.findsport.repository.EventModerationRepository;
import by.findsport.repository.EventRepository;
import by.findsport.repository.ProviderRepository;
import by.findsport.service.command.EventCommand;
import by.findsport.service.exception.ConflictException;
import by.findsport.service.exception.ForbiddenException;
import by.findsport.service.exception.InvalidReferenceException;
import by.findsport.service.exception.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Управление своими карточками мероприятий в кабинете поставщика (ТЗ п. 4.2.6–4.2.7, Приложение 2)
 * и отправка их на модерацию (п. 4.14.1).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProviderEventService {

    private final EventRepository events;
    private final ProviderRepository providers;
    private final EventModerationRepository moderations;
    private final UserAccountService accounts;
    private final EventCardWriter writer;
    private final EventSnapshotService snapshots;

    /** Новая карточка сохраняется черновиком (Приложение 3, сценарий «Создание карточки»). */
    public ProviderEventView create(String userEmail, EventCommand command) {
        User user = accounts.requireByEmail(userEmail);
        Provider provider = providers.findById(command.providerId())
                .orElseThrow(() -> new InvalidReferenceException("providerId", "Профиль поставщика не найден"));
        if (!provider.isManageableBy(user)) {
            throw new ForbiddenException("Нет прав на создание карточек от имени этого поставщика");
        }
        Event event = new Event(provider, command.type());
        writer.apply(event, command);
        return new ProviderEventView(events.save(event), null);
    }

    /**
     * Черновик и отклоненная карточка изменяются сразу. Существенные изменения опубликованной карточки
     * (название, дата, время, место, описание — п. 4.14.1) не применяются, а направляются на модерацию
     * целиком: до одобрения публично показывается прежняя версия (решение PM). Несущественные
     * изменения опубликованной карточки применяются сразу.
     */
    public ProviderEventView update(String userEmail, Long eventId, EventCommand command) {
        User user = accounts.requireByEmail(userEmail);
        Event event = loadManageable(eventId, user);
        if (!event.isEditable()) {
            throw new ConflictException("Карточку в статусе " + event.getStatus() + " редактировать нельзя");
        }
        if (!event.getProvider().getId().equals(command.providerId())) {
            throw new ConflictException("Организатора карточки изменить нельзя");
        }
        if (moderations.findFirstByEventIdAndDecisionIsNull(eventId).isPresent()) {
            throw new ConflictException("Предыдущие правки карточки еще на модерации");
        }
        if (event.getStatus() == CardStatus.PUBLISHED && EventCardWriter.hasEssentialChanges(event, command)) {
            writer.validate(event, command);
            moderations.save(new EventModeration(event, user, snapshots.snapshot(command)));
        } else {
            writer.apply(event, command);
        }
        return view(event);
    }

    public ProviderEventView submitForModeration(String userEmail, Long eventId) {
        User user = accounts.requireByEmail(userEmail);
        Event event = loadManageable(eventId, user);
        if (event.getStatus() != CardStatus.DRAFT && event.getStatus() != CardStatus.REJECTED) {
            throw new ConflictException("На модерацию направляется только черновик или карточка, требующая доработки");
        }
        event.setStatus(CardStatus.ON_MODERATION);
        moderations.save(new EventModeration(event, user, snapshots.snapshot(EventCommand.of(event))));
        return new ProviderEventView(event, null);
    }

    @Transactional(readOnly = true)
    public ProviderEventView get(String userEmail, Long eventId) {
        return view(loadManageable(eventId, accounts.requireByEmail(userEmail)));
    }

    @Transactional(readOnly = true)
    public Page<ProviderEventView> listOwn(String userEmail, int page, int size) {
        User user = accounts.requireByEmail(userEmail);
        Page<Event> found = events.findByProviderOwnerId(user.getId(),
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("updatedAt"))));
        if (found.isEmpty()) {
            return found.map(event -> new ProviderEventView(event, null));
        }
        Map<Long, EventModeration> latest = moderations
                .findLatestByEventIds(found.map(BaseEntity::getId).getContent()).stream()
                .collect(Collectors.toMap(m -> m.getEvent().getId(), Function.identity()));
        return found.map(event -> new ProviderEventView(event, revisionOf(event, latest.get(event.getId()))));
    }

    private ProviderEventView view(Event event) {
        EventModeration latest = moderations.findLatestByEventIds(List.of(event.getId())).stream()
                .findFirst().orElse(null);
        return new ProviderEventView(event, revisionOf(event, latest));
    }

    /** Неодобренные правки бывают только у опубликованной карточки. */
    private static EventModeration revisionOf(Event event, EventModeration latest) {
        boolean unapproved = latest != null && latest.getDecision() != ModerationDecision.APPROVED;
        return event.getStatus() == CardStatus.PUBLISHED && unapproved ? latest : null;
    }

    private Event loadManageable(Long eventId, User user) {
        Event event = events.findWithDetailsById(eventId)
                .orElseThrow(() -> new NotFoundException("Мероприятие не найдено"));
        if (!event.getProvider().isManageableBy(user)) {
            throw new ForbiddenException("Нет прав на управление этой карточкой");
        }
        return event;
    }
}
