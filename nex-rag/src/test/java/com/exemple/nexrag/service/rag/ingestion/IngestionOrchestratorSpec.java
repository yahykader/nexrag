package com.exemple.nexrag.service.rag.ingestion;

import com.exemple.nexrag.config.ClamAvProperties;
import com.exemple.nexrag.dto.DetailedHealthResponse;
import com.exemple.nexrag.dto.IngestionResult;
import com.exemple.nexrag.dto.StatsResponse;
import com.exemple.nexrag.exception.DuplicateFileException;
import com.exemple.nexrag.exception.IngestionException;
import com.exemple.nexrag.service.rag.ingestion.deduplication.file.DeduplicationService;
import com.exemple.nexrag.service.rag.ingestion.repository.EmbeddingRepository;
import com.exemple.nexrag.service.rag.ingestion.security.AntivirusGuard;
import com.exemple.nexrag.service.rag.ingestion.security.AntivirusScanner;
import com.exemple.nexrag.service.rag.ingestion.strategy.IngestionStrategy;
import com.exemple.nexrag.service.rag.ingestion.tracker.IngestionTracker;
import com.exemple.nexrag.service.rag.metrics.RAGMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Spec : IngestionOrchestrator — Pipeline antivirus → stratégie → dédup → ingestion → métriques.
 */
@DisplayName("Spec : IngestionOrchestrator — Orchestration antivirus/dédup/ingestion/rollback")
@ExtendWith(MockitoExtension.class)
class IngestionOrchestratorSpec {

    @Mock private IngestionStrategy   strategy;
    @Mock private RAGMetrics          ragMetrics;
    @Mock private IngestionTracker    tracker;
    @Mock private DeduplicationService deduplicationService;
    @Mock private AntivirusGuard      antivirusGuard;
    @Mock private AntivirusScanner    antivirusScanner;
    @Mock private EmbeddingRepository embeddingRepository;
    @Mock private ClamAvProperties    antivirusProps;
    @Mock private MultipartFile       file;

    private IngestionOrchestrator orchestrator;

    private static final String BATCH_ID  = "batch-001";
    private static final String FILENAME  = "document.pdf";
    private static final String FILE_HASH = "sha256-abc123";

    @BeforeEach
    void setUp() {
        // Injection manuelle : List<IngestionStrategy> non supporté par @InjectMocks
        orchestrator = new IngestionOrchestrator(
            List.of(strategy),
            ragMetrics,
            tracker,
            deduplicationService,
            antivirusGuard,
            antivirusScanner,
            embeddingRepository,
            antivirusProps
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void stubFichierValide(IngestionResult resultat) throws Exception {
        when(file.getOriginalFilename()).thenReturn(FILENAME);
        when(file.getSize()).thenReturn(2048L);
        when(file.getBytes()).thenReturn("contenu-test".getBytes());
        when(antivirusProps.isEnabled()).thenReturn(false);
        when(strategy.canHandle(eq(file), anyString())).thenReturn(true);
        when(strategy.getName()).thenReturn("PDF");
        when(strategy.getPriority()).thenReturn(1);
        when(deduplicationService.computeHash(any(byte[].class))).thenReturn(FILE_HASH);
        when(deduplicationService.isDuplicateAndRecord(FILE_HASH, "PDF")).thenReturn(false);
        when(strategy.ingest(file, BATCH_ID)).thenReturn(resultat);
    }

    // -------------------------------------------------------------------------
    // Happy path — séquence complète
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner le résultat de la stratégie pour un fichier valide non dupliqué")
    void shouldReturnStrategyResultForValidNonDuplicateFile() throws Exception {
        IngestionResult attendu = new IngestionResult(5, 2);
        stubFichierValide(attendu);

        IngestionResult result = orchestrator.ingestFile(file, BATCH_ID);

        assertThat(result.textEmbeddings()).isEqualTo(5);
        assertThat(result.imageEmbeddings()).isEqualTo(2);
    }

    @Test
    @DisplayName("DOIT appeler strategy.ingest() après la vérification de déduplication (ordre InOrder)")
    void shouldCallStrategyIngestAfterDeduplicationCheck() throws Exception {
        stubFichierValide(new IngestionResult(3, 0));
        InOrder ordre = inOrder(deduplicationService, strategy, ragMetrics);

        orchestrator.ingestFile(file, BATCH_ID);

        ordre.verify(deduplicationService).computeHash(any(byte[].class));
        ordre.verify(deduplicationService).isDuplicateAndRecord(FILE_HASH, "PDF");
        ordre.verify(strategy).ingest(file, BATCH_ID);
        ordre.verify(ragMetrics).recordIngestionSuccess(anyString(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("DOIT appeler ragMetrics.startIngestion() et endIngestion() pour chaque fichier traité")
    void shouldCallStartAndEndIngestionMetrics() throws Exception {
        stubFichierValide(new IngestionResult(1, 0));

        orchestrator.ingestFile(file, BATCH_ID);

        verify(ragMetrics).startIngestion();
        verify(ragMetrics).endIngestion();
    }

    // -------------------------------------------------------------------------
    // Antivirus activé
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT appeler antivirusGuard.assertClean() quand l'antivirus est activé")
    void shouldCallAntivirusGuardWhenEnabled() throws Exception {
        when(file.getOriginalFilename()).thenReturn(FILENAME);
        when(file.getSize()).thenReturn(1024L);
        when(file.getBytes()).thenReturn("contenu".getBytes());
        when(antivirusProps.isEnabled()).thenReturn(true);  // antivirus ON
        when(strategy.canHandle(eq(file), anyString())).thenReturn(true);
        when(strategy.getName()).thenReturn("PDF");
        when(strategy.getPriority()).thenReturn(1);
        when(deduplicationService.computeHash(any(byte[].class))).thenReturn(FILE_HASH);
        when(deduplicationService.isDuplicateAndRecord(FILE_HASH, "PDF")).thenReturn(false);
        when(strategy.ingest(file, BATCH_ID)).thenReturn(new IngestionResult(1, 0));

        orchestrator.ingestFile(file, BATCH_ID);

        verify(antivirusGuard).assertClean(file);
    }

    @Test
    @DisplayName("DOIT NE PAS appeler antivirusGuard.assertClean() quand l'antivirus est désactivé")
    void shouldNotCallAntivirusGuardWhenDisabled() throws Exception {
        stubFichierValide(new IngestionResult(1, 0));

        orchestrator.ingestFile(file, BATCH_ID);

        verify(antivirusGuard, never()).assertClean(any());
    }

    // -------------------------------------------------------------------------
    // Exception dans strategy.ingest() → rollbackSafely() appelé
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT appeler rollbackBatch() quand strategy.ingest() lève une exception")
    void shouldCallRollbackBatchWhenStrategyIngestThrows() throws Exception {
        when(file.getOriginalFilename()).thenReturn(FILENAME);
        when(file.getSize()).thenReturn(1024L);
        when(file.getBytes()).thenReturn("contenu".getBytes());
        when(antivirusProps.isEnabled()).thenReturn(false);
        when(strategy.canHandle(eq(file), anyString())).thenReturn(true);
        when(strategy.getName()).thenReturn("PDF");
        when(strategy.getPriority()).thenReturn(1);
        when(deduplicationService.computeHash(any(byte[].class))).thenReturn(FILE_HASH);
        when(deduplicationService.isDuplicateAndRecord(FILE_HASH, "PDF")).thenReturn(false);
        when(strategy.ingest(file, BATCH_ID)).thenThrow(new IngestionException("PDF corrompu"));

        assertThatThrownBy(() -> orchestrator.ingestFile(file, BATCH_ID))
            .isInstanceOf(IngestionException.class);

        verify(tracker).rollbackBatch(BATCH_ID);
    }

    @Test
    @DisplayName("DOIT re-propager l'exception après rollback quand strategy.ingest() échoue")
    void shouldRethrowExceptionAfterRollbackOnIngestionFailure() throws Exception {
        when(file.getOriginalFilename()).thenReturn(FILENAME);
        when(file.getSize()).thenReturn(1024L);
        when(file.getBytes()).thenReturn("contenu".getBytes());
        when(antivirusProps.isEnabled()).thenReturn(false);
        when(strategy.canHandle(eq(file), anyString())).thenReturn(true);
        when(strategy.getName()).thenReturn("PDF");
        when(strategy.getPriority()).thenReturn(1);
        when(deduplicationService.computeHash(any(byte[].class))).thenReturn(FILE_HASH);
        when(deduplicationService.isDuplicateAndRecord(FILE_HASH, "PDF")).thenReturn(false);
        RuntimeException causeOriginale = new RuntimeException("erreur inattendue");
        when(strategy.ingest(file, BATCH_ID)).thenThrow(causeOriginale);

        assertThatThrownBy(() -> orchestrator.ingestFile(file, BATCH_ID))
            .isSameAs(causeOriginale);
    }

    // -------------------------------------------------------------------------
    // DuplicateFileException → PAS de rollback
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT NE PAS appeler rollbackBatch() pour une DuplicateFileException")
    void shouldNotCallRollbackBatchForDuplicateFileException() throws Exception {
        when(file.getOriginalFilename()).thenReturn(FILENAME);
        when(file.getSize()).thenReturn(1024L);
        when(file.getBytes()).thenReturn("contenu".getBytes());
        when(antivirusProps.isEnabled()).thenReturn(false);
        when(strategy.canHandle(eq(file), anyString())).thenReturn(true);
        when(strategy.getName()).thenReturn("PDF");
        when(strategy.getPriority()).thenReturn(1);
        when(deduplicationService.computeHash(any(byte[].class))).thenReturn(FILE_HASH);
        when(deduplicationService.isDuplicateAndRecord(FILE_HASH, "PDF")).thenReturn(true);
        when(deduplicationService.getExistingBatchId(FILE_HASH)).thenReturn("batch-existant");

        assertThatThrownBy(() -> orchestrator.ingestFile(file, BATCH_ID))
            .isInstanceOf(DuplicateFileException.class);

        verify(tracker, never()).rollbackBatch(anyString());
    }

    @Test
    @DisplayName("DOIT lever DuplicateFileException avec le batchId existant pour un doublon")
    void shouldThrowDuplicateFileExceptionWithExistingBatchId() throws Exception {
        when(file.getOriginalFilename()).thenReturn(FILENAME);
        when(file.getSize()).thenReturn(1024L);
        when(file.getBytes()).thenReturn("contenu".getBytes());
        when(antivirusProps.isEnabled()).thenReturn(false);
        when(strategy.canHandle(eq(file), anyString())).thenReturn(true);
        when(strategy.getName()).thenReturn("PDF");
        when(strategy.getPriority()).thenReturn(1);
        when(deduplicationService.computeHash(any(byte[].class))).thenReturn(FILE_HASH);
        when(deduplicationService.isDuplicateAndRecord(FILE_HASH, "PDF")).thenReturn(true);
        when(deduplicationService.getExistingBatchId(FILE_HASH)).thenReturn("batch-existant");

        assertThatThrownBy(() -> orchestrator.ingestFile(file, BATCH_ID))
            .isInstanceOf(DuplicateFileException.class)
            .hasMessageContaining(FILENAME);
    }

    // -------------------------------------------------------------------------
    // OCP — stratégie supplémentaire n'impacte pas les tests existants
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("OCP : ajouter une stratégie supplémentaire ne casse pas la sélection de la stratégie PDF")
    void shouldSelectCorrectStrategyWhenMultipleStrategiesPresent() throws Exception {
        IngestionStrategy strategieSupplementaire = mock(IngestionStrategy.class);
        // La stratégie supplémentaire ne gère pas les PDF
        when(strategieSupplementaire.canHandle(eq(file), anyString())).thenReturn(false);

        IngestionOrchestrator orchestrateurEtendu = new IngestionOrchestrator(
            List.of(strategieSupplementaire, strategy),  // stratégie PDF en second
            ragMetrics, tracker, deduplicationService,
            antivirusGuard, antivirusScanner, embeddingRepository, antivirusProps
        );

        when(file.getOriginalFilename()).thenReturn(FILENAME);
        when(file.getSize()).thenReturn(1024L);
        when(file.getBytes()).thenReturn("contenu".getBytes());
        when(antivirusProps.isEnabled()).thenReturn(false);
        when(strategy.canHandle(eq(file), anyString())).thenReturn(true);
        when(strategy.getName()).thenReturn("PDF");
        when(strategy.getPriority()).thenReturn(1);
        when(deduplicationService.computeHash(any(byte[].class))).thenReturn(FILE_HASH);
        when(deduplicationService.isDuplicateAndRecord(FILE_HASH, "PDF")).thenReturn(false);
        when(strategy.ingest(file, BATCH_ID)).thenReturn(new IngestionResult(2, 0));

        IngestionResult result = orchestrateurEtendu.ingestFile(file, BATCH_ID);

        assertThat(result.textEmbeddings()).isEqualTo(2);
        verify(strategy).ingest(file, BATCH_ID);
        verify(strategieSupplementaire, never()).ingest(any(), anyString());
    }

    // -------------------------------------------------------------------------
    // Aucune stratégie disponible
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT lever UnsupportedOperationException quand aucune stratégie ne peut traiter le fichier")
    void shouldThrowUnsupportedOperationExceptionWhenNoStrategyMatches() throws Exception {
        when(file.getOriginalFilename()).thenReturn("fichier.xyz");
        when(file.getSize()).thenReturn(512L);
        when(antivirusProps.isEnabled()).thenReturn(false);
        when(strategy.canHandle(eq(file), anyString())).thenReturn(false);  // aucune stratégie

        assertThatThrownBy(() -> orchestrator.ingestFile(file, BATCH_ID))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("xyz");
    }

    // -------------------------------------------------------------------------
    // Monitoring — getStats, getHealthReport, getAvailableStrategies
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner les stats avec le nombre de stratégies")
    void shouldReturnStatsWithStrategyCount() {
        when(tracker.getBatchCount()).thenReturn(2);
        when(tracker.getTotalEmbeddings()).thenReturn(10);
        when(ragMetrics.getActiveIngestions()).thenReturn(0);

        StatsResponse stats = orchestrator.getStats();

        assertThat(stats.getStrategiesCount()).isEqualTo(1);
        assertThat(stats.getTrackerBatches()).isEqualTo(2);
        assertThat(stats.getTrackerEmbeddings()).isEqualTo(10);
    }

    @Test
    @DisplayName("DOIT retourner HEALTHY quand Redis et antivirus sont opérationnels")
    void shouldReturnHealthyWhenRedisAndAntivirusAreHealthy() {
        when(deduplicationService.isHealthy()).thenReturn(true);
        when(antivirusProps.isEnabled()).thenReturn(false);
        when(tracker.getBatchCount()).thenReturn(0);
        when(tracker.getTotalEmbeddings()).thenReturn(0);
        when(ragMetrics.getActiveIngestions()).thenReturn(0);

        DetailedHealthResponse health = orchestrator.getHealthReport();

        assertThat(health.getStatus()).isEqualTo("HEALTHY");
        assertThat(health.isRedisHealthy()).isTrue();
    }

    @Test
    @DisplayName("DOIT retourner DEGRADED quand Redis est indisponible")
    void shouldReturnDegradedWhenRedisIsUnhealthy() {
        when(deduplicationService.isHealthy()).thenReturn(false);
        when(antivirusProps.isEnabled()).thenReturn(false);
        when(tracker.getBatchCount()).thenReturn(0);
        when(tracker.getTotalEmbeddings()).thenReturn(0);
        when(ragMetrics.getActiveIngestions()).thenReturn(0);

        DetailedHealthResponse health = orchestrator.getHealthReport();

        assertThat(health.getStatus()).isEqualTo("DEGRADED");
    }

    @Test
    @DisplayName("DOIT retourner les noms de stratégies disponibles")
    void shouldReturnAvailableStrategyNames() {
        when(strategy.getName()).thenReturn("PDF");

        List<String> strategies = orchestrator.getAvailableStrategies();

        assertThat(strategies).containsExactly("PDF");
    }

    @Test
    @DisplayName("DOIT retourner les infos détaillées de stratégies")
    void shouldReturnStrategiesInfo() {
        when(strategy.getName()).thenReturn("PDF");
        when(strategy.getPriority()).thenReturn(1);

        var infos = orchestrator.getStrategiesInfo();

        assertThat(infos).hasSize(1);
        assertThat(infos.get(0).getName()).isEqualTo("PDF");
        assertThat(infos.get(0).getPriority()).isEqualTo(1);
    }

    @Test
    @DisplayName("DOIT retourner une liste vide pour getActiveIngestions() si aucune ingestion en cours")
    void shouldReturnEmptyListForGetActiveIngestionsWhenNoneActive() {
        assertThat(orchestrator.getActiveIngestions()).isEmpty();
    }

    @Test
    @DisplayName("DOIT retourner Optional.empty() pour getIngestionStatus() sur batch inconnu")
    void shouldReturnEmptyOptionalForUnknownBatchId() {
        assertThat(orchestrator.getIngestionStatus("inconnu")).isEqualTo(Optional.empty());
    }

    // -------------------------------------------------------------------------
    // fileExists / getExistingBatchId
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner true pour fileExists() quand le fichier est un doublon")
    void shouldReturnTrueForFileExistsWhenFileDuplicate() throws Exception {
        when(file.getBytes()).thenReturn("contenu".getBytes());
        when(deduplicationService.computeHash(any(byte[].class))).thenReturn(FILE_HASH);
        when(deduplicationService.isDuplicateByHash(FILE_HASH)).thenReturn(true);

        assertThat(orchestrator.fileExists(file)).isTrue();
    }

    @Test
    @DisplayName("DOIT retourner false pour fileExists() quand le fichier n'existe pas")
    void shouldReturnFalseForFileExistsWhenFileNew() throws Exception {
        when(file.getBytes()).thenReturn("contenu".getBytes());
        when(deduplicationService.computeHash(any(byte[].class))).thenReturn(FILE_HASH);
        when(deduplicationService.isDuplicateByHash(FILE_HASH)).thenReturn(false);

        assertThat(orchestrator.fileExists(file)).isFalse();
    }

    @Test
    @DisplayName("DOIT retourner false pour fileExists() en cas d'exception")
    void shouldReturnFalseForFileExistsOnException() throws Exception {
        when(file.getBytes()).thenThrow(new java.io.IOException("erreur IO"));
        when(file.getOriginalFilename()).thenReturn(FILENAME);

        assertThat(orchestrator.fileExists(file)).isFalse();
    }

    @Test
    @DisplayName("DOIT retourner le batchId existant pour getExistingBatchId() quand le fichier est un doublon")
    void shouldReturnExistingBatchIdWhenFileDuplicate() throws Exception {
        when(file.getBytes()).thenReturn("contenu".getBytes());
        when(deduplicationService.computeHash(any(byte[].class))).thenReturn(FILE_HASH);
        when(deduplicationService.isDuplicateByHash(FILE_HASH)).thenReturn(true);
        when(deduplicationService.getExistingBatchId(FILE_HASH)).thenReturn("batch-existant");

        assertThat(orchestrator.getExistingBatchId(file)).isEqualTo("batch-existant");
    }

    @Test
    @DisplayName("DOIT retourner null pour getExistingBatchId() quand le fichier n'existe pas")
    void shouldReturnNullForGetExistingBatchIdWhenFileNew() throws Exception {
        when(file.getBytes()).thenReturn("contenu".getBytes());
        when(deduplicationService.computeHash(any(byte[].class))).thenReturn(FILE_HASH);
        when(deduplicationService.isDuplicateByHash(FILE_HASH)).thenReturn(false);

        assertThat(orchestrator.getExistingBatchId(file)).isNull();
    }

    @Test
    @DisplayName("DOIT retourner null pour getExistingBatchId() en cas d'exception")
    void shouldReturnNullForGetExistingBatchIdOnException() throws Exception {
        when(file.getBytes()).thenThrow(new java.io.IOException("erreur IO"));
        when(file.getOriginalFilename()).thenReturn(FILENAME);

        assertThat(orchestrator.getExistingBatchId(file)).isNull();
    }

    // -------------------------------------------------------------------------
    // rollbackSafely — exception dans rollbackBatch() interceptée
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT intercepter l'exception si rollbackBatch() échoue (rollbackSafely best-effort)")
    void shouldInterceptExceptionIfRollbackBatchFails() throws Exception {
        when(file.getOriginalFilename()).thenReturn(FILENAME);
        when(file.getSize()).thenReturn(1024L);
        when(file.getBytes()).thenReturn("contenu".getBytes());
        when(antivirusProps.isEnabled()).thenReturn(false);
        when(strategy.canHandle(eq(file), anyString())).thenReturn(true);
        when(strategy.getName()).thenReturn("PDF");
        when(strategy.getPriority()).thenReturn(1);
        when(deduplicationService.computeHash(any(byte[].class))).thenReturn(FILE_HASH);
        when(deduplicationService.isDuplicateAndRecord(FILE_HASH, "PDF")).thenReturn(false);
        when(strategy.ingest(file, BATCH_ID)).thenThrow(new IngestionException("PDF corrompu"));
        // rollbackBatch throws itself
        when(tracker.rollbackBatch(BATCH_ID)).thenThrow(new RuntimeException("rollback failed"));

        // Exception must still be propagated (the original one), not swallowed
        assertThatThrownBy(() -> orchestrator.ingestFile(file, BATCH_ID))
            .isInstanceOf(IngestionException.class);
    }

    // -------------------------------------------------------------------------
    // logStats — délégation à getStats()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT appeler getStats() lors de logStats() sans lever d'exception")
    void shouldCallGetStatsInLogStatsWithoutException() {
        when(tracker.getBatchCount()).thenReturn(1);
        when(tracker.getTotalEmbeddings()).thenReturn(5);
        when(ragMetrics.getActiveIngestions()).thenReturn(0);

        orchestrator.logStats(); // must not throw
    }

    // -------------------------------------------------------------------------
    // getExtension — cas limites
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT lever UnsupportedOperationException pour un fichier sans extension")
    void shouldThrowForFileWithNoExtension() throws Exception {
        when(file.getOriginalFilename()).thenReturn("fichier_sans_extension");
        when(file.getSize()).thenReturn(512L);
        when(antivirusProps.isEnabled()).thenReturn(false);
        when(strategy.canHandle(eq(file), eq("unknown"))).thenReturn(false);

        assertThatThrownBy(() -> orchestrator.ingestFile(file, BATCH_ID))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("DOIT lever UnsupportedOperationException pour un nom de fichier null")
    void shouldThrowForNullFilename() throws Exception {
        when(file.getOriginalFilename()).thenReturn(null);
        when(file.getSize()).thenReturn(512L);
        when(antivirusProps.isEnabled()).thenReturn(false);
        when(strategy.canHandle(eq(file), eq("unknown"))).thenReturn(false);

        assertThatThrownBy(() -> orchestrator.ingestFile(file, BATCH_ID))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // -------------------------------------------------------------------------
    // API asynchrone (exécution synchrone sans Spring AOP)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner un CompletableFuture complété pour ingestFileAsync() succès")
    void shouldReturnCompletedFutureForSuccessfulIngestFileAsync() throws Exception {
        stubFichierValide(new IngestionResult(3, 1));

        var future = orchestrator.ingestFileAsync(file, BATCH_ID);

        assertThat(future).isDone();
        assertThat(future.get().textEmbeddings()).isEqualTo(3);
    }

    @Test
    @DisplayName("DOIT retourner un CompletableFuture échoué pour ingestFileAsync() en cas d'erreur")
    void shouldReturnFailedFutureForIngestFileAsyncOnError() throws Exception {
        when(file.getOriginalFilename()).thenReturn(FILENAME);
        when(file.getSize()).thenReturn(1024L);
        when(file.getBytes()).thenReturn("contenu".getBytes());
        when(antivirusProps.isEnabled()).thenReturn(false);
        when(strategy.canHandle(eq(file), anyString())).thenReturn(true);
        when(strategy.getName()).thenReturn("PDF");
        when(strategy.getPriority()).thenReturn(1);
        when(deduplicationService.computeHash(any(byte[].class))).thenReturn(FILE_HASH);
        when(deduplicationService.isDuplicateAndRecord(FILE_HASH, "PDF")).thenReturn(false);
        when(strategy.ingest(file, BATCH_ID)).thenThrow(new IngestionException("erreur"));

        var future = orchestrator.ingestFileAsync(file, BATCH_ID);

        assertThat(future).isCompletedExceptionally();
    }

    // -------------------------------------------------------------------------
    // API batch (exécution synchrone sans Spring AOP)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT traiter deux fichiers et retourner une liste de 2 résultats pour ingestBatch()")
    void shouldProcessTwoFilesAndReturn2ResultsForIngestBatch() throws Exception {
        MultipartFile file2 = mock(MultipartFile.class);
        stubFichierValide(new IngestionResult(2, 0));
        // file2 setup
        when(file2.getOriginalFilename()).thenReturn("doc2.pdf");
        when(file2.getSize()).thenReturn(1024L);
        when(file2.getBytes()).thenReturn("contenu2".getBytes());
        when(strategy.canHandle(eq(file2), anyString())).thenReturn(true);
        when(deduplicationService.computeHash("contenu2".getBytes())).thenReturn("hash-2");
        when(deduplicationService.isDuplicateAndRecord("hash-2", "PDF")).thenReturn(false);
        when(strategy.ingest(file2, BATCH_ID)).thenReturn(new IngestionResult(1, 0));

        var future = orchestrator.ingestBatch(List.of(file, file2), BATCH_ID);

        assertThat(future).isDone();
        assertThat(future.get()).hasSize(2);
    }

    @Test
    @DisplayName("DOIT ignorer les erreurs individuelles et retourner les succès pour ingestBatch()")
    void shouldIgnoreIndividualErrorsAndReturnSuccessesForIngestBatch() throws Exception {
        MultipartFile fileOk  = mock(MultipartFile.class);
        MultipartFile fileFail = mock(MultipartFile.class);

        when(fileOk.getOriginalFilename()).thenReturn("ok.pdf");
        when(fileOk.getSize()).thenReturn(512L);
        when(fileOk.getBytes()).thenReturn("ok".getBytes());
        when(fileFail.getOriginalFilename()).thenReturn("fail.xyz");
        when(fileFail.getSize()).thenReturn(512L);

        when(antivirusProps.isEnabled()).thenReturn(false);
        when(strategy.canHandle(eq(fileOk), anyString())).thenReturn(true);
        when(strategy.getName()).thenReturn("PDF");
        when(strategy.getPriority()).thenReturn(1);
        when(deduplicationService.computeHash("ok".getBytes())).thenReturn("hash-ok");
        when(deduplicationService.isDuplicateAndRecord("hash-ok", "PDF")).thenReturn(false);
        when(strategy.ingest(fileOk, BATCH_ID)).thenReturn(new IngestionResult(1, 0));
        when(strategy.canHandle(eq(fileFail), anyString())).thenReturn(false);

        var future = orchestrator.ingestBatch(List.of(fileOk, fileFail), BATCH_ID);

        assertThat(future).isDone();
        // One success, one failure (ignored) → list has 1 result
        assertThat(future.get()).hasSize(1);
    }

    // -------------------------------------------------------------------------
    // ingestBatchDetailed — processFileForBatch (succès, doublon, erreur)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DOIT retourner BatchIngestionResult avec 1 succès pour ingestBatchDetailed()")
    void shouldReturnBatchIngestionResultWith1SuccessForIngestBatchDetailed() throws Exception {
        stubFichierValide(new IngestionResult(2, 1));

        var future = orchestrator.ingestBatchDetailed(List.of(file), BATCH_ID);

        assertThat(future).isDone();
        var result = future.get();
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isZero();
    }

    @Test
    @DisplayName("DOIT capturer le doublon dans BatchIngestionResult (processFileForBatch)")
    void shouldCaptureDuplicateInBatchIngestionResult() throws Exception {
        when(file.getOriginalFilename()).thenReturn(FILENAME);
        when(file.getSize()).thenReturn(1024L);
        when(file.getBytes()).thenReturn("contenu".getBytes());
        when(antivirusProps.isEnabled()).thenReturn(false);
        when(strategy.canHandle(eq(file), anyString())).thenReturn(true);
        when(strategy.getName()).thenReturn("PDF");
        when(strategy.getPriority()).thenReturn(1);
        when(deduplicationService.computeHash(any(byte[].class))).thenReturn(FILE_HASH);
        when(deduplicationService.isDuplicateAndRecord(FILE_HASH, "PDF")).thenReturn(true);
        when(deduplicationService.getExistingBatchId(FILE_HASH)).thenReturn("batch-existant");

        var future = orchestrator.ingestBatchDetailed(List.of(file), BATCH_ID);

        assertThat(future).isDone();
        var result = future.get();
        assertThat(result.duplicateCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("DOIT capturer l'erreur dans BatchIngestionResult (processFileForBatch erreur)")
    void shouldCaptureErrorInBatchIngestionResult() throws Exception {
        when(file.getOriginalFilename()).thenReturn(FILENAME);
        when(file.getSize()).thenReturn(1024L);
        when(file.getBytes()).thenReturn("contenu".getBytes());
        when(antivirusProps.isEnabled()).thenReturn(false);
        when(strategy.canHandle(eq(file), anyString())).thenReturn(true);
        when(strategy.getName()).thenReturn("PDF");
        when(strategy.getPriority()).thenReturn(1);
        when(deduplicationService.computeHash(any(byte[].class))).thenReturn(FILE_HASH);
        when(deduplicationService.isDuplicateAndRecord(FILE_HASH, "PDF")).thenReturn(false);
        when(strategy.ingest(file, BATCH_ID)).thenThrow(new IngestionException("parse error"));

        var future = orchestrator.ingestBatchDetailed(List.of(file), BATCH_ID);

        assertThat(future).isDone();
        var result = future.get();
        assertThat(result.failureCount()).isEqualTo(1);
    }
}
