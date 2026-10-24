package by.findsport.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Профиль поставщика услуг — организации или тренера (ТЗ п. 4.10.3–4.10.6).
 * Обязательные сведения по п. 5.2: наименование, тип, виды спорта, описание, регион,
 * адрес или территория оказания услуг, контакты, режим работы и статус публикации.
 */
@Getter
@Setter
@Entity
@Table(name = "provider")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Provider extends BaseEntity {

    /** Учетная запись представителя организации или тренера. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProviderType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardStatus status = CardStatus.DRAFT;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "ru", column = @Column(name = "name_ru", nullable = false, length = 200)),
            @AttributeOverride(name = "en", column = @Column(name = "name_en", length = 200))
    })
    private LocalizedText name;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "ru", column = @Column(name = "description_ru", nullable = false)),
            @AttributeOverride(name = "en", column = @Column(name = "description_en"))
    })
    private LocalizedText description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @Embedded
    private Address address;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "ru", column = @Column(name = "service_area_ru")),
            @AttributeOverride(name = "en", column = @Column(name = "service_area_en"))
    })
    private LocalizedText serviceArea;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "ru", column = @Column(name = "working_hours_ru", nullable = false, length = 500)),
            @AttributeOverride(name = "en", column = @Column(name = "working_hours_en", length = 500))
    })
    private LocalizedText workingHours;

    @Column(name = "contact_phone", length = 13)
    private String contactPhone;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    /** Показывать телефон публично (п. 4.10.6). */
    @Column(name = "phone_public", nullable = false)
    private boolean phonePublic;

    /** Показывать e-mail публично (п. 4.10.6). */
    @Column(name = "email_public", nullable = false)
    private boolean emailPublic;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "published_at")
    private Instant publishedAt;

    @ManyToMany
    @JoinTable(name = "provider_sport",
            joinColumns = @JoinColumn(name = "provider_id"),
            inverseJoinColumns = @JoinColumn(name = "sport_id"))
    private Set<Sport> sports = new HashSet<>();

    public Provider(User owner, ProviderType type) {
        this.owner = owner;
        this.type = type;
    }

    public boolean isManageableBy(User user) {
        return user.canManageAnyCard() || owner.getId().equals(user.getId());
    }
}
