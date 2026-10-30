package by.findsport.domain;

import jakarta.persistence.Column;
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
 * Учетная запись авторизованного пользователя (ТЗ п. 4.1.12, 4.2.2–4.2.5, 4.10.1).
 */
@Getter
@Setter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    /** Логин; хранится в нижнем регистре. */
    @Column(nullable = false, unique = true, length = 254)
    private String email;

    /** Хэш BCrypt с индивидуальной солью (п. 4.13.2). */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(length = 13)
    private String phone;

    /** Выбранный регион (п. 4.10.1). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id")
    private Region region;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_language", nullable = false, length = 2)
    private Language preferredLanguage = Language.RU;

    /** Адрес подтвержден по ссылке из письма (п. 4.2.2). */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    /** Заблокирован администратором (п. 4.5.1). */
    @Column(nullable = false)
    private boolean blocked;

    /** Счетчик последовательных неуспешных входов (п. 4.2.3). */
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    /** Временное ограничение авторизации после 5 неуспешных попыток (п. 4.2.3). */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    /** Согласие на персонализацию рекомендаций (п. 4.8.1). */
    @Column(name = "personalization_consent", nullable = false)
    private boolean personalizationConsent;

    /** Получать уведомления по электронной почте (п. 4.2.5, 4.2.11). */
    @Column(name = "email_notifications", nullable = false)
    private boolean emailNotifications = true;

    /** Принятие пользовательского соглашения и политики обработки ПД при регистрации (п. 4.6.8, 4.13.10). */
    @Column(name = "consents_accepted_at")
    private Instant consentsAcceptedAt;

    /** Момент, когда пользователь инициировал удаление учетной записи (п. 4.10.2). */
    @Column(name = "deletion_requested_at")
    private Instant deletionRequestedAt;

    @ManyToMany
    @JoinTable(name = "user_sport_interest",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "sport_id"))
    private Set<Sport> sportInterests = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "user_favorite_event",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "event_id"))
    private Set<Event> favoriteEvents = new HashSet<>();

    public User(String email, String passwordHash, UserRole role) {
        this.email = email.toLowerCase();
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public boolean isLockedAt(Instant moment) {
        return lockedUntil != null && lockedUntil.isAfter(moment);
    }

    /** Снимает временное ограничение входа и обнуляет счетчик неуспешных попыток (п. 4.2.3). */
    public void clearLoginFailures() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    /** Модератор и администратор могут исправлять чужие карточки (Приложение 2, сноска *). */
    public boolean canManageAnyCard() {
        return role == UserRole.MODERATOR || role == UserRole.ADMIN;
    }
}
