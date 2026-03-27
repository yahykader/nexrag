package com.exemple.nexrag.service.rag.ingestion.deduplication.text;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("Spec : TextLocalCache — Cache mémoire thread-safe pour déduplication texte")
class TextLocalCacheSpec {

    private TextLocalCache cache;

    @BeforeEach
    void setUp() {
        // Nouvelle instance par test — isolé comme un batch
        cache = new TextLocalCache();
    }

    @Test
    @DisplayName("DOIT retourner true lors du premier ajout d'une clé (clé nouvelle)")
    void devraitRetournerTrueAuPremierAjout() {
        assertThat(cache.addIfAbsent("cle-nouvelle")).isTrue();
    }

    @Test
    @DisplayName("DOIT retourner false si la même clé est ajoutée une seconde fois (doublon)")
    void devraitRetournerFalsePourDoublon() {
        cache.addIfAbsent("cle-doublon");
        assertThat(cache.addIfAbsent("cle-doublon")).isFalse();
    }

    @Test
    @DisplayName("DOIT confirmer la présence d'une clé après add()")
    void devraitConteniirCleApresAdd() {
        cache.add("cle-ajoutee");
        assertThat(cache.contains("cle-ajoutee")).isTrue();
    }

    @Test
    @DisplayName("DOIT retourner false pour contains() si la clé est absente")
    void devraitRetournerFalsePourCleAbsente() {
        assertThat(cache.contains("inexistante")).isFalse();
    }

    @Test
    @DisplayName("DOIT vider toutes les clés après clear()")
    void devraitViderToutesLesClesApresClean() {
        cache.add("cle-1");
        cache.add("cle-2");
        cache.add("cle-3");

        cache.clear();

        assertThat(cache.size()).isZero();
        assertThat(cache.contains("cle-1")).isFalse();
        assertThat(cache.contains("cle-2")).isFalse();
    }

    @Test
    @DisplayName("DOIT comptabiliser correctement le nombre d'entrées distinctes")
    void devraitComptabiliserEntreesDistinctes() {
        cache.add("a");
        cache.add("b");
        cache.add("c");
        cache.add("a"); // doublon → pas incrémenté

        assertThat(cache.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("DOIT avoir size()==0 pour un cache nouvellement créé")
    void devraitAvoirSizeZeroPourCacheVide() {
        assertThat(cache.size()).isZero();
    }

    @Test
    @DisplayName("DOIT permettre d'ajouter à nouveau après clear()")
    void devraitPermettreAjoutApresClean() {
        cache.add("avant-clear");
        cache.clear();
        cache.add("avant-clear"); // doit être traité comme nouveau

        assertThat(cache.contains("avant-clear")).isTrue();
        assertThat(cache.size()).isEqualTo(1);
    }
}
