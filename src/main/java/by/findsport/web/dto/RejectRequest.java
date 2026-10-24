package by.findsport.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Причина отклонения обязательна (ТЗ п. 4.14.3). */
public record RejectRequest(@NotBlank @Size(max = 2000) String reason) {
}
