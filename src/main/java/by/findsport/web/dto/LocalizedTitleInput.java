package by.findsport.web.dto;

import by.findsport.domain.LocalizedText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Короткий текст (название) на двух языках; русская версия обязательна (ТЗ п. 4.1.6). */
public record LocalizedTitleInput(
        @NotBlank @Size(max = 200) String ru,
        @Size(max = 200) String en) {

    public LocalizedText toDomain() {
        return new LocalizedText(ru.trim(), en == null || en.isBlank() ? null : en.trim());
    }

    public static LocalizedText toDomainOrNull(LocalizedTitleInput input) {
        return input == null ? null : input.toDomain();
    }
}
