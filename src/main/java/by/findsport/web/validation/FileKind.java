package by.findsport.web.validation;

import java.util.Set;

/** Допустимые виды загружаемых файлов (ТЗ п. 4.1.7, 4.13.7). */
public enum FileKind {

    /** Изображение JPEG: сигнатура FF D8 FF. */
    IMAGE(Set.of(".jpg", ".jpeg"), Set.of("image/jpeg"), new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),

    /** Документ DOCX (OOXML-контейнер ZIP): сигнатура PK\3\4. */
    DOCUMENT(Set.of(".docx"),
            Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            new byte[] {0x50, 0x4B, 0x03, 0x04});

    private final Set<String> extensions;
    private final Set<String> contentTypes;
    private final byte[] signature;

    FileKind(Set<String> extensions, Set<String> contentTypes, byte[] signature) {
        this.extensions = extensions;
        this.contentTypes = contentTypes;
        this.signature = signature;
    }

    public Set<String> extensions() {
        return extensions;
    }

    public Set<String> contentTypes() {
        return contentTypes;
    }

    public byte[] signature() {
        return signature.clone();
    }
}
