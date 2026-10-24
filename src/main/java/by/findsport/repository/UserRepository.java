package by.findsport.repository;

import by.findsport.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** E-mail хранится в нижнем регистре — передавайте нормализованное значение. */
    Optional<User> findByEmail(String email);
}
