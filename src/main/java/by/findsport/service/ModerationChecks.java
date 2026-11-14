package by.findsport.service;

import by.findsport.config.FileUploadProperties;
import by.findsport.domain.EventMedia;
import by.findsport.domain.MediaChange;
import by.findsport.domain.MediaType;
import by.findsport.service.command.EventCommand;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Автоматические проверки отправки карточки по объектному составу модерации (ТЗ п. 4.14.2):
 * обязательные поля текстового блока, дата проведения, технические параметры файлов и видеоссылок.
 * Файлы сверяются с действующими пределами конфигурации — администратор мог изменить их после загрузки.
 */
@Component
@RequiredArgsConstructor
class ModerationChecks {

    static final Pattern GOOGLE_DRIVE_LINK = Pattern.compile("^https://drive\\.google\\.com/\\S+$");

    private final FileUploadProperties limits;
    private final Clock clock;

    List<ModerationCheck> check(EventCommand version, List<EventMedia> media) {
        List<ModerationCheck> checks = new ArrayList<>();
        checks.add(requiredFields(version));
        checks.add(startsInFuture(version));
        List<EventMedia> resulting = media.stream().filter(m -> m.pendingChange() != MediaChange.REMOVED).toList();
        checks.add(files(resulting));
        checks.add(videoLinks(resulting));
        return checks;
    }

    /** Название, дата/время проведения, локация, возрастной ценз (п. 4.14.2, «Текстовый блок»). */
    private static ModerationCheck requiredFields(EventCommand version) {
        List<String> missing = new ArrayList<>();
        if (version.title() == null || isBlank(version.title().getRu())) {
            missing.add("название");
        }
        if (version.startsAt() == null) {
            missing.add("дата и время");
        }
        if (version.address() == null || isBlank(version.address().getLocality())) {
            missing.add("локация");
        }
        if (version.ageMin() == null && version.ageMax() == null) {
            missing.add("возрастной ценз");
        }
        return missing.isEmpty()
                ? new ModerationCheck("REQUIRED_FIELDS", true, "Обязательные поля заполнены")
                : new ModerationCheck("REQUIRED_FIELDS", false, "Не заполнено: " + String.join(", ", missing));
    }

    private ModerationCheck startsInFuture(EventCommand version) {
        boolean future = version.startsAt() != null && version.startsAt().isAfter(OffsetDateTime.now(clock));
        return new ModerationCheck("STARTS_IN_FUTURE", future,
                future ? "Мероприятие еще не началось" : "Дата начала уже прошла");
    }

    /** Изображения jpg 0.1–5 МБ, документы только docx до 20 МБ (п. 4.11.6, 4.12.6, 4.14.2). */
    private ModerationCheck files(List<EventMedia> media) {
        List<String> invalid = media.stream()
                .filter(EventMedia::isFile)
                .filter(m -> !fileWithinLimits(m))
                .map(EventMedia::getOriginalFilename)
                .toList();
        long count = media.stream().filter(EventMedia::isFile).count();
        if (!invalid.isEmpty()) {
            return new ModerationCheck("FILES", false, "Не соответствуют формату или размеру: " + String.join(", ", invalid));
        }
        return new ModerationCheck("FILES", true, count == 0 ? "Файлов нет"
                : "Файлов: %d — формат и размер соответствуют требованиям".formatted(count));
    }

    /** Видео — только прямые ссылки на Google Drive (п. 4.1.7, 4.14.2). */
    private static ModerationCheck videoLinks(List<EventMedia> media) {
        List<EventMedia> videos = media.stream().filter(m -> m.getType() == MediaType.VIDEO_LINK).toList();
        boolean valid = videos.stream().allMatch(m -> GOOGLE_DRIVE_LINK.matcher(m.getVideoUrl()).matches());
        return new ModerationCheck("VIDEO_LINKS", valid, videos.isEmpty() ? "Видеоссылок нет"
                : valid ? "Видеоссылки ведут на Google Drive; воспроизведение проверяет модератор"
                : "Есть ссылки не на Google Drive");
    }

    private boolean fileWithinLimits(EventMedia media) {
        long size = media.getSizeBytes();
        String name = media.getOriginalFilename() == null ? "" : media.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (media.getType() == MediaType.IMAGE) {
            return size >= limits.imageMinSize().toBytes() && size <= limits.imageMaxSize().toBytes()
                    && "image/jpeg".equals(media.getContentType());
        }
        return size <= limits.documentMaxSize().toBytes() && name.endsWith(".docx");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
