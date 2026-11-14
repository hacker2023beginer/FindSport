package by.findsport.service;

import by.findsport.domain.CardStatus;
import by.findsport.domain.Event;
import by.findsport.domain.EventMedia;
import by.findsport.domain.MediaChange;
import by.findsport.domain.MediaType;
import by.findsport.domain.User;
import by.findsport.repository.EventMediaRepository;
import by.findsport.repository.EventModerationRepository;
import by.findsport.repository.EventRepository;
import by.findsport.service.command.UploadedFile;
import by.findsport.service.exception.ConflictException;
import by.findsport.service.exception.ForbiddenException;
import by.findsport.service.exception.NotFoundException;
import by.findsport.service.storage.FileStorage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Фотографии, документы и видеоссылки карточки мероприятия (ТЗ п. 4.1.7, 4.2.6, 4.13.7).
 * <p>
 * Замена и добавление медиафайлов — существенное изменение (п. 4.14.1). Новый материал не виден посетителям,
 * пока карточку или ее правки не одобрит модератор; удаление опубликованного материала тоже вступает в силу
 * только после одобрения. Изменения опубликованной карточки автор направляет на модерацию через
 * {@code submit}, а пока правки ждут решения, материалы не меняются.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class EventMediaService {

    private static final int MAX_FILENAME_LENGTH = 255;

    private final EventRepository events;
    private final EventMediaRepository medias;
    private final EventModerationRepository moderations;
    private final UserAccountService accounts;
    private final FileStorage storage;
    private final ModerationAudit audit;

    public EventMedia upload(String userEmail, Long eventId, UploadedFile file) {
        User user = accounts.requireByEmail(userEmail);
        Event event = loadForChange(eventId, user);
        String extension = file.type() == MediaType.IMAGE ? ".jpg" : ".docx";
        EventMedia media = new EventMedia(event, file.type());
        media.setStorageKey(storage.store("events/" + eventId, extension, file.content()));
        media.setOriginalFilename(displayName(file.filename(), extension));
        media.setContentType(file.contentType());
        media.setSizeBytes(file.size());
        return attach(media, user);
    }

    /** Видеоматериал — только ссылка на Google Drive (п. 4.1.7); формат ссылки проверен на входе. */
    public EventMedia addVideoLink(String userEmail, Long eventId, String url) {
        User user = accounts.requireByEmail(userEmail);
        EventMedia media = new EventMedia(loadForChange(eventId, user), MediaType.VIDEO_LINK);
        media.setVideoUrl(url);
        return attach(media, user);
    }

    /**
     * Неопубликованный материал удаляется сразу вместе с файлом. Опубликованный помечается к удалению
     * и остается в публичной версии до одобрения изменений модератором.
     */
    public void remove(String userEmail, Long eventId, Long mediaId) {
        User user = accounts.requireByEmail(userEmail);
        loadForChange(eventId, user);
        EventMedia media = medias.findByIdAndEventId(mediaId, eventId)
                .orElseThrow(() -> new NotFoundException("Материал не найден"));
        switch (media.pendingChange()) {
            case ADDED -> delete(media, user);
            case UNCHANGED -> {
                media.setRemovalRequested(true);
                audit.mediaRemovalRequested(media, user);
            }
            case REMOVED -> throw new ConflictException("Материал уже отмечен к удалению");
        }
    }

    /** Все материалы карточки, включая ожидающие модерации. */
    @Transactional(readOnly = true)
    public List<EventMedia> listAll(Long eventId) {
        return medias.findByEventIdOrderByIdAsc(eventId);
    }

    /** Файл для автора, модератора и администратора — в том числе еще не опубликованный. */
    @Transactional(readOnly = true)
    public StoredMedia authorFile(String userEmail, Long eventId, Long mediaId) {
        loadManageable(eventId, accounts.requireByEmail(userEmail));
        return file(medias.findByIdAndEventId(mediaId, eventId).orElse(null));
    }

    /** Файл для посетителя: только одобренный материал опубликованной карточки. */
    @Transactional(readOnly = true)
    public StoredMedia publicFile(Long eventId, Long mediaId) {
        boolean published = events.findById(eventId).map(e -> e.getStatus() == CardStatus.PUBLISHED).orElse(false);
        EventMedia media = published ? medias.findByIdAndEventId(mediaId, eventId).orElse(null) : null;
        return file(media != null && media.isPublished() ? media : null);
    }

    /**
     * Одобрение модератором: добавленные материалы публикуются, отмеченные к удалению удаляются
     * вместе с файлами после фиксации транзакции.
     */
    void publishPending(Long eventId, User moderator) {
        for (EventMedia media : medias.findByEventIdOrderByIdAsc(eventId)) {
            switch (media.pendingChange()) {
                case ADDED -> media.setPublished(true);
                case REMOVED -> delete(media, moderator);
                case UNCHANGED -> {
                }
            }
        }
    }

    /** У опубликованной карточки есть изменения материалов, еще не направленные на модерацию. */
    boolean hasPendingChanges(Long eventId) {
        return medias.findByEventIdOrderByIdAsc(eventId).stream()
                .anyMatch(media -> media.pendingChange() != MediaChange.UNCHANGED);
    }

    private EventMedia attach(EventMedia media, User user) {
        EventMedia saved = medias.save(media);
        audit.mediaAdded(saved, user);
        return saved;
    }

    private void delete(EventMedia media, User user) {
        medias.delete(media);
        if (media.isFile()) {
            storage.deleteAfterCommit(media.getStorageKey());
        }
        audit.mediaDeleted(media, user);
    }

    private StoredMedia file(EventMedia media) {
        if (media == null || !media.isFile()) {
            throw new NotFoundException("Файл не найден");
        }
        return new StoredMedia(media, storage.load(media.getStorageKey()));
    }

    private Event loadForChange(Long eventId, User user) {
        Event event = loadManageable(eventId, user);
        if (!event.isEditable()) {
            throw new ConflictException("Материалы карточки в статусе " + event.getStatus() + " изменять нельзя");
        }
        if (moderations.findFirstByEventIdAndDecisionIsNull(eventId).isPresent()) {
            throw new ConflictException("Изменения карточки на модерации — дождитесь решения модератора");
        }
        return event;
    }

    private Event loadManageable(Long eventId, User user) {
        Event event = events.findWithDetailsById(eventId)
                .orElseThrow(() -> new NotFoundException("Мероприятие не найдено"));
        if (!event.getProvider().isManageableBy(user)) {
            throw new ForbiddenException("Нет прав на управление этой карточкой");
        }
        return event;
    }

    /** Только имя без пути и управляющих символов; используется для отображения и скачивания. */
    private static String displayName(String filename, String extension) {
        String name = StringUtils.getFilename(StringUtils.cleanPath(filename == null ? "" : filename.replace('\\', '/')));
        name = name == null ? "" : name.replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) {
            return "file" + extension;
        }
        return name.length() <= MAX_FILENAME_LENGTH ? name : name.substring(name.length() - MAX_FILENAME_LENGTH);
    }
}
