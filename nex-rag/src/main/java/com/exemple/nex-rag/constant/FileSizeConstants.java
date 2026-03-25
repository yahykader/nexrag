package com.exemple.nexrag.constant;

/**
 * Constantes de taille de fichier.
 *
 * Clean code : élimine les magic numbers éparpillés dans le code.
 */
public final class FileSizeConstants {

    public static final long MAX_FILE_SIZE_BYTES    = 5L * 1024 * 1024 * 1024; // 5 GB
    public static final long MAX_FILE_SIZE_MB       = MAX_FILE_SIZE_BYTES / 1_000_000;
    public static final long STREAMING_THRESHOLD    = 100_000_000L;             // 100 MB

    private FileSizeConstants() {}
}