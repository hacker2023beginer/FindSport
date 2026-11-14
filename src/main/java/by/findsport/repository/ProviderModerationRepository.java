package by.findsport.repository;

import by.findsport.domain.ProviderModeration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProviderModerationRepository extends JpaRepository<ProviderModeration, Long> {

    @EntityGraph(attributePaths = {"submittedBy"})
    Optional<ProviderModeration> findFirstByProviderIdAndDecisionIsNull(Long providerId);

    /** Очередь модератора: профили, ожидающие подтверждения, и правки опубликованных профилей. */
    @EntityGraph(attributePaths = {"provider", "provider.owner"})
    Page<ProviderModeration> findByDecisionIsNull(Pageable pageable);

    /** Журнал решений по профилю (п. 4.13.4), новые записи первыми. */
    @EntityGraph(attributePaths = {"submittedBy", "moderator"})
    List<ProviderModeration> findByProviderIdOrderByIdDesc(Long providerId);

    /** Последняя отправка по каждому профилю — состояние правок для кабинета автора. */
    @Query("""
            select m from ProviderModeration m
            where m.provider.id in :providerIds
              and m.id = (select max(m2.id) from ProviderModeration m2 where m2.provider = m.provider)
            """)
    List<ProviderModeration> findLatestByProviderIds(@Param("providerIds") Collection<Long> providerIds);
}
