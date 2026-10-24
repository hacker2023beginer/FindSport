package by.findsport.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Видеоматериал — только ссылка на Google Drive (ТЗ п. 4.1.7). */
public record VideoLinkRequest(
        @NotBlank
        @Size(max = 2048)
        @Pattern(regexp = "^https://drive\\.google\\.com/\\S+$", message = "Допускается только ссылка на Google Drive")
        String url) {
}
