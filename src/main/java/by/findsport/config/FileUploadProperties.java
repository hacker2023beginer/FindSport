package by.findsport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Ограничения загружаемых файлов (ТЗ п. 4.11.6, 4.12.6): изображение jpg от 0.1 до 5 МБ,
 * документ docx до 20 МБ. Значения вынесены в конфигурацию, чтобы администратор мог их менять.
 */
@ConfigurationProperties("findsport.uploads")
public record FileUploadProperties(
        @DefaultValue("100KB") DataSize imageMinSize,
        @DefaultValue("5MB") DataSize imageMaxSize,
        @DefaultValue("20MB") DataSize documentMaxSize) {
}
