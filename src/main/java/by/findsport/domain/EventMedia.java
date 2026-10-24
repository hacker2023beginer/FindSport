package by.findsport.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Медиаматериал карточки: изображение jpg, документ docx или видеоссылка на Google Drive (ТЗ п. 4.1.7).
 * Файлы хранятся вне исполняемых каталогов; в БД — только ключ хранилища (п. 4.13.7).
 */
@Getter
@Setter
@Entity
@Table(name = "event_media")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventMedia extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaType type;

    @Column(name = "storage_key", length = 500)
    private String storageKey;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "video_url", length = 2048)
    private String videoUrl;

    public EventMedia(Event event, MediaType type) {
        this.event = event;
        this.type = type;
    }
}
