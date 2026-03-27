package com.exemple.nexrag.service.rag.ingestion.deduplication.text;

import com.exemple.nexrag.dto.deduplication.text.TextDeduplicationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Spec : TextDeduplicationService — Orchestration déduplication texte")
class TextDeduplicationServiceSpec {

    @Mock
    private TextDeduplicationProperties props;

    @Mock
    private TextNormalizer normalizer;

    @Mock
    private TextLocalCache localCache;

    @Mock
    private TextDeduplicationStore store;

    @InjectMocks
    private TextDeduplicationService service;

    @BeforeEach
    void setUp() {
        when(props.isEnabled()).thenReturn(true);
        when(props.isBatchIdScope()).thenReturn(false);
        when(props.getTtlDays()).thenReturn(30);
        when(normalizer.hash(anyString())).thenReturn("hash-abc-123");
    }

    @Test
    @DisplayName("DOIT retourner true (nouveau) quand checkAndMark voit le texte pour la première fois")
    void devraitRetournerTruePourNouveauTexte() {
        when(localCache.addIfAbsent(anyString())).thenReturn(true);

        assertThat(service.checkAndMark("texte nouveau", "batch-001")).isTrue();
    }

    @Test
    @DisplayName("DOIT retourner false (doublon) quand checkAndMark voit un texte déjà présent")
    void devraitRetournerFalsePourDoublon() {
        when(localCache.addIfAbsent(anyString())).thenReturn(false);

        assertThat(service.checkAndMark("texte doublon", "batch-001")).isFalse();
    }

    @Test
    @DisplayName("DOIT retourner true (non-doublon) quand déduplication désactivée")
    void devraitRetournerTrueSiDesactive() {
        when(props.isEnabled()).thenReturn(false);

        boolean result = service.checkAndMark("texte", "batch");
        assertThat(result).isTrue();
        verify(localCache, never()).addIfAbsent(anyString());
    }

    @Test
    @DisplayName("DOIT retourner true (non-doublon) quand le texte est blank")
    void devraitRetournerTruePourTexteBlank() {
        boolean result = service.checkAndMark("   ", "batch");
        assertThat(result).isTrue();
        verify(localCache, never()).addIfAbsent(anyString());
    }

    @Test
    @DisplayName("DOIT déterminer doublon via cache local (fast path) dans isDuplicate")
    void devraitDetecterDoublonViaCacheLocal() {
        when(localCache.contains(anyString())).thenReturn(true);

        assertThat(service.isDuplicate("texte", "batch-001")).isTrue();
        verify(store, never()).exists(anyString());
    }

    @Test
    @DisplayName("DOIT interroger Redis (slow path) quand cache local ne contient pas le texte")
    void devraitInterrogerRedisQuandCacheLocalVide() {
        when(localCache.contains(anyString())).thenReturn(false);
        when(store.exists(anyString())).thenReturn(true);

        assertThat(service.isDuplicate("texte redis", "batch-001")).isTrue();
        verify(store).exists(anyString());
    }

    @Test
    @DisplayName("DOIT retourner false si texte absent du cache local ET de Redis")
    void devraitRetournerFalseSiAbsentPartout() {
        when(localCache.contains(anyString())).thenReturn(false);
        when(store.exists(anyString())).thenReturn(false);

        assertThat(service.isDuplicate("nouveau", "batch")).isFalse();
    }

    @Test
    @DisplayName("DOIT vider le cache local via clearLocalCache()")
    void devraitViderCacheLocalViaClearLocalCache() {
        service.clearLocalCache();
        verify(localCache).clear();
    }
}
