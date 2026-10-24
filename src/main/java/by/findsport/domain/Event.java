package by.findsport.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Карточка спортивного мероприятия (ТЗ п. 4.6.5, 4.9.2–4.9.3, 5.3).
 * Все связи ленивые; для выдачи списков и карточек используются entity graph-ы репозитория.
 */
@Getter
@Setter
@Entity
@Table(name = "event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event extends BaseEntity {

    /** Организатор. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardStatus status = CardStatus.DRAFT;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "ru", column = @Column(name = "title_ru", nullable = false, length = 200)),
            @AttributeOverride(name = "en", column = @Column(name = "title_en", length = 200))
    })
    private LocalizedText title;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "ru", column = @Column(name = "description_ru", nullable = false)),
            @AttributeOverride(name = "en", column = @Column(name = "description_en"))
    })
    private LocalizedText description;

    /** Условия участия (п. 4.9.3, 5.3). */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "ru", column = @Column(name = "conditions_ru", nullable = false)),
            @AttributeOverride(name = "en", column = @Column(name = "conditions_en"))
    })
    private LocalizedText conditions;

    /** Программа мероприятия (п. 4.9.3). */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "ru", column = @Column(name = "program_ru")),
            @AttributeOverride(name = "en", column = @Column(name = "program_en"))
    })
    private LocalizedText program;

    /** Уровень подготовки (п. 4.2.6). */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "ru", column = @Column(name = "skill_level_ru", length = 200)),
            @AttributeOverride(name = "en", column = @Column(name = "skill_level_en", length = 200))
    })
    private LocalizedText skillLevel;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "ends_at")
    private OffsetDateTime endsAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @Embedded
    private Address address;

    @Column(name = "age_min")
    private Integer ageMin;

    @Column(name = "age_max")
    private Integer ageMax;

    /** Лимит участников; {@code null} — без ограничения (п. 4.9.6). */
    @Column(name = "participant_limit")
    private Integer participantLimit;

    /** Срок окончания регистрации (п. 4.9.6). */
    @Column(name = "registration_deadline")
    private OffsetDateTime registrationDeadline;

    @Column(name = "contact_phone", length = 13)
    private String contactPhone;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    /** Причина последнего отклонения, отображается автору (п. 4.14.3). */
    @Column(name = "rejection_reason")
    private String rejectionReason;

    /** Момент первой публикации — ключ сортировки по новизне (п. 4.7.3). */
    @Column(name = "published_at")
    private Instant publishedAt;

    @ManyToMany
    @JoinTable(name = "event_sport",
            joinColumns = @JoinColumn(name = "event_id"),
            inverseJoinColumns = @JoinColumn(name = "sport_id"))
    private Set<Sport> sports = new LinkedHashSet<>();

    // Set, а не List: карточка загружается одним entity graph вместе со sports без MultipleBagFetchException
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("price ASC")
    private Set<EventOffer> offers = new LinkedHashSet<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<EventMedia> media = new ArrayList<>();

    public Event(Provider provider, EventType type) {
        this.provider = provider;
        this.type = type;
    }

    /** Мероприятие завершено и исключается из списка предстоящих (п. 4.9.5). */
    public boolean isFinishedAt(OffsetDateTime moment) {
        OffsetDateTime end = endsAt != null ? endsAt : startsAt;
        return end.isBefore(moment);
    }

    /**
     * Прием заявок открыт: мероприятие опубликовано и не завершено, срок регистрации не истек
     * и лимит участников не достигнут (п. 4.9.6–4.9.7).
     */
    public boolean isRegistrationOpenAt(OffsetDateTime moment, long takenSeats) {
        return status == CardStatus.PUBLISHED
                && !isFinishedAt(moment)
                && (registrationDeadline == null || moment.isBefore(registrationDeadline))
                && (participantLimit == null || takenSeats < participantLimit);
    }

    /** Автор может редактировать черновик, отклоненную или опубликованную карточку. */
    public boolean isEditable() {
        return status == CardStatus.DRAFT || status == CardStatus.REJECTED || status == CardStatus.PUBLISHED;
    }
}
