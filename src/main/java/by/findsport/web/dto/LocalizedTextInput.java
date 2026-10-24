package by.findsport.web.dto;

import by.findsport.domain.LocalizedText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Развернутый текст (описание, условия) на двух языках; русская версия обязательна (ТЗ п. 4.1.6). */
public record LocalizedTextInput(
        @NotBlank @Size(max = 10000) String ru,
        @Size(max = 10000) String en) {

    public LocalizedText toDomain() {
        return new LocalizedText(ru.trim(), en == null || en.isBlank() ? null : en.trim());
    }

    public static LocalizedText toDomainOrNull(LocalizedTextInput input) {
        return input == null ? null : input.toDomain();
    }
}
