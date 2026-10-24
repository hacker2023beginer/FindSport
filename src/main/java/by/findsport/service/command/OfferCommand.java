package by.findsport.service.command;

import by.findsport.domain.LocalizedText;
import java.math.BigDecimal;

/**
 * @param id идентификатор существующего предложения при редактировании; {@code null} — новое
 */
public record OfferCommand(Long id, LocalizedText title, BigDecimal price) {
}
