package by.findsport.service;

import by.findsport.domain.EventMedia;
import org.springframework.core.io.Resource;

/** Файл материала карточки для выдачи клиенту. */
public record StoredMedia(EventMedia media, Resource content) {
}
