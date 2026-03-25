package com.exemple.nexrag.constant;

/**
 * Constantes pour le controller Voice.
 *
 * Clean code : élimine le magic number 25 * 1024 * 1024.
 */
public final class VoiceConstants {

    /** Taille maximale d'un fichier audio : 25 MB. */
    public static final long MAX_AUDIO_SIZE_BYTES = 25L * 1024 * 1024;

    /** Langue par défaut pour la transcription. */
    public static final String DEFAULT_LANGUAGE = "fr";

    private VoiceConstants() {}
}