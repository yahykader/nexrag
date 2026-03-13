package com.exemple.nexrag.dto;

/**
 * Type d'embedding supporté par le système.
 *
 * Principe OCP  : ajouter un type ne modifie pas le code existant.
 * Clean code    : élimine les magic strings "text" / "image".
 */
public enum EmbeddingType {
    TEXT,
    IMAGE;

    /**
     * Convertit une chaîne case-insensitive en EmbeddingType.
     *
     * @param value chaîne à convertir
     * @return EmbeddingType correspondant
     * @throws IllegalArgumentException si la valeur est inconnue
     */
    public static EmbeddingType fromString(String value) {
        try {
            return EmbeddingType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Type invalide '" + value + "'. Valeurs acceptées : text, image"
            );
        }
    }
}