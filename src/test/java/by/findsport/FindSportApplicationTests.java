package by.findsport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import by.findsport.domain.Address;
import by.findsport.domain.Booking;
import by.findsport.domain.BookingStatus;
import by.findsport.domain.CardStatus;
import by.findsport.domain.Event;
import by.findsport.domain.EventOffer;
import by.findsport.domain.EventType;
import by.findsport.domain.Language;
import by.findsport.domain.LocalizedText;
import by.findsport.domain.Provider;
import by.findsport.domain.ProviderType;
import by.findsport.domain.Region;
import by.findsport.domain.Sport;
import by.findsport.domain.User;
import by.findsport.domain.UserRole;
import by.findsport.repository.BookingRepository;
import by.findsport.repository.EventMediaRepository;
import by.findsport.repository.EventModerationRepository;
import by.findsport.repository.EventOfferRepository;
import by.findsport.repository.EventRepository;
import by.findsport.repository.EventSpecifications;
import by.findsport.repository.ProviderRepository;
import by.findsport.repository.RegionRepository;
import by.findsport.repository.SportRepository;
import by.findsport.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.repository.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Стартовый тест: контекст поднимается на реальном PostgreSQL, все миграции Flyway применены,
 * Hibernate {@code ddl-auto: validate} подтвердил соответствие сущностей схеме, репозитории работают.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FindSportApplicationTests {

    private static final List<String> EXPECTED_TABLES = List.of(
            "region", "sport", "users", "user_sport_interest", "provider", "provider_sport",
            "event", "event_sport", "event_offer", "event_media", "user_favorite_event",
            "booking", "event_moderation");

    @Autowired
    private ApplicationContext context;
    @Autowired
    private Flyway flyway;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RegionRepository regions;
    @Autowired
    private SportRepository sports;
    @Autowired
    private UserRepository users;
    @Autowired
    private ProviderRepository providers;
    @Autowired
    private EventRepository events;
    @Autowired
    private BookingRepository bookings;

    @Test
    void contextLoads() {
        assertThat(context.getBean(FindSportApplication.class)).isNotNull();
    }

    @Test
    void allMigrationsAppliedSuccessfully() {
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied).extracting(info -> info.getVersion().getVersion()).containsExactly("1", "2", "3", "4", "5");
        assertThat(applied).allMatch(info -> info.getState() == MigrationState.SUCCESS);
        assertThat(flyway.info().pending()).isEmpty();

        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
        assertThat(tables).containsAll(EXPECTED_TABLES);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'pg_trgm'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void allRepositoriesInitialized() {
        List<Class<?>> repositoryTypes = List.of(RegionRepository.class, SportRepository.class, UserRepository.class,
                ProviderRepository.class, EventRepository.class, EventOfferRepository.class,
                EventMediaRepository.class, BookingRepository.class, EventModerationRepository.class);

        assertThat(context.getBeansOfType(Repository.class)).hasSize(repositoryTypes.size());
        repositoryTypes.forEach(type -> assertThat(context.getBean(type)).isNotNull());
    }

    @Test
    void regionsSeededByMigration() {
        assertThat(regions.findAll()).extracting(Region::getCode)
                .containsExactlyInAnyOrder("BREST", "VITEBSK", "GOMEL", "GRODNO", "MINSK_REGION", "MOGILEV", "MINSK");
    }

    @Test
    @Transactional
    void aggregatePersistsWithAuditFieldsAndLazyRelations() {
        Region minsk = regions.findAll().stream().filter(r -> r.getCode().equals("MINSK")).findFirst().orElseThrow();
        Sport football = sports.save(new Sport("FOOTBALL", new LocalizedText("Футбол", "Football")));
        User owner = users.save(new User("Owner@Example.by", "{bcrypt}hash", UserRole.PROVIDER));

        Provider provider = new Provider(owner, ProviderType.ORGANIZATION);
        provider.setName(new LocalizedText("ФК Звезда", null));
        provider.setDescription(new LocalizedText("Детская футбольная школа", null));
        provider.setRegion(minsk);
        provider.setAddress(new Address(null, "Минск", "ул. Спортивная", "1"));
        provider.setWorkingHours(new LocalizedText("Пн–Пт 9:00–18:00", null));
        provider.setContactEmail("club@example.by");
        provider.getSports().add(football);
        providers.save(provider);

        Event event = new Event(provider, EventType.TOURNAMENT);
        event.setTitle(new LocalizedText("Турнир по мини-футболу", "Mini-football tournament"));
        event.setDescription(new LocalizedText("Открытый турнир", null));
        event.setConditions(new LocalizedText("Команды до 8 человек", null));
        event.setStartsAt(OffsetDateTime.now().plusDays(7));
        event.setRegion(minsk);
        event.setAddress(new Address(null, "Минск", "ул. Спортивная", "1"));
        event.setParticipantLimit(1);
        event.setContactPhone("+375291234567");
        event.getSports().add(football);
        EventOffer offer = new EventOffer(event, new LocalizedText("Командный взнос", null), new BigDecimal("50.00"));
        event.getOffers().add(offer);
        event.setStatus(CardStatus.PUBLISHED);
        events.save(event);

        Booking booking = new Booking(event, owner, offer, "Иван Иванов");
        booking.setContactPhone("+375447654321");
        bookings.save(booking);

        entityManager.flush();
        entityManager.clear();

        Event loaded = events.findWithDetailsById(event.getId()).orElseThrow();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
        assertThat(loaded.getVersion()).isZero();
        assertThat(loaded.getTitle().get(Language.EN)).isEqualTo("Mini-football tournament");
        assertThat(loaded.getSports()).extracting(Sport::getCode).containsExactly("FOOTBALL");
        assertThat(loaded.getOffers()).extracting(EventOffer::getPrice).containsExactly(new BigDecimal("50.00"));
        assertThat(users.findByEmail("owner@example.by")).isPresent();
        assertThat(bookings.countByEventIdAndStatusIn(event.getId(), EventSpecifications.SEAT_TAKING_STATUSES))
                .isEqualTo(1);

        // Лимит 1 исчерпан — фильтр «есть свободные места» исключает мероприятие (п. 4.7.2, 4.9.7)
        assertThat(events.findAll(EventSpecifications.hasFreeSeats())).isEmpty();
        // Отмена пользователем освобождает место (решение PM)
        booking = bookings.findById(booking.getId()).orElseThrow();
        booking.cancelByUser();
        entityManager.flush();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED_BY_USER);
        assertThat(events.findAll(EventSpecifications.hasFreeSeats())).extracting(Event::getId)
                .containsExactly(event.getId());
        assertThatThrownBy(booking::cancelByUser).isInstanceOf(IllegalStateException.class);
    }
}
