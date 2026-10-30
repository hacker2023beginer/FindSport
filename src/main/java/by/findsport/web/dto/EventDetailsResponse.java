package by.findsport.web.dto;

import by.findsport.domain.EventType;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Детальная карточка мероприятия (ТЗ п. 4.9.3, 5.3): условия участия, программа, стоимость,
 * возрастные ограничения, контакты и статус регистрации для кнопки заявки.
 */
public record EventDetailsResponse(
        Long id,
        EventType type,
        String title,
        String description,
        String conditions,
        String program,
        String skillLevel,
        List<DictionaryItemResponse> sports,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        PublicAddressResponse address,
        Integer ageMin,
        Integer ageMax,
        List<OfferResponse> offers,
        OrganizerResponse organizer,
        String contactPhone,
        String contactEmail,
        Integer participantLimit,
        Integer freeSeats,
        OffsetDateTime registrationDeadline,
        boolean registrationOpen) {

    public record OrganizerResponse(Long id, String name) {
    }
}
