package by.findsport.web.dto;

import by.findsport.domain.ProviderType;
import by.findsport.web.validation.BelarusPhone;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Профиль организации или тренера. Обязательные сведения по ТЗ п. 5.2; объем публичных контактов —
 * флаги {@code phonePublic}/{@code emailPublic} (п. 4.10.6).
 */
public record ProviderProfileRequest(
        @NotNull ProviderType type,
        @NotNull @Valid LocalizedTitleInput name,
        @NotNull @Valid LocalizedTextInput description,
        @NotEmpty @Size(max = 20) Set<@NotNull Long> sportIds,
        @NotNull Long regionId,
        @Valid AddressDto address,
        @Valid LocalizedTextInput serviceArea,
        @NotNull @Valid LocalizedTitleInput workingHours,
        @BelarusPhone String contactPhone,
        @Email @Size(max = 254) String contactEmail,
        boolean phonePublic,
        boolean emailPublic) {

    @AssertTrue(message = "Укажите адрес или территорию оказания услуг")
    public boolean isLocationPresent() {
        return address != null || serviceArea != null;
    }

    @AssertTrue(message = "Укажите телефон или e-mail для связи")
    public boolean isContactPresent() {
        return (contactPhone != null && !contactPhone.isBlank()) || (contactEmail != null && !contactEmail.isBlank());
    }
}
