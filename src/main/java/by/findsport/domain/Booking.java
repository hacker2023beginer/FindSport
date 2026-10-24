package by.findsport.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Заявка пользователя на мероприятие (ТЗ п. 4.2.9): дата подачи ({@code createdAt}),
 * выбранное предложение, контактные данные и статус обработки.
 */
@Getter
@Setter
@Entity
@Table(name = "booking")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Выбранное предложение; составной FK в БД гарантирует, что оно принадлежит {@link #event}. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id", nullable = false)
    private EventOffer offer;

    @Column(name = "contact_name", nullable = false, length = 100)
    private String contactName;

    @Column(name = "contact_phone", length = 13)
    private String contactPhone;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.SUBMITTED;

    public Booking(Event event, User user, EventOffer offer, String contactName) {
        this.event = event;
        this.user = user;
        this.offer = offer;
        this.contactName = contactName;
    }

    /** Отмена возможна, пока заявка не завершена отказом или предыдущей отменой. */
    public void cancelByUser() {
        if (status != BookingStatus.SUBMITTED && status != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Заявку в статусе " + status + " отменить нельзя");
        }
        status = BookingStatus.CANCELLED_BY_USER;
    }
}
