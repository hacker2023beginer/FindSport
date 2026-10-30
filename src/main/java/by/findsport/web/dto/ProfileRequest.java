package by.findsport.web.dto;

import by.findsport.domain.Language;
import by.findsport.web.validation.BelarusPhone;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Профиль пользователя (ТЗ п. 4.10.1): имя, телефон, регион, спортивные интересы, язык. */
public record ProfileRequest(
        @Size(max = 100) String displayName,
        @BelarusPhone String phone,
        Long regionId,
        @NotNull @Size(max = 20) Set<@NotNull Long> sportIds,
        @NotNull Language preferredLanguage) {
}
