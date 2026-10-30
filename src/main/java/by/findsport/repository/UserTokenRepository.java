package by.findsport.repository;

import by.findsport.domain.TokenPurpose;
import by.findsport.domain.UserToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserTokenRepository extends JpaRepository<UserToken, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<UserToken> findByTokenHashAndPurpose(String tokenHash, TokenPurpose purpose);

    /** Новая ссылка отменяет ранее выданные ссылки того же назначения. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update UserToken t set t.usedAt = :moment "
            + "where t.user.id = :userId and t.purpose = :purpose and t.usedAt is null")
    int invalidateActive(Long userId, TokenPurpose purpose, Instant moment);
}
