package by.findsport.web.dto;

import java.math.BigDecimal;

/** Цена всегда в BYN; {@code currency} передается явно для клиентов API. */
public record OfferResponse(Long id, String title, BigDecimal price, String currency) {
}
