package by.findsport.service;

import by.findsport.domain.Address;
import by.findsport.domain.BaseEntity;
import by.findsport.domain.Event;
import by.findsport.domain.EventOffer;
import by.findsport.domain.LocalizedText;
import by.findsport.domain.Sport;
import by.findsport.service.command.EventCommand;
import by.findsport.service.command.OfferCommand;
import by.findsport.service.exception.InvalidReferenceException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Применение версии карточки к сущности: при редактировании поставщиком и при одобрении правок модератором.
 */
@Component
@RequiredArgsConstructor
class EventCardWriter {

    private final ReferenceResolver references;

    void apply(Event event, EventCommand command) {
        event.setType(command.type());
        event.setTitle(command.title());
        event.setDescription(command.description());
        event.setConditions(command.conditions());
        event.setProgram(command.program());
        event.setSkillLevel(command.skillLevel());
        event.setStartsAt(command.startsAt());
        event.setEndsAt(command.endsAt());
        event.setRegion(references.region(command.regionId()));
        event.setAddress(command.address());
        event.setAgeMin(command.ageMin());
        event.setAgeMax(command.ageMax());
        event.setParticipantLimit(command.participantLimit());
        event.setRegistrationDeadline(command.registrationDeadline());
        event.setContactPhone(command.contactPhone());
        event.setContactEmail(command.contactEmail());

        Set<Sport> sports = references.sports(command.sportIds());
        event.getSports().retainAll(sports);
        event.getSports().addAll(sports);

        mergeOffers(event, command);
    }

    /** Проверка ссылок версии без изменения карточки — перед сохранением правок на модерацию. */
    void validate(Event event, EventCommand command) {
        references.region(command.regionId());
        references.sports(command.sportIds());
        existingOffers(event, command);
    }

    /** Изменены поля, признаваемые существенными (п. 4.14.1); медиафайлы учитываются при их загрузке. */
    static boolean hasEssentialChanges(Event event, EventCommand command) {
        return !EssentialFields.of(event).equals(EssentialFields.of(command));
    }

    /** Предложения сопоставляются по id, чтобы не терять ссылки из существующих заявок. */
    private void mergeOffers(Event event, EventCommand command) {
        Map<Long, EventOffer> existing = existingOffers(event, command);
        Set<EventOffer> result = new LinkedHashSet<>();
        for (OfferCommand offerCommand : command.offers()) {
            if (offerCommand.id() == null) {
                result.add(new EventOffer(event, offerCommand.title(), offerCommand.price()));
                continue;
            }
            EventOffer offer = existing.get(offerCommand.id());
            offer.setTitle(offerCommand.title());
            offer.setPrice(offerCommand.price());
            result.add(offer);
        }
        event.getOffers().retainAll(result);
        event.getOffers().addAll(result);
    }

    private static Map<Long, EventOffer> existingOffers(Event event, EventCommand command) {
        Map<Long, EventOffer> existing = event.getOffers().stream()
                .filter(offer -> offer.getId() != null)
                .collect(Collectors.toMap(BaseEntity::getId, Function.identity()));
        command.offers().stream()
                .map(OfferCommand::id)
                .filter(id -> id != null && !existing.containsKey(id))
                .findFirst()
                .ifPresent(id -> {
                    throw new InvalidReferenceException("offers", "Предложение не найдено: " + id);
                });
        return existing;
    }

    private record EssentialFields(LocalizedText title, LocalizedText description, Instant startsAt, Instant endsAt,
                                   Long regionId, Address address) {

        static EssentialFields of(Event event) {
            return new EssentialFields(event.getTitle(), event.getDescription(),
                    toInstant(event.getStartsAt()), toInstant(event.getEndsAt()),
                    event.getRegion().getId(), event.getAddress());
        }

        static EssentialFields of(EventCommand command) {
            return new EssentialFields(command.title(), command.description(),
                    toInstant(command.startsAt()), toInstant(command.endsAt()),
                    command.regionId(), command.address());
        }

        private static Instant toInstant(OffsetDateTime value) {
            return value == null ? null : value.toInstant();
        }
    }
}
