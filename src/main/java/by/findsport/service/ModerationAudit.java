package by.findsport.service;

import by.findsport.domain.EventMedia;
import by.findsport.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Регистрация публикации, отклонения, архивирования и удаления материалов (ТЗ п. 4.13.4)
 * в отдельной категории журнала {@code by.findsport.moderation}. Решения модератора дополнительно
 * хранятся в БД (журнал отправок); пользователи указываются только идентификаторами (п. 4.13.13).
 */
@Component
public class ModerationAudit {

    private static final Logger log = LoggerFactory.getLogger("by.findsport.moderation");

    public void submitted(String object, Long id, User author) {
        log.info("SUBMITTED {}={} by userId={}", object, id, author.getId());
    }

    public void approved(String object, Long id, User moderator) {
        log.info("PUBLISHED {}={} by moderatorId={}", object, id, moderator.getId());
    }

    public void rejected(String object, Long id, User moderator) {
        log.info("REJECTED {}={} by moderatorId={}", object, id, moderator.getId());
    }

    public void archived(String object, Long id, User user) {
        log.info("ARCHIVED {}={} by userId={}", object, id, user.getId());
    }

    public void mediaAdded(EventMedia media, User author) {
        log.info("MEDIA_ADDED eventId={} mediaId={} type={} by userId={}",
                media.getEvent().getId(), media.getId(), media.getType(), author.getId());
    }

    public void mediaRemovalRequested(EventMedia media, User author) {
        log.info("MEDIA_REMOVAL_REQUESTED eventId={} mediaId={} by userId={}",
                media.getEvent().getId(), media.getId(), author.getId());
    }

    /** Файл удален из карточки и из хранилища: неопубликованный — сразу, опубликованный — после одобрения. */
    public void mediaDeleted(EventMedia media, User user) {
        log.info("MEDIA_DELETED eventId={} mediaId={} type={} by userId={}",
                media.getEvent().getId(), media.getId(), media.getType(), user.getId());
    }
}
