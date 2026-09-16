package com.aiquote.backend.file;

/**
 * Magic-byte (file signature) checks so upload validation doesn't rely solely on the
 * client-declared Content-Type/filename, both of which are trivially spoofable (Etap 21
 * security audit — a multipart request can declare any Content-Type regardless of the
 * bytes actually sent). Checked in addition to, not instead of, the existing
 * declared-type/extension allowlists.
 */
public final class FileSignature {

    private FileSignature() {
    }

    public static boolean isJpeg(byte[] bytes) {
        return startsWith(bytes, 0xFF, 0xD8, 0xFF);
    }

    public static boolean isPng(byte[] bytes) {
        return startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
    }

    public static boolean isWebp(byte[] bytes) {
        return bytes.length >= 12
                && startsWith(bytes, 'R', 'I', 'F', 'F')
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }

    public static boolean isImage(byte[] bytes, String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> isJpeg(bytes);
            case "image/png" -> isPng(bytes);
            case "image/webp" -> isWebp(bytes);
            default -> false;
        };
    }

    public static boolean isPdf(byte[] bytes) {
        return startsWith(bytes, '%', 'P', 'D', 'F', '-');
    }

    /** .xlsx (OOXML) is a ZIP container; .xls (legacy binary) uses the OLE2 signature. */
    public static boolean isExcel(byte[] bytes) {
        return startsWith(bytes, 0x50, 0x4B, 0x03, 0x04) || startsWith(bytes, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
    }

    private static boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xFF) != (signature[i] & 0xFF)) {
                return false;
            }
        }
        return true;
    }
}
