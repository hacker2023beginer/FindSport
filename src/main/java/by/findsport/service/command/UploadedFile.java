package by.findsport.service.command;

import by.findsport.domain.MediaType;
import org.springframework.core.io.InputStreamSource;

/**
 * Загруженный файл, уже прошедший проверку типа, расширения, размера и содержимого (ТЗ п. 4.13.7).
 *
 * @param type     {@link MediaType#IMAGE} или {@link MediaType#DOCUMENT}
 * @param filename исходное имя файла — только для отображения, в путь хранилища не попадает
 */
public record UploadedFile(MediaType type, String filename, String contentType, long size,
                           InputStreamSource content) {
}
