package by.findsport.tools;

import by.findsport.domain.Address;
import by.findsport.domain.CardStatus;
import by.findsport.domain.Event;
import by.findsport.domain.EventOffer;
import by.findsport.domain.EventType;
import by.findsport.domain.LocalizedText;
import by.findsport.domain.Provider;
import by.findsport.domain.ProviderType;
import by.findsport.domain.Region;
import by.findsport.domain.Sport;
import by.findsport.domain.User;
import by.findsport.domain.UserRole;
import by.findsport.repository.EventOfferRepository;
import by.findsport.repository.EventRepository;
import by.findsport.repository.ProviderRepository;
import by.findsport.repository.RegionRepository;
import by.findsport.repository.SportRepository;
import by.findsport.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Генератор синтетического набора данных для проверки скорости поиска и каталога
 * (ТЗ п. 4.12.5, п. 6.8 — нагрузочные испытания; план недели 5, задача разработчика 7).
 * <p>
 * Включается только по явному признаку {@code findsport.tools.seed.enabled=true} и объему
 * {@code findsport.tools.seed.events} — по умолчанию генератор выключен и на обычную работу
 * не влияет.
 */
@Configuration
@EnableConfigurationProperties(SeedProperties.class)
class SeedConfiguration {
}

@Component
@ConditionalOnProperty(prefix = "findsport.tools.seed", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
class SyntheticDataGenerator implements ApplicationRunner {

    private static final ZoneId ZONE = ZoneId.of("Europe/Minsk");

    private static final String[][] SPORT_NAMES = {
            {"Бег", "Running"},
            {"Плавание", "Swimming"},
            {"Баскетбол", "Basketball"},
            {"Велоспорт", "Cycling"},
            {"Теннис", "Tennis"},
            {"Йога", "Yoga"},
            {"Волейбол", "Volleyball"},
            {"Единоборства", "Combat sports"},
            {"Футбол", "Football"},
            {"Лыжный спорт", "Skiing"}};

    private static final String[] TITLE_PREFIX = {
            "Открытый турнир", "Городской забег", "Тренировка выходного дня", "Кубок области",
            "Спортивный праздник", "Открытая тренировка", "Летний марафон", "Ночной забег",
            "Юношеские соревнования", "Лыжная гонка"};

    private static final String[] TITLE_SUFFIX = {
            "в Минске", "в Гомеле", "в Бресте", "в Гродно", "в Витебске", "в Могилеве",
            "для любителей", "на открытом воздухе", "с разминкой", "с награждением"};

    /** Населенные пункты из справочника: метки карты ставятся по ним (ТЗ п. 4.6.2). */
    private static final List<String> LOCALITIES = List.of(
            "Минск", "Брест", "Витебск", "Гомель", "Гродно", "Могилев",
            "Барановичи", "Бобруйск", "Лида", "Пинск", "Речица", "Слоним");

    private final EventRepository events;
    private final EventOfferRepository offers;
    private final ProviderRepository providers;
    private final RegionRepository regions;
    private final SportRepository sports;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final SeedProperties properties;
    private final Clock clock;

    /** Создает спортивный словарь, поставщиков и опубликованные мероприятия. */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Sport> sportList = ensureSports();
        List<Region> regionList = regions.findAll();
        List<Provider> providerList = ensureProviders(regionList, sportList);
        Random random = new Random(2026);
        OffsetDateTime now = OffsetDateTime.now(clock);

        List<Event> batch = new ArrayList<>();
        for (int i = 0; i < properties.events(); i++) {
            Provider provider = providerList.get(random.nextInt(providerList.size()));
            Sport sport = sportList.get(random.nextInt(sportList.size()));
            Region region = regionList.get(random.nextInt(regionList.size()));
            String locality = LOCALITIES.get(random.nextInt(LOCALITIES.size()));
            Event event = new Event(provider, EventType.values()[random.nextInt(EventType.values().length)]);
            event.setTitle(new LocalizedText(
                    TITLE_PREFIX[random.nextInt(TITLE_PREFIX.length)] + " "
                            + TITLE_SUFFIX[random.nextInt(TITLE_SUFFIX.length)],
                    "Event " + (i + 1)));
            event.setDescription(new LocalizedText(
                    "Синтетическая карточка для проверки скорости поиска и постраничного вывода.",
                    null));
            event.setConditions(new LocalizedText("Регистрация на месте, форма свободная.", null));
            event.setStartsAt(now.plusDays(random.nextInt(120) + 1).withHour(9).withMinute(0).withSecond(0).withNano(0));
            event.setEndsAt(event.getStartsAt().plusHours(3));
            event.setRegion(region);
            event.setAddress(new Address(null, locality, "ул. Спортивная",
                    String.valueOf(1 + random.nextInt(50))));
            event.setAgeMin(6 + random.nextInt(30));
            event.setAgeMax(event.getAgeMin() + random.nextInt(20));
            event.setParticipantLimit(random.nextInt(60) + 20);
            event.setContactPhone("+375291234567");
            event.getSports().add(sport);
            event.setStatus(CardStatus.PUBLISHED);
            event.setPublishedAt(now.minusSeconds(random.nextInt(2_000_000)).toInstant());
            batch.add(event);
            if (batch.size() >= 500) {
                persist(batch);
            }
        }
        persist(batch);
    }

    private void persist(List<Event> batch) {
        if (batch.isEmpty()) {
            return;
        }
        List<Event> saved = events.saveAll(batch);
        for (Event event : saved) {
            offers.save(new EventOffer(event, new LocalizedText("Участие", null),
                    BigDecimal.valueOf(10 + (event.getId() % 90))));
        }
        batch.clear();
    }

    private List<Sport> ensureSports() {
        List<Sport> existing = sports.findAll();
        List<Sport> result = new ArrayList<>(existing);
        for (int i = 0; i < SPORT_NAMES.length; i++) {
            String code = "SPORT_" + i;
            boolean present = existing.stream().anyMatch(sport -> sport.getCode().equals(code));
            if (!present) {
                result.add(sports.save(new Sport(code,
                        new LocalizedText(SPORT_NAMES[i][0], SPORT_NAMES[i][1]))));
            }
        }
        return result;
    }

    private List<Provider> ensureProviders(List<Region> regionList, List<Sport> sportList) {
        List<Provider> result = new ArrayList<>();
        for (int i = 0; i < properties.providers(); i++) {
            String email = "seed-provider-" + i + "@example.by";
            User owner = users.findByEmail(email).orElseGet(() -> {
                User created = new User(email, passwordEncoder.encode(properties.password()), UserRole.PROVIDER);
                created.setEmailVerified(true);
                return users.save(created);
            });
            Provider provider = new Provider(owner, i % 3 == 0 ? ProviderType.TRAINER : ProviderType.ORGANIZATION);
            provider.setName(new LocalizedText("Поставщик услуг №" + (i + 1), "Service provider #" + (i + 1)));
            provider.setDescription(new LocalizedText(
                    "Синтетический профиль поставщика для нагрузочной проверки каталога.", null));
            provider.setRegion(regionList.get(i % regionList.size()));
            provider.setAddress(new Address(null, LOCALITIES.get(i % LOCALITIES.size()), "ул. Спортивная",
                    String.valueOf(i + 1)));
            provider.setWorkingHours(new LocalizedText("Ежедневно 9:00–21:00", null));
            provider.setContactEmail(email);
            provider.getSports().add(sportList.get(i % sportList.size()));
            provider.setStatus(CardStatus.PUBLISHED);
            provider.setPublishedAt(OffsetDateTime.now(clock).minusSeconds(i * 3600L).toInstant());
            result.add(providers.save(provider));
        }
        return result;
    }
}
