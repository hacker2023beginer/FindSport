package by.findsport.web;

import by.findsport.domain.Language;
import by.findsport.service.EventMediaService;
import by.findsport.service.EventSearchService;
import by.findsport.web.dto.EventDetailsResponse;
import by.findsport.web.dto.EventListItemResponse;
import by.findsport.web.dto.EventSearchRequest;
import by.findsport.web.dto.PageResponse;
import by.findsport.web.mapper.ApiMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Публичный поиск и карточки мероприятий — доступны гостю (ТЗ п. 4.2.1, 4.7, 4.9).
 * Язык ответа выбирается заголовком {@code Accept-Language} (ru по умолчанию).
 */
@Tag(name = "Мероприятия (публичные)", description = "Поиск и карточки опубликованных мероприятий, доступны гостю")
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class PublicEventController {

    private final EventSearchService search;
    private final EventMediaService media;

    @Operation(summary = "Поиск предстоящих мероприятий",
            description = "Регистронезависимый поиск по частичному совпадению; сортировка по новизне; цены в BYN")
    @GetMapping
    public PageResponse<EventListItemResponse> search(@Valid @ParameterObject @ModelAttribute EventSearchRequest request,
                                                      Locale locale) {
        Language language = Language.from(locale);
        return PageResponse.of(
                search.search(request.toCriteria(), request.pageOrDefault(), request.sizeOrDefault()),
                event -> ApiMapper.eventListItem(event, language));
    }

    @Operation(summary = "Детальная карточка опубликованного мероприятия")
    @GetMapping("/{id}")
    public EventDetailsResponse get(@PathVariable Long id, Locale locale) {
        return ApiMapper.eventDetails(search.getPublished(id), Language.from(locale));
    }

    @Operation(summary = "Фото или документ опубликованной карточки",
            description = "Доступны только материалы, одобренные модератором")
    @GetMapping("/{id}/media/{mediaId}")
    public ResponseEntity<Resource> media(@PathVariable Long id, @PathVariable Long mediaId) {
        // Без кэширования: снятый с публикации материал не должен оставаться доступным
        return MediaFiles.response(media.publicFile(id, mediaId), CacheControl.noCache());
    }
}
