package by.findsport.web;

import by.findsport.config.OpenApiConfig;
import by.findsport.domain.Language;
import by.findsport.service.ProviderEventService;
import by.findsport.service.command.EventCommand;
import by.findsport.service.command.OfferCommand;
import by.findsport.web.dto.EventRequest;
import by.findsport.web.dto.LocalizedTextInput;
import by.findsport.web.dto.LocalizedTitleInput;
import by.findsport.web.dto.PageResponse;
import by.findsport.web.dto.ProviderEventResponse;
import by.findsport.web.dto.ProviderEventSummaryResponse;
import by.findsport.web.dto.VideoLinkRequest;
import by.findsport.web.error.NotImplementedYetException;
import by.findsport.web.mapper.ApiMapper;
import by.findsport.web.validation.AllowedFile;
import by.findsport.web.validation.FileKind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Карточки мероприятий в кабинете поставщика (ТЗ п. 4.2.6–4.2.7, 4.6.7):
 * черновик → отправка на модерацию → публикация модератором (Приложение 3).
 */
@Tag(name = "Кабинет поставщика: мероприятия", description = "Создание и редактирование карточек, отправка на модерацию")
@SecurityRequirement(name = OpenApiConfig.BASIC_AUTH)
@RestController
@RequestMapping("/api/v1/provider/events")
@RequiredArgsConstructor
public class ProviderEventController {

    private final ProviderEventService events;

    @GetMapping
    public PageResponse<ProviderEventSummaryResponse> listOwn(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication auth, Locale locale) {
        Language language = Language.from(locale);
        return PageResponse.of(events.listOwn(auth.getName(), page, size),
                view -> ApiMapper.providerEventSummary(view, language));
    }

    @GetMapping("/{id}")
    public ProviderEventResponse get(@PathVariable Long id, Authentication auth, Locale locale) {
        return ApiMapper.providerEvent(events.get(auth.getName(), id), Language.from(locale));
    }

    @Operation(summary = "Создать карточку в статусе «Черновик»")
    @PostMapping
    public ResponseEntity<ProviderEventResponse> create(@Valid @RequestBody EventRequest request, Authentication auth,
                                                        Locale locale) {
        ProviderEventResponse created = ApiMapper.providerEvent(
                events.create(auth.getName(), toCommand(request)), Language.from(locale));
        return ResponseEntity.created(URI.create("/api/v1/provider/events/" + created.id())).body(created);
    }

    @Operation(summary = "Изменить карточку",
            description = "Существенные изменения опубликованной карточки (название, дата, время, место, описание) "
                    + "уходят на модерацию, а публично до одобрения видна прежняя версия (поле revision)")
    @PutMapping("/{id}")
    public ProviderEventResponse update(@PathVariable Long id, @Valid @RequestBody EventRequest request,
                                        Authentication auth, Locale locale) {
        return ApiMapper.providerEvent(events.update(auth.getName(), id, toCommand(request)), Language.from(locale));
    }

    @Operation(summary = "Отправить черновик или карточку, требующую доработки, на модерацию")
    @PostMapping("/{id}/submit")
    public ProviderEventResponse submit(@PathVariable Long id, Authentication auth, Locale locale) {
        return ApiMapper.providerEvent(events.submitForModeration(auth.getName(), id), Language.from(locale));
    }

    /** Фото jpg 0.1–5 МБ (п. 4.11.6, 4.12.6, решение PM). Хранилище файлов — следующий этап. */
    @Operation(summary = "Загрузить фото (jpg, 0.1–5 МБ) — пока возвращает 501 после проверки файла")
    @PostMapping(path = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void uploadImage(@PathVariable Long id, @RequestPart("file") @AllowedFile(FileKind.IMAGE) MultipartFile file) {
        throw new NotImplementedYetException("Загрузка изображений будет реализована вместе с файловым хранилищем");
    }

    /** Документы только docx до 20 МБ (п. 4.12.6, 4.14.2). */
    @Operation(summary = "Загрузить документ (docx, до 20 МБ) — пока возвращает 501 после проверки файла")
    @PostMapping(path = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void uploadDocument(@PathVariable Long id,
                               @RequestPart("file") @AllowedFile(FileKind.DOCUMENT) MultipartFile file) {
        throw new NotImplementedYetException("Загрузка документов будет реализована вместе с файловым хранилищем");
    }

    /** Видеоссылка на Google Drive (п. 4.1.7). */
    @Operation(summary = "Добавить видеоссылку на Google Drive — пока возвращает 501 после проверки ссылки")
    @PostMapping("/{id}/videos")
    public void addVideoLink(@PathVariable Long id, @Valid @RequestBody VideoLinkRequest request) {
        throw new NotImplementedYetException("Добавление видеоссылок будет реализовано вместе с медиаматериалами");
    }

    private static EventCommand toCommand(EventRequest request) {
        return new EventCommand(
                request.providerId(),
                request.type(),
                request.title().toDomain(),
                request.description().toDomain(),
                request.conditions().toDomain(),
                LocalizedTextInput.toDomainOrNull(request.program()),
                LocalizedTitleInput.toDomainOrNull(request.skillLevel()),
                request.sportIds(),
                request.startsAt(),
                request.endsAt(),
                request.regionId(),
                request.address().toDomain(),
                request.ageMin(),
                request.ageMax(),
                request.participantLimit(),
                request.registrationDeadline(),
                Inputs.text(request.contactPhone()),
                Inputs.email(request.contactEmail()),
                request.offers().stream()
                        .map(o -> new OfferCommand(o.id(), o.title().toDomain(), o.price()))
                        .toList());
    }
}
