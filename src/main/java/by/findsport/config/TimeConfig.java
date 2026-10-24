package by.findsport.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Даты и время обрабатываются в часовом поясе Республики Беларусь (ТЗ п. 4.2.17). */
@Configuration
public class TimeConfig {

    public static final ZoneId BELARUS_ZONE = ZoneId.of("Europe/Minsk");

    @Bean
    public Clock clock() {
        return Clock.system(BELARUS_ZONE);
    }
}
