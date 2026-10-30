package by.findsport.repository;

import by.findsport.domain.User;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    /** E-mail хранится в нижнем регистре — передавайте нормализованное значение. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Профиль со всеми отображаемыми связями (п. 4.10.1). */
    @EntityGraph(attributePaths = {"region", "sportInterests"})
    Optional<User> findWithProfileByEmail(String email);

    Page<User> findAllByOrderByIdAsc(Pageable pageable);

    /**
     * Атомарно увеличивает счетчик неуспешных входов (п. 4.2.3): параллельные попытки
     * подбора пароля не теряют инкременты и не конфликтуют по {@code @Version}.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.failedLoginAttempts = u.failedLoginAttempts + 1 where u.id = :id")
    void incrementFailedLogins(Long id);

    /** Ограничивает вход, если счетчик достиг порога; счетчик начинается заново после ограничения. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.lockedUntil = :until, u.failedLoginAttempts = 0 "
            + "where u.id = :id and u.failedLoginAttempts >= :threshold")
    int lockIfThresholdReached(Long id, int threshold, Instant until);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.failedLoginAttempts = 0 where u.id = :id and u.failedLoginAttempts > 0")
    void resetFailedLogins(Long id);
}
