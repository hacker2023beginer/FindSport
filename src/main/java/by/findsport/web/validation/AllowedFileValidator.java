package by.findsport.web.validation;

import by.findsport.config.FileUploadProperties;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/** Экземпляр создается Spring-фабрикой валидаторов, поэтому пределы внедряются из конфигурации. */
public class AllowedFileValidator implements ConstraintValidator<AllowedFile, MultipartFile> {

    private static final String DOCX_CONTENT_TYPES = "[Content_Types].xml";
    private static final String DOCX_MAIN_PART = "word/document.xml";

    private final FileUploadProperties limits;
    private FileKind kind;

    public AllowedFileValidator(FileUploadProperties limits) {
        this.limits = limits;
    }

    @Override
    public void initialize(AllowedFile annotation) {
        this.kind = annotation.value();
    }

    @Override
    public boolean isValid(MultipartFile file, ConstraintValidatorContext context) {
        if (file == null || file.isEmpty()) {
            return reject(context, "Файл не передан");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (kind.extensions().stream().noneMatch(name::endsWith)) {
            return reject(context, "Допустимые расширения: " + String.join(", ", kind.extensions()));
        }
        if (file.getContentType() == null || !kind.contentTypes().contains(file.getContentType().toLowerCase(Locale.ROOT))) {
            return reject(context, "Недопустимый тип содержимого файла");
        }
        DataSize min = kind == FileKind.IMAGE ? limits.imageMinSize() : DataSize.ofBytes(1);
        DataSize max = kind == FileKind.IMAGE ? limits.imageMaxSize() : limits.documentMaxSize();
        if (file.getSize() < min.toBytes() || file.getSize() > max.toBytes()) {
            return reject(context, "Размер файла должен быть от %d КБ до %d МБ"
                    .formatted(min.toKilobytes(), max.toMegabytes()));
        }
        if (!hasSignature(file) || !hasValidStructure(file)) {
            return reject(context, "Содержимое файла не соответствует его формату");
        }
        return true;
    }

    private boolean hasSignature(MultipartFile file) {
        byte[] expected = kind.signature();
        try (InputStream in = file.getInputStream()) {
            return Arrays.equals(in.readNBytes(expected.length), expected);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Проверка содержимого глубже сигнатуры: JPEG читается декодером до размеров изображения
     * (без распаковки пикселей), DOCX — ZIP-контейнер с основной частью документа Word.
     */
    private boolean hasValidStructure(MultipartFile file) {
        return kind == FileKind.IMAGE ? isReadableJpeg(file) : isWordDocument(file);
    }

    private static boolean isReadableJpeg(MultipartFile file) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("jpeg");
        if (!readers.hasNext()) {
            return false;
        }
        ImageReader reader = readers.next();
        try (InputStream in = file.getInputStream();
             ImageInputStream image = new MemoryCacheImageInputStream(in)) {
            reader.setInput(image, true, true);
            return reader.getWidth(0) > 0 && reader.getHeight(0) > 0;
        } catch (IOException | RuntimeException e) {
            return false;
        } finally {
            reader.dispose();
        }
    }

    /** Центральный каталог ZIP читается без распаковки содержимого — архив-бомба не исполняется. */
    private static boolean isWordDocument(MultipartFile file) {
        Path temp = null;
        try {
            temp = Files.createTempFile("findsport-docx-", ".tmp");
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            try (ZipFile zip = new ZipFile(temp.toFile())) {
                return zip.getEntry(DOCX_CONTENT_TYPES) != null && zip.getEntry(DOCX_MAIN_PART) != null;
            }
        } catch (IOException | RuntimeException e) {
            return false;
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // временный файл удалит ОС
                }
            }
        }
    }

    private static boolean reject(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
