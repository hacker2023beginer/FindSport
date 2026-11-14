package by.findsport.web;

import by.findsport.config.OpenApiConfig;
import by.findsport.domain.Language;
import by.findsport.domain.Provider;
import by.findsport.service.ProviderModerationService;
import by.findsport.web.dto.ModerationHistoryItemResponse;
import by.findsport.web.dto.PageResponse;
import by.findsport.web.dto.ProviderModerationQueueItemResponse;
import by.findsport.web.dto.ProviderModerationReviewResponse;
import by.findsport.web.dto.ProviderStatusResponse;
import by.findsport.web.dto.RejectRequest;
import by.findsport.web.mapper.ApiMapper;
import by.findsport.web.mapper.AuthorStatusLabels;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Подтверждение профилей организаций и тренеров (ТЗ п. 4.10.5, 4.14.3). Доступ: модератор, администратор. */
@Tag(name = "Панель модератора: поставщики", description = "Подтверждение профилей организаций и тренеров")
@SecurityRequirement(name = OpenApiConfig.SESSION_AUTH)
@RestController
@RequestMapping("/api/v1/moderation/providers")
@RequiredArgsConstructor
public class ProviderModerationController {

    private final ProviderModerationService moderation;

    @Operation(summary = "Очередь: запросы на подтверждение и правки опубликованных профилей")
    @GetMapping
    public PageResponse<ProviderModerationQueueItemResponse> queue(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(moderation.queue(page, size), ApiMapper::providerModerationQueueItem);
    }

    @Operation(summary = "Diff-режим: отправленная и опубликованная версии профиля, измененные поля")
    @GetMapping("/{id}")
    public ProviderModerationReviewResponse review(@PathVariable Long id) {
        return ApiMapper.providerModerationReview(moderation.review(id));
    }

    @Operation(summary = "Журнал отправок и решений модераторов по профилю")
    @GetMapping("/{id}/history")
    public List<ModerationHistoryItemResponse> history(@PathVariable Long id) {
        return moderation.history(id).stream().map(ApiMapper::moderationHistoryItem).toList();
    }

    @Operation(summary = "Подтвердить (опубликовать) профиль или его правки")
    @PostMapping("/{id}/approve")
    public ProviderStatusResponse approve(@PathVariable Long id, Authentication auth, Locale locale) {
        return status(moderation.approve(auth.getName(), id), locale);
    }

    @Operation(summary = "Отклонить с обязательной причиной",
            description = "Новый профиль получает статус REJECTED («Требует доработки» для владельца); "
                    + "у опубликованного отклоняются только правки, публичная версия не меняется")
    @PostMapping("/{id}/reject")
    public ProviderStatusResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request,
                                         Authentication auth, Locale locale) {
        return status(moderation.reject(auth.getName(), id, request.reason().trim()), locale);
    }

    private static ProviderStatusResponse status(Provider provider, Locale locale) {
        return new ProviderStatusResponse(provider.getId(), provider.getStatus(),
                AuthorStatusLabels.label(provider.getStatus(), Language.from(locale)), provider.getRejectionReason());
    }
}
