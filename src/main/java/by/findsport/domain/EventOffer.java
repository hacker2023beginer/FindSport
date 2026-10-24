package by.findsport.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Предложение (вариант участия) мероприятия с ценой; выбирается при подаче заявки (ТЗ п. 4.2.9, 4.6.5).
 * Все цены — строго в белорусских рублях (решение PM), поэтому валюта не хранится в БД.
 */
@Getter
@Setter
@Entity
@Table(name = "event_offer")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventOffer extends BaseEntity {

    /** Единственная валюта Платформы — белорусский рубль (ISO 4217). */
    public static final String CURRENCY = "BYN";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "ru", column = @Column(name = "title_ru", nullable = false, length = 200)),
            @AttributeOverride(name = "en", column = @Column(name = "title_en", length = 200))
    })
    private LocalizedText title;

    /** Цена в {@link #CURRENCY}. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    public EventOffer(Event event, LocalizedText title, BigDecimal price) {
        this.event = event;
        this.title = title;
        this.price = price;
    }
}
