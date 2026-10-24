package by.findsport.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Автоматическое заполнение дат создания и изменения сущностей (ТЗ п. 5.9). */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
