package by.findsport.web.dto;

import by.findsport.domain.Address;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Адрес по ТЗ п. 7.6 (область передается отдельно как {@code regionId}). */
public record AddressDto(
        @Size(max = 100) String district,
        @NotBlank @Size(max = 100) String locality,
        @NotBlank @Size(max = 150) String street,
        @NotBlank @Size(max = 20) String building) {

    public Address toDomain() {
        return new Address(district == null || district.isBlank() ? null : district.trim(),
                locality.trim(), street.trim(), building.trim());
    }

    public static AddressDto of(Address address) {
        return address == null ? null
                : new AddressDto(address.getDistrict(), address.getLocality(), address.getStreet(), address.getBuilding());
    }
}
