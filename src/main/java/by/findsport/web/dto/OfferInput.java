package by.findsport.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Предложение (вариант участия) с ценой в белорусских рублях (BYN) — другая валюта не допускается.
 *
 * @param id указывается при редактировании существующего предложения
 */
public record OfferInput(
        Long id,
        @NotNull @Valid LocalizedTitleInput title,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price) {
}
