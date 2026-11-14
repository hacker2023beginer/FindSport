package by.findsport.web;

import by.findsport.config.OpenApiConfig;
import by.findsport.domain.EventMedia;
import by.findsport.domain.Language;
import by.findsport.service.EventMediaService;
import by.findsport.service.ProviderEventService;
import by.findsport.service.command.EventCommand;
import by.findsport.service.command.OfferCommand;
import by.findsport.service.command.UploadedFile;
import by.findsport.web.dto.EventRequest;
import by.findsport.web.dto.LocalizedTextInput;
import by.findsport.web.dto.LocalizedTitleInput;
import by.findsport.web.dto.MediaResponse;
import by.findsport.web.dto.PageResponse;
import by.findsport.web.dto.ProviderEventResponse;
import by.findsport.web.dto.ProviderEventSummaryResponse;
import by.findsport.web.dto.VideoLinkRequest;
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
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Карточки мероприятий в кабинете поставщика (ТЗ п. 4.2.6–4.2.7, 4.6.7):
 * черновик → отправка на модерацию → публикация модератором (Приложение 3) → архивирование;
 * фотографии, документы и видеоссылки (п. 4.1.7).
 */
@Tag(name = "Кабинет поставщика: мероприятия",
        description = "Создание и редактирование карточек, медиаматериалы, отправка на модерацию, архивирование")
@SecurityRequirement(name = OpenApiConfig.SESSION_AUTH)
@RestController
@RequestMapping("/api/v1/provider/events")
@RequiredArgsConstructor
public class ProviderEventController {

    private final ProviderEventService events;
    private final EventMediaService media;

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

    @Operation(summary = "Отправить на модерацию",
            description = "Черновик или карточка, требующая доработки, переходит в статус ON_MODERATION; "
                    + "у опубликованной карточки так отправляются изменения фото, документов и видеоссылок")
    @PostMapping("/{id}/submit")
    public ProviderEventResponse submit(@PathVariable Long id, Authentication auth, Locale locale) {
        return ApiMapper.providerEvent(events.submitForModeration(auth.getName(), id), Language.from(locale));
    }

    @Operation(summary = "Архивировать опубликованную карточку",
            description = "Карточка снимается с публикации; доступно автору, модератору и администратору")
    @PostMapping("/{id}/archive")
    public ProviderEventResponse archive(@PathVariable Long id, Authentication auth, Locale locale) {
        return ApiMapper.providerEvent(events.archive(auth.getName(), id), Language.from(locale));
    }

    /** Фото jpg 0.1–5 МБ (п. 4.11.6, 4.12.6, решение PM); проверяются расширение, тип, размер и содержимое. */
    @Operation(summary = "Загрузить фото (jpg, 0.1–5 МБ)",
            description = "У опубликованной карточки фото станет публичным после одобрения изменений модератором")
    @PostMapping(path = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaResponse> uploadImage(@PathVariable Long id,
                                                     @RequestPart("file") @AllowedFile(FileKind.IMAGE) MultipartFile file,
                                                     Authentication auth) {
        return created(media.upload(auth.getName(), id, uploaded(FileKind.IMAGE, file)));
    }

    /** Документы только docx до 20 МБ (п. 4.12.6, 4.14.2). */
    @Operation(summary = "Загрузить документ (docx, до 20 МБ)",
            description = "У опубликованной карточки документ станет публичным после одобрения изменений модератором")
    @PostMapping(path = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaResponse> uploadDocument(@PathVariable Long id,
                                                        @RequestPart("file") @AllowedFile(FileKind.DOCUMENT) MultipartFile file,
                                                        Authentication auth) {
        return created(media.upload(auth.getName(), id, uploaded(FileKind.DOCUMENT, file)));
    }

    /** Видеоссылка на Google Drive (п. 4.1.7). */
    @Operation(summary = "Добавить видеоссылку на Google Drive")
    @PostMapping("/{id}/videos")
    public ResponseEntity<MediaResponse> addVideoLink(@PathVariable Long id, @Valid @RequestBody VideoLinkRequest request,
                                                      Authentication auth) {
        return created(media.addVideoLink(auth.getName(), id, request.url().trim()));
    }

    @Operation(summary = "Удалить материал",
            description = "Неопубликованный материал удаляется сразу; опубликованный — после одобрения изменений")
    @DeleteMapping("/{id}/media/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMedia(@PathVariable Long id, @PathVariable Long mediaId, Authentication auth) {
        media.remove(auth.getName(), id, mediaId);
    }

    @Operation(summary = "Файл материала, в том числе еще не опубликованного (автор, модератор, администратор)")
    @GetMapping("/{id}/media/{mediaId}")
    public ResponseEntity<Resource> mediaFile(@PathVariable Long id, @PathVariable Long mediaId, Authentication auth) {
        return MediaFiles.response(media.authorFile(auth.getName(), id, mediaId), CacheControl.noStore());
    }

    private static ResponseEntity<MediaResponse> created(EventMedia media) {
        return ResponseEntity.created(URI.create("/api/v1/provider/events/%d/media/%d"
                        .formatted(media.getEvent().getId(), media.getId())))
                .body(ApiMapper.authorMedia(media));
    }

    /** Тип содержимого задает сервер по виду файла — значение клиента уже сверено валидатором. */
    private static UploadedFile uploaded(FileKind kind, MultipartFile file) {
        var type = kind == FileKind.IMAGE ? by.findsport.domain.MediaType.IMAGE : by.findsport.domain.MediaType.DOCUMENT;
        return new UploadedFile(type, file.getOriginalFilename(), kind.contentTypes().iterator().next(), file.getSize(),
                file);
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
