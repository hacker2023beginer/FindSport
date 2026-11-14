package by.findsport.web.mapper;

import by.findsport.domain.BaseEntity;
import by.findsport.domain.CardStatus;
import by.findsport.domain.Event;
import by.findsport.domain.EventMedia;
import by.findsport.domain.EventModeration;
import by.findsport.domain.EventOffer;
import by.findsport.domain.Language;
import by.findsport.domain.LocalizedText;
import by.findsport.domain.MediaChange;
import by.findsport.domain.Provider;
import by.findsport.domain.ProviderModeration;
import by.findsport.domain.Region;
import by.findsport.domain.Sport;
import by.findsport.domain.User;
import by.findsport.service.ModerationCheck;
import by.findsport.service.ModerationReview;
import by.findsport.service.ProviderEventView;
import by.findsport.service.ProviderModerationReview;
import by.findsport.service.ProviderProfileView;
import by.findsport.service.PublicEventCard;
import by.findsport.web.dto.AddressDto;
import by.findsport.web.dto.AdminUserResponse;
import by.findsport.web.dto.CurrentUserResponse;
import by.findsport.web.dto.DictionaryItemResponse;
import by.findsport.web.dto.EventDetailsResponse;
import by.findsport.web.dto.EventListItemResponse;
import by.findsport.web.dto.EventStatusResponse;
import by.findsport.web.dto.LocalizedTextDto;
import by.findsport.web.dto.MediaResponse;
import by.findsport.web.dto.ModerationCheckResponse;
import by.findsport.web.dto.ModerationHistoryItemResponse;
import by.findsport.web.dto.ModerationQueueItemResponse;
import by.findsport.web.dto.ModerationReviewResponse;
import by.findsport.web.dto.OfferResponse;
import by.findsport.web.dto.ProfileResponse;
import by.findsport.web.dto.ProviderEventResponse;
import by.findsport.web.dto.ProviderEventSummaryResponse;
import by.findsport.web.dto.ProviderModerationQueueItemResponse;
import by.findsport.web.dto.ProviderModerationReviewResponse;
import by.findsport.web.dto.ProviderProfileResponse;
import by.findsport.web.dto.PublicAddressResponse;
import by.findsport.web.dto.PublicProviderResponse;
import by.findsport.web.dto.RevisionResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Преобразование сущностей в DTO API. Сущности приходят из сервисов с уже загруженными связями
 * (entity graph-ы), поэтому маппинг не порождает дополнительных запросов.
 */
public final class ApiMapper {

    private static final int SHORT_DESCRIPTION_LENGTH = 300;

    private ApiMapper() {
    }

    public static DictionaryItemResponse region(Region region, Language language) {
        return new DictionaryItemResponse(region.getId(), region.getCode(), region.getName().get(language));
    }

    public static DictionaryItemResponse sport(Sport sport, Language language) {
        return new DictionaryItemResponse(sport.getId(), sport.getCode(), sport.getName().get(language));
    }

    public static EventListItemResponse eventListItem(Event event, Language language) {
        BigDecimal minPrice = event.getOffers().stream()
                .map(EventOffer::getPrice)
                .min(BigDecimal::compareTo)
                .orElse(null);
        return new EventListItemResponse(
                event.getId(),
                event.getType(),
                event.getTitle().get(language),
                abbreviate(event.getDescription().get(language)),
                event.getSports().stream().map(s -> s.getName().get(language)).toList(),
                event.getStartsAt(),
                event.getEndsAt(),
                publicAddress(event, language),
                event.getProvider().getName().get(language),
                minPrice,
                minPrice == null ? null : EventOffer.CURRENCY,
                event.getAgeMin(),
                event.getAgeMax());
    }

    public static EventDetailsResponse eventDetails(PublicEventCard card, Language language) {
        Event event = card.event();
        return new EventDetailsResponse(
                event.getId(),
                event.getType(),
                event.getTitle().get(language),
                event.getDescription().get(language),
                event.getConditions().get(language),
                localized(event.getProgram(), language),
                localized(event.getSkillLevel(), language),
                event.getSports().stream().map(s -> sport(s, language)).toList(),
                event.getStartsAt(),
                event.getEndsAt(),
                publicAddress(event, language),
                event.getAgeMin(),
                event.getAgeMax(),
                event.getOffers().stream()
                        .map(o -> new OfferResponse(o.getId(), o.getTitle().get(language), o.getPrice(), EventOffer.CURRENCY))
                        .toList(),
                new EventDetailsResponse.OrganizerResponse(event.getProvider().getId(),
                        event.getProvider().getName().get(language)),
                event.getContactPhone(),
                event.getContactEmail(),
                event.getParticipantLimit(),
                card.freeSeats(),
                event.getRegistrationDeadline(),
                card.registrationOpen(),
                card.media().stream().map(m -> media(m, publicMediaUrl(m), null)).toList());
    }

    public static ProviderEventResponse providerEvent(ProviderEventView view, Language language) {
        Event event = view.event();
        return new ProviderEventResponse(
                event.getId(),
                event.getProvider().getId(),
                event.getStatus(),
                AuthorStatusLabels.label(event.getStatus(), language),
                event.getRejectionReason(),
                revision(view.revision(), language, true),
                event.getType(),
                LocalizedTextDto.of(event.getTitle()),
                LocalizedTextDto.of(event.getDescription()),
                LocalizedTextDto.of(event.getConditions()),
                LocalizedTextDto.of(event.getProgram()),
                LocalizedTextDto.of(event.getSkillLevel()),
                ids(event.getSports()),
                event.getStartsAt(),
                event.getEndsAt(),
                event.getRegion().getId(),
                AddressDto.of(event.getAddress()),
                event.getAgeMin(),
                event.getAgeMax(),
                event.getParticipantLimit(),
                event.getRegistrationDeadline(),
                event.getContactPhone(),
                event.getContactEmail(),
                event.getOffers().stream()
                        .map(o -> new ProviderEventResponse.Offer(o.getId(), LocalizedTextDto.of(o.getTitle()), o.getPrice(),
                                EventOffer.CURRENCY))
                        .toList(),
                view.media().stream().map(ApiMapper::authorMedia).toList(),
                event.getCreatedAt(),
                event.getUpdatedAt());
    }

    public static ProviderEventSummaryResponse providerEventSummary(ProviderEventView view, Language language) {
        Event event = view.event();
        return new ProviderEventSummaryResponse(
                event.getId(),
                event.getProvider().getId(),
                event.getProvider().getName().getRu(),
                event.getTitle().getRu(),
                event.getStatus(),
                AuthorStatusLabels.label(event.getStatus(), language),
                event.getRejectionReason(),
                revision(view.revision(), language, false),
                event.getStartsAt(),
                event.getUpdatedAt());
    }

    public static EventStatusResponse eventStatus(Event event) {
        return new EventStatusResponse(event.getId(), event.getStatus(), event.getRejectionReason());
    }

    public static ProviderProfileResponse providerProfile(ProviderProfileView view, Language language) {
        Provider provider = view.provider();
        ProviderModeration revision = view.revision();
        return new ProviderProfileResponse(
                provider.getId(),
                provider.getType(),
                provider.getStatus(),
                AuthorStatusLabels.label(provider.getStatus(), language),
                provider.getRejectionReason(),
                revision == null ? null : revision(revision.isPending(), revision.getReason(), revision.getCreatedAt(),
                        revision.getSnapshot(), language, true),
                LocalizedTextDto.of(provider.getName()),
                LocalizedTextDto.of(provider.getDescription()),
                ids(provider.getSports()),
                provider.getRegion().getId(),
                AddressDto.of(provider.getAddress()),
                LocalizedTextDto.of(provider.getServiceArea()),
                LocalizedTextDto.of(provider.getWorkingHours()),
                provider.getContactPhone(),
                provider.getContactEmail(),
                provider.isPhonePublic(),
                provider.isEmailPublic(),
                provider.getCreatedAt());
    }

    public static ModerationQueueItemResponse moderationQueueItem(EventModeration submission) {
        Event event = submission.getEvent();
        return new ModerationQueueItemResponse(
                submission.getId(),
                event.getId(),
                event.getTitle().getRu(),
                event.getProvider().getName().getRu(),
                event.getStartsAt(),
                submission.getCreatedAt(),
                event.getStatus() == CardStatus.PUBLISHED);
    }

    public static ModerationReviewResponse moderationReview(ModerationReview review) {
        return new ModerationReviewResponse(
                review.event().getId(),
                review.submission().getId(),
                review.submission().getCreatedAt(),
                review.submission().getSubmittedBy().getEmail(),
                review.changedFields(),
                review.submission().getSnapshot(),
                review.approvedSnapshot(),
                review.media().stream().map(ApiMapper::authorMedia).toList(),
                review.checks().stream().map(ApiMapper::moderationCheck).toList());
    }

    public static ModerationHistoryItemResponse moderationHistoryItem(EventModeration submission) {
        return new ModerationHistoryItemResponse(submission.getId(), submission.getCreatedAt(),
                submission.getSubmittedBy().getId(), submission.getDecision(), submission.getReason(),
                submission.getModerator() == null ? null : submission.getModerator().getId(), submission.getDecidedAt());
    }

    public static ModerationHistoryItemResponse moderationHistoryItem(ProviderModeration submission) {
        return new ModerationHistoryItemResponse(submission.getId(), submission.getCreatedAt(),
                submission.getSubmittedBy().getId(), submission.getDecision(), submission.getReason(),
                submission.getModerator() == null ? null : submission.getModerator().getId(), submission.getDecidedAt());
    }

    public static ProviderModerationQueueItemResponse providerModerationQueueItem(ProviderModeration submission) {
        Provider provider = submission.getProvider();
        return new ProviderModerationQueueItemResponse(
                submission.getId(),
                provider.getId(),
                provider.getType(),
                provider.getName().getRu(),
                submission.getCreatedAt(),
                provider.getStatus() == CardStatus.PUBLISHED);
    }

    public static ProviderModerationReviewResponse providerModerationReview(ProviderModerationReview review) {
        return new ProviderModerationReviewResponse(
                review.provider().getId(),
                review.submission().getId(),
                review.submission().getCreatedAt(),
                review.submission().getSubmittedBy().getEmail(),
                review.changedFields(),
                review.submission().getSnapshot(),
                review.approvedSnapshot());
    }

    /** Ожидает профиль с загруженными регионом и видами спорта; контакты — по флагам владельца (п. 4.10.6). */
    public static PublicProviderResponse publicProvider(Provider provider, Language language) {
        return new PublicProviderResponse(
                provider.getId(),
                provider.getType(),
                provider.getName().get(language),
                provider.getDescription().get(language),
                provider.getSports().stream()
                        .sorted(Comparator.comparing(Sport::getId))
                        .map(sport -> sport(sport, language))
                        .toList(),
                provider.getRegion().getName().get(language),
                AddressDto.of(provider.getAddress()),
                localized(provider.getServiceArea(), language),
                provider.getWorkingHours().get(language),
                provider.isPhonePublic() ? provider.getContactPhone() : null,
                provider.isEmailPublic() ? provider.getContactEmail() : null);
    }

    /** Материал в кабинете автора и в Diff-режиме: ссылка на файл ведет в закрытую часть API. */
    public static MediaResponse authorMedia(EventMedia media) {
        String url = media.isFile()
                ? "/api/v1/provider/events/%d/media/%d".formatted(media.getEvent().getId(), media.getId())
                : media.getVideoUrl();
        return media(media, url, media.pendingChange());
    }

    private static String publicMediaUrl(EventMedia media) {
        return media.isFile()
                ? "/api/v1/events/%d/media/%d".formatted(media.getEvent().getId(), media.getId())
                : media.getVideoUrl();
    }

    private static MediaResponse media(EventMedia media, String url, MediaChange change) {
        return new MediaResponse(media.getId(), media.getType(), media.getOriginalFilename(), media.getContentType(),
                media.getSizeBytes(), url, change);
    }

    private static ModerationCheckResponse moderationCheck(ModerationCheck check) {
        return new ModerationCheckResponse(check.code(), check.passed(), check.message());
    }

    private static RevisionResponse revision(EventModeration revision, Language language, boolean withVersion) {
        if (revision == null) {
            return null;
        }
        return revision(revision.isPending(), revision.getReason(), revision.getCreatedAt(), revision.getSnapshot(),
                language, withVersion);
    }

    private static RevisionResponse revision(boolean pending, String reason, Instant submittedAt, String snapshot,
                                             Language language, boolean withVersion) {
        CardStatus status = pending ? CardStatus.ON_MODERATION : CardStatus.REJECTED;
        return new RevisionResponse(status, AuthorStatusLabels.label(status, language), reason, submittedAt,
                withVersion ? snapshot : null);
    }

    private static PublicAddressResponse publicAddress(Event event, Language language) {
        var address = event.getAddress();
        return new PublicAddressResponse(event.getRegion().getName().get(language),
                address.getDistrict(), address.getLocality(), address.getStreet(), address.getBuilding());
    }

    private static String localized(LocalizedText text, Language language) {
        return text == null ? null : text.get(language);
    }

    private static String abbreviate(String text) {
        return text.length() <= SHORT_DESCRIPTION_LENGTH ? text : text.substring(0, SHORT_DESCRIPTION_LENGTH - 1) + "…";
    }

    public static CurrentUserResponse currentUser(User user) {
        return new CurrentUserResponse(user.getId(), user.getEmail(), user.getRole(), user.getDisplayName());
    }

    /** Ожидает пользователя с загруженными регионом и интересами ({@code findWithProfileByEmail}). */
    public static ProfileResponse profile(User user, Language language) {
        return new ProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getDisplayName(),
                user.getPhone(),
                user.getRegion() == null ? null : region(user.getRegion(), language),
                user.getSportInterests().stream()
                        .sorted(Comparator.comparing(Sport::getId))
                        .map(sport -> sport(sport, language))
                        .toList(),
                user.getPreferredLanguage(),
                user.isEmailVerified(),
                user.isPersonalizationConsent(),
                user.isEmailNotifications(),
                user.getConsentsAcceptedAt(),
                user.getDeletionRequestedAt(),
                user.getCreatedAt());
    }

    public static AdminUserResponse adminUser(User user) {
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getRole(), user.getDisplayName(),
                user.isEmailVerified(), user.isBlocked(), user.getLockedUntil(), user.getDeletionRequestedAt(),
                user.getCreatedAt());
    }

    private static List<Long> ids(Collection<? extends BaseEntity> entities) {
        return entities.stream().map(BaseEntity::getId).sorted().toList();
    }
}
