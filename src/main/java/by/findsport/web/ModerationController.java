package by.findsport.web;

import by.findsport.service.ModerationService;
import by.findsport.web.dto.EventStatusResponse;
import by.findsport.web.dto.ModerationQueueItemResponse;
import by.findsport.web.dto.ModerationReviewResponse;
import by.findsport.web.dto.PageResponse;
import by.findsport.web.dto.RejectRequest;
import by.findsport.web.mapper.ApiMapper;
import by.findsport.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Панель модератора (ТЗ п. 4.5.4, 4.14.3). Доступ: модератор, администратор. */
@Tag(name = "Панель модератора", description = "Очередь модерации, Diff-режим, одобрение и отклонение")
@SecurityRequirement(name = OpenApiConfig.SESSION_AUTH)
@RestController
@RequestMapping("/api/v1/moderation/events")
@RequiredArgsConstructor
public class ModerationController {

    private final ModerationService moderation;

    @Operation(summary = "Очередь: новые карточки и правки опубликованных карточек")
    @GetMapping
    public PageResponse<ModerationQueueItemResponse> queue(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(moderation.queue(page, size), ApiMapper::moderationQueueItem);
    }

    /** Отправленная версия, действующая опубликованная и список измененных полей (Diff-режим). */
    @Operation(summary = "Diff-режим: отправленная и опубликованная версии, измененные поля")
    @GetMapping("/{id}")
    public ModerationReviewResponse review(@PathVariable Long id) {
        return ApiMapper.moderationReview(moderation.review(id));
    }

    @Operation(summary = "Одобрить (опубликовать) отправленную версию")
    @PostMapping("/{id}/approve")
    public EventStatusResponse approve(@PathVariable Long id, Authentication auth) {
        return ApiMapper.eventStatus(moderation.approve(auth.getName(), id));
    }

    @Operation(summary = "Отклонить с обязательной причиной",
            description = "Новая карточка получает статус REJECTED («Требует доработки» для автора); "
                    + "у опубликованной отклоняются только правки, публичная версия не меняется")
    @PostMapping("/{id}/reject")
    public EventStatusResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request,
                                      Authentication auth) {
        return ApiMapper.eventStatus(moderation.reject(auth.getName(), id, request.reason().trim()));
    }
}
