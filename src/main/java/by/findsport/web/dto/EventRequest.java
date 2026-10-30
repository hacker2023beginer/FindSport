package by.findsport.web.dto;

import by.findsport.domain.EventType;
import by.findsport.web.validation.BelarusPhone;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * Карточка мероприятия от поставщика (ТЗ п. 4.2.6, 4.9.3, 4.9.6, 5.3). Обязательные поля модерации
 * по п. 4.14.2: название, дата/время, локация; возрастной ценз задается диапазоном ageMin–ageMax.
 */
public record EventRequest(
        @NotNull Long providerId,
        @NotNull EventType type,
        @NotNull @Valid LocalizedTitleInput title,
        @NotNull @Valid LocalizedTextInput description,
        @NotNull @Valid LocalizedTextInput conditions,
        @Valid LocalizedTextInput program,
        @Valid LocalizedTitleInput skillLevel,
        @NotEmpty @Size(max = 10) Set<@NotNull Long> sportIds,
        @NotNull OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        @NotNull Long regionId,
        @NotNull @Valid AddressDto address,
        @Min(0) @Max(120) Integer ageMin,
        @Min(0) @Max(120) Integer ageMax,
        @Positive Integer participantLimit,
        OffsetDateTime registrationDeadline,
        @BelarusPhone String contactPhone,
        @Email @Size(max = 254) String contactEmail,
        @NotEmpty @Size(max = 50) List<@NotNull @Valid OfferInput> offers) {

    @AssertTrue(message = "Дата окончания не может быть раньше даты начала")
    public boolean isPeriodValid() {
        return startsAt == null || endsAt == null || !endsAt.isBefore(startsAt);
    }

    @AssertTrue(message = "Максимальный возраст не может быть меньше минимального")
    public boolean isAgeRangeValid() {
        return ageMin == null || ageMax == null || ageMax >= ageMin;
    }

    @AssertTrue(message = "Укажите телефон или e-mail для связи")
    public boolean isContactPresent() {
        return (contactPhone != null && !contactPhone.isBlank()) || (contactEmail != null && !contactEmail.isBlank());
    }
}
