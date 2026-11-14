package by.findsport.service;

import static org.assertj.core.api.Assertions.assertThat;

import by.findsport.config.FileUploadProperties;
import by.findsport.domain.Address;
import by.findsport.domain.Event;
import by.findsport.domain.EventMedia;
import by.findsport.domain.EventType;
import by.findsport.domain.LocalizedText;
import by.findsport.domain.MediaType;
import by.findsport.service.command.EventCommand;
import by.findsport.service.command.OfferCommand;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/** Модульные проверки автоматической проверки отправки на модерацию (ТЗ п. 4.14.2). */
class ModerationChecksTests {

    private static final ZoneId MINSK = ZoneId.of("Europe/Minsk");
    private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");

    private final ModerationChecks checks = new ModerationChecks(
            new FileUploadProperties(DataSize.ofKilobytes(100), DataSize.ofMegabytes(5), DataSize.ofMegabytes(20),
                    Path.of("unused")),
            Clock.fixed(NOW, MINSK));
    private final Event event = new Event(null, EventType.TOURNAMENT);

    @Test
    void completeSubmissionPassesAllChecks() {
        EventMedia photo = file(MediaType.IMAGE, "afisha.jpg", "image/jpeg", 2_000_000);
        EventMedia rules = file(MediaType.DOCUMENT, "rules.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 1_000_000);
        EventMedia video = new EventMedia(event, MediaType.VIDEO_LINK);
        video.setVideoUrl("https://drive.google.com/file/d/abc/view");

        Map<String, ModerationCheck> result = byCode(checks.check(version(10, OffsetDateTime.now(Clock.fixed(NOW, MINSK)).plusDays(3)),
                List.of(photo, rules, video)));

        assertThat(result.values()).allMatch(ModerationCheck::passed);
        assertThat(result.get("FILES").message()).isEqualTo("Файлов: 2 — формат и размер соответствуют требованиям");
    }

    @Test
    void reportsMissingFieldsPastDateAndFilesBeyondCurrentLimits() {
        // Администратор уменьшил предел после загрузки — модератор видит несоответствие
        EventMedia bigPhoto = file(MediaType.IMAGE, "big.jpg", "image/jpeg", 6_000_000);
        EventMedia removed = file(MediaType.IMAGE, "old.jpg", "image/jpeg", 50);
        removed.setPublished(true);
        removed.setRemovalRequested(true);

        Map<String, ModerationCheck> result = byCode(checks.check(
                version(null, OffsetDateTime.now(Clock.fixed(NOW, MINSK)).minusDays(1)), List.of(bigPhoto, removed)));

        assertThat(result.get("REQUIRED_FIELDS").passed()).isFalse();
        assertThat(result.get("REQUIRED_FIELDS").message()).isEqualTo("Не заполнено: возрастной ценз");
        assertThat(result.get("STARTS_IN_FUTURE").passed()).isFalse();
        // Удаляемый файл в проверку не входит
        assertThat(result.get("FILES").passed()).isFalse();
        assertThat(result.get("FILES").message()).endsWith("big.jpg");
        assertThat(result.get("VIDEO_LINKS").message()).isEqualTo("Видеоссылок нет");
    }

    private EventMedia file(MediaType type, String name, String contentType, long size) {
        EventMedia media = new EventMedia(event, type);
        media.setOriginalFilename(name);
        media.setContentType(contentType);
        media.setSizeBytes(size);
        media.setStorageKey("events/1/" + name);
        return media;
    }

    private static EventCommand version(Integer ageMin, OffsetDateTime startsAt) {
        return new EventCommand(1L, EventType.TOURNAMENT, new LocalizedText("Турнир", null),
                new LocalizedText("Описание", null), new LocalizedText("Условия", null), null, null, Set.of(1L),
                startsAt, null, 1L, new Address(null, "Минск", "ул. Спортивная", "1"), ageMin, null, null, null,
                "+375291234567", null, List.of(new OfferCommand(null, new LocalizedText("Участник", null), BigDecimal.TEN)));
    }

    private static Map<String, ModerationCheck> byCode(List<ModerationCheck> result) {
        return result.stream().collect(Collectors.toMap(ModerationCheck::code, check -> check));
    }
}
