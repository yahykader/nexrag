package com.exemple.nexrag.service.rag.ingestion.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("Spec : MetadataSanitizer — Nettoyage des métadonnées")
class MetadataSanitizerSpec {

    private final MetadataSanitizer sanitizer = new MetadataSanitizer();

    @Test
    @DisplayName("DOIT retourner une map vide pour une entrée null")
    void devraitRetournerMapVidePourNull() {
        assertThat(sanitizer.sanitize(null)).isEmpty();
    }

    @Test
    @DisplayName("DOIT retourner une map vide pour une map vide en entrée")
    void devraitRetournerMapVidePourMapVide() {
        assertThat(sanitizer.sanitize(new HashMap<>())).isEmpty();
    }

    @Test
    @DisplayName("DOIT supprimer les entrées avec valeur null")
    void devraitSupprimerEnteesAvecValeurNull() {
        Map<String, Object> input = new HashMap<>();
        input.put("cle", null);
        assertThat(sanitizer.sanitize(input)).isEmpty();
    }

    @Test
    @DisplayName("DOIT supprimer les entrées avec clé null")
    void devraitSupprimerEntreesAvecCleNull() {
        Map<String, Object> input = new HashMap<>();
        input.put(null, "valeur");
        assertThat(sanitizer.sanitize(input)).isEmpty();
    }

    @Test
    @DisplayName("DOIT conserver les entrées valides avec String, Number et Boolean")
    void devraitConserverEntreesValides() {
        Map<String, Object> input = Map.of(
            "titre",    "document test",
            "pages",    42,
            "indexe",   true
        );
        Map<String, Object> result = sanitizer.sanitize(input);
        assertThat(result)
            .containsEntry("titre", "document test")
            .containsEntry("pages", 42)
            .containsEntry("indexe", true);
    }

    @Test
    @DisplayName("DOIT tronquer les clés dépassant 100 caractères")
    void devraitTronquerClesTropLongues() {
        String longKey = "k".repeat(150);
        Map<String, Object> input = new HashMap<>();
        input.put(longKey, "val");

        Map<String, Object> result = sanitizer.sanitize(input);
        assertThat(result).hasSize(1);
        String key = result.keySet().iterator().next();
        assertThat(key).hasSize(100);
    }

    @Test
    @DisplayName("DOIT tronquer les valeurs String dépassant 10 000 caractères")
    void devraitTronquerValeurStringTropLongue() {
        String longVal = "x".repeat(20_000);
        Map<String, Object> input = Map.of("k", longVal);

        Map<String, Object> result = sanitizer.sanitize(input);
        assertThat((String) result.get("k")).hasSize(10_000);
    }

    @Test
    @DisplayName("DOIT convertir les types non supportés en String via toString()")
    void devraitConvertirTypesNonSupportesEnString() {
        Object custom = new Object() {
            @Override public String toString() { return "ma-valeur-custom"; }
        };
        Map<String, Object> input = new HashMap<>();
        input.put("objet", custom);

        Map<String, Object> result = sanitizer.sanitize(input);
        assertThat(result.get("objet")).isEqualTo("ma-valeur-custom");
    }

    @Test
    @DisplayName("DOIT ne pas lever d'exception pour n'importe quelle entrée valide")
    void devraitNePasLeverException() {
        Map<String, Object> input = Map.of("a", "b", "c", 123);
        assertThatCode(() -> sanitizer.sanitize(input)).doesNotThrowAnyException();
    }
}
