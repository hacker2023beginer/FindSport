package by.findsport.web.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import by.findsport.TestFiles;
import by.findsport.config.FileUploadProperties;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

/**
 * Модульные проверки загружаемых файлов (ТЗ п. 4.13.7, 4.11.6, 4.12.6): расширение, тип, размер
 * и содержимое — подмена расширения или сигнатуры не проходит.
 */
class AllowedFileValidatorTests {

    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final FileUploadProperties limits = new FileUploadProperties(DataSize.ofKilobytes(100),
            DataSize.ofMegabytes(5), DataSize.ofMegabytes(20), Path.of("unused"));
    private final ConstraintValidatorContext context = mock(ConstraintValidatorContext.class, RETURNS_DEEP_STUBS);

    @Test
    void acceptsRealJpegWithinLimits() {
        assertThat(image("photo.jpeg", "image/jpeg", TestFiles.jpeg(120 * 1024))).isTrue();
    }

    @Test
    void rejectsImageOutsideSizeLimits() {
        assertThat(image("photo.jpg", "image/jpeg", TestFiles.jpeg(1))).isFalse();
        assertThat(validator(FileKind.IMAGE, new FileUploadProperties(DataSize.ofKilobytes(100),
                DataSize.ofKilobytes(110), DataSize.ofMegabytes(20), Path.of("unused")))
                .isValid(new MockMultipartFile("file", "photo.jpg", "image/jpeg", TestFiles.jpeg(150 * 1024)), context))
                .isFalse();
    }

    @Test
    void rejectsSpoofedImages() {
        byte[] jpeg = TestFiles.jpeg(120 * 1024);
        assertThat(image("photo.png", "image/jpeg", jpeg)).isFalse();
        assertThat(image("photo.jpg", "image/png", jpeg)).isFalse();
        // Сигнатура JPEG, за которой нет изображения
        byte[] fake = new byte[150 * 1024];
        Arrays.fill(fake, (byte) 7);
        fake[0] = (byte) 0xFF;
        fake[1] = (byte) 0xD8;
        fake[2] = (byte) 0xFF;
        assertThat(image("photo.jpg", "image/jpeg", fake)).isFalse();
        // Документ, переименованный в jpg
        byte[] docx = Arrays.copyOf(TestFiles.docx(), 150 * 1024);
        assertThat(image("photo.jpg", "image/jpeg", docx)).isFalse();
    }

    @Test
    void acceptsOnlyWordDocuments() {
        FileKindCheck documents = (name, type, bytes) -> validator(FileKind.DOCUMENT, limits)
                .isValid(new MockMultipartFile("file", name, type, bytes), context);

        assertThat(documents.isValid("rules.docx", DOCX, TestFiles.docx())).isTrue();
        assertThat(documents.isValid("rules.doc", "application/msword", TestFiles.docx())).isFalse();
        assertThat(documents.isValid("rules.docx", DOCX, new byte[] {0x50, 0x4B, 0x03, 0x04, 1, 2, 3})).isFalse();
        assertThat(documents.isValid("rules.docx", DOCX, TestFiles.jpeg(1))).isFalse();
        assertThat(documents.isValid("rules.docx", DOCX, new byte[0])).isFalse();
    }

    private boolean image(String name, String type, byte[] bytes) {
        return validator(FileKind.IMAGE, limits).isValid(new MockMultipartFile("file", name, type, bytes), context);
    }

    private static AllowedFileValidator validator(FileKind kind, FileUploadProperties limits) {
        AllowedFileValidator validator = new AllowedFileValidator(limits);
        validator.initialize(annotation(kind));
        return validator;
    }

    private static AllowedFile annotation(FileKind kind) {
        return new AllowedFile() {
            @Override
            public FileKind value() {
                return kind;
            }

            @Override
            public String message() {
                return "Недопустимый файл";
            }

            @Override
            public Class<?>[] groups() {
                return new Class<?>[0];
            }

            @SuppressWarnings("unchecked")
            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() {
                return new Class[0];
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return AllowedFile.class;
            }
        };
    }

    @FunctionalInterface
    private interface FileKindCheck {
        boolean isValid(String name, String type, byte[] bytes);
    }
}
