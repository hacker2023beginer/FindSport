package by.findsport.service.command;

import by.findsport.domain.Address;
import by.findsport.domain.BaseEntity;
import by.findsport.domain.Event;
import by.findsport.domain.EventType;
import by.findsport.domain.LocalizedText;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Данные карточки мероприятия, заполняемые поставщиком (ТЗ п. 4.2.6, 5.3).
 * Он же — формат JSON-снимка версии карточки в журнале модерации: снимок применяется к карточке
 * при одобрении правок (решение PM: до одобрения публично показывается прежняя версия).
 */
public record EventCommand(
        Long providerId,
        EventType type,
        LocalizedText title,
        LocalizedText description,
        LocalizedText conditions,
        LocalizedText program,
        LocalizedText skillLevel,
        Set<Long> sportIds,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        Long regionId,
        Address address,
        Integer ageMin,
        Integer ageMax,
        Integer participantLimit,
        OffsetDateTime registrationDeadline,
        String contactPhone,
        String contactEmail,
        List<OfferCommand> offers) {

    private static final Comparator<OfferCommand> OFFER_ORDER = Comparator
            .comparing(OfferCommand::price)
            .thenComparing(offer -> offer.title().getRu());

    /** Текущее состояние карточки в виде команды — для снимка одобряемой или опубликованной версии. */
    public static EventCommand of(Event event) {
        return new EventCommand(
                event.getProvider().getId(), event.getType(), event.getTitle(), event.getDescription(),
                event.getConditions(), event.getProgram(), event.getSkillLevel(),
                event.getSports().stream().map(BaseEntity::getId).collect(Collectors.toSet()),
                event.getStartsAt(), event.getEndsAt(), event.getRegion().getId(), event.getAddress(),
                event.getAgeMin(), event.getAgeMax(), event.getParticipantLimit(), event.getRegistrationDeadline(),
                event.getContactPhone(), event.getContactEmail(),
                event.getOffers().stream()
                        .map(offer -> new OfferCommand(offer.getId(), offer.getTitle(), offer.getPrice()))
                        .toList());
    }

    /**
     * Канонический вид для сравнения версий: порядок видов спорта и предложений и масштаб цен
     * не должны давать ложных отличий в Diff-режиме.
     */
    public EventCommand normalized() {
        List<OfferCommand> normalizedOffers = offers.stream()
                .map(offer -> new OfferCommand(offer.id(), offer.title(), offer.price().setScale(2, RoundingMode.HALF_UP)))
                .sorted(OFFER_ORDER)
                .toList();
        return new EventCommand(providerId, type, title, description, conditions, program, skillLevel,
                new TreeSet<>(sportIds), startsAt, endsAt, regionId, address, ageMin, ageMax, participantLimit,
                registrationDeadline, contactPhone, contactEmail, normalizedOffers);
    }
}
