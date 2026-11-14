package by.findsport.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Отправка профиля организации или тренера на подтверждение и решение модератора (ТЗ п. 4.10.5, 4.14).
 * Хранит JSON-снимок отправленной версии профиля; записи образуют журнал решений (п. 4.13.4).
 * Дата отправки — {@code createdAt}.
 */
@Getter
@Entity
@Table(name = "provider_moderation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProviderModeration extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by", nullable = false)
    private User submittedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String snapshot;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ModerationDecision decision;

    @Column
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moderator_id")
    private User moderator;

    @Column(name = "decided_at")
    private Instant decidedAt;

    public ProviderModeration(Provider provider, User submittedBy, String snapshot) {
        this.provider = provider;
        this.submittedBy = submittedBy;
        this.snapshot = snapshot;
    }

    public boolean isPending() {
        return decision == null;
    }

    public void decide(ModerationDecision decision, String reason, User moderator, Instant decidedAt) {
        if (!isPending()) {
            throw new IllegalStateException("Решение по отправке уже принято");
        }
        this.decision = decision;
        this.reason = reason;
        this.moderator = moderator;
        this.decidedAt = decidedAt;
    }
}
