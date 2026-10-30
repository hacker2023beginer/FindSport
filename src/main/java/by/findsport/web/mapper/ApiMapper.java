package by.findsport.web.mapper;

import by.findsport.domain.BaseEntity;
import by.findsport.domain.CardStatus;
import by.findsport.domain.Event;
import by.findsport.domain.EventModeration;
import by.findsport.domain.EventOffer;
import by.findsport.domain.Language;
import by.findsport.domain.LocalizedText;
import by.findsport.domain.Provider;
import by.findsport.domain.Region;
import by.findsport.domain.Sport;
import by.findsport.domain.User;
import by.findsport.service.ModerationReview;
import by.findsport.service.ProviderEventView;
import by.findsport.service.PublicEventCard;
import by.findsport.web.dto.AddressDto;
import by.findsport.web.dto.AdminUserResponse;
import by.findsport.web.dto.CurrentUserResponse;
import by.findsport.web.dto.DictionaryItemResponse;
import by.findsport.web.dto.EventDetailsResponse;
import by.findsport.web.dto.EventListItemResponse;
import by.findsport.web.dto.EventStatusResponse;
import by.findsport.web.dto.LocalizedTextDto;
import by.findsport.web.dto.ModerationQueueItemResponse;
import by.findsport.web.dto.ModerationReviewResponse;
import by.findsport.web.dto.OfferResponse;
import by.findsport.web.dto.ProfileResponse;
import by.findsport.web.dto.ProviderEventResponse;
import by.findsport.web.dto.ProviderEventSummaryResponse;
import by.findsport.web.dto.ProviderProfileResponse;
import by.findsport.web.dto.PublicAddressResponse;
import by.findsport.web.dto.RevisionResponse;
import java.math.BigDecimal;
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
                card.registrationOpen());
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

    public static ProviderProfileResponse providerProfile(Provider provider) {
        return new ProviderProfileResponse(
                provider.getId(),
                provider.getType(),
                provider.getStatus(),
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
                review.approvedSnapshot());
    }

    private static RevisionResponse revision(EventModeration revision, Language language, boolean withVersion) {
        if (revision == null) {
            return null;
        }
        CardStatus status = revision.isPending() ? CardStatus.ON_MODERATION : CardStatus.REJECTED;
        return new RevisionResponse(status, AuthorStatusLabels.label(status, language), revision.getReason(),
                revision.getCreatedAt(), withVersion ? revision.getSnapshot() : null);
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
