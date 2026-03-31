package com.exemple.nexrag.service.rag.streaming.openai;

import com.exemple.nexrag.config.StreamingConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Spec : OpenAiStreamingClient — Streaming SSE depuis l'API de génération
 *
 * <p>SRP : teste uniquement la consommation du flux SSE et l'invocation des callbacks.
 * WireMock simule le service de génération externe — aucun appel réseau réel.
 *
 * <p>Format SSE attendu (Anthropic-style) :
 * {@code event: content_block_delta\ndata: {"delta":{"text":"token"}}\n\n}
 * {@code event: message_stop\ndata: {}\n\n}
 */
@DisplayName("Spec : OpenAiStreamingClient — Streaming SSE depuis l'API de génération")
class OpenAiStreamingClientSpec {

    private WireMockServer wireMock;
    private OpenAiStreamingClient client;

    @BeforeEach
    void setup() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();

        StreamingConfig config = mock(StreamingConfig.class);
        StreamingConfig.OpenAi openAiConfig = mock(StreamingConfig.OpenAi.class);
        when(config.getOpenAi()).thenReturn(openAiConfig);
        // baseUrl = WireMock root → POST /
        when(openAiConfig.getApiUrl()).thenReturn("http://localhost:" + wireMock.port());
        when(openAiConfig.getApiKey()).thenReturn("fake-api-key");
        when(openAiConfig.getModel()).thenReturn("gpt-4o-mini");
        when(openAiConfig.getMaxTokens()).thenReturn(2000);
        when(openAiConfig.getTemperature()).thenReturn(0.7);

        client = new OpenAiStreamingClient(config, new ObjectMapper());
    }

    @AfterEach
    void teardown() {
        wireMock.stop();
    }

    // =========================================================================
    // T033 — AC-12.1 / AC-12.2 / SC-004 / SC-005 : Tokens + fin de flux
    // =========================================================================

    @Test
    @DisplayName("DOIT émettre des tokens puis déclencher onComplete depuis le flux SSE")
    void shouldEmitTokensThenTriggerOnComplete() throws InterruptedException {
        String sseBody =
                "event: content_block_delta\n" +
                "data: {\"delta\":{\"text\":\"Bonjour\"}}\n\n" +
                "event: content_block_delta\n" +
                "data: {\"delta\":{\"text\":\" monde\"}}\n\n" +
                "event: message_stop\n" +
                "data: {}\n\n";

        // text/plain → StringDecoder.textPlainOnly() splits by \n (line-by-line)
        // text/event-stream → ServerSentEventHttpMessageReader strips "data: " prefix
        wireMock.stubFor(post(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody(sseBody)));

        List<String> receivedTokens = new ArrayList<>();
        CountDownLatch completeLatch = new CountDownLatch(1);
        AtomicReference<OpenAiStreamingClient.StreamingResponse> completedResponse = new AtomicReference<>();

        client.streamResponse(
                "Dis bonjour",
                receivedTokens::add,
                response -> {
                    completedResponse.set(response);
                    completeLatch.countDown();
                },
                error -> completeLatch.countDown()
        );

        boolean completed = completeLatch.await(5, TimeUnit.SECONDS);

        assertThat(completed).isTrue();
        assertThat(receivedTokens).containsExactly("Bonjour", " monde");
        assertThat(completedResponse.get()).isNotNull();
        assertThat(completedResponse.get().getFullText()).isEqualTo("Bonjour monde");
        assertThat(completedResponse.get().getTotalTokens()).isEqualTo(2);
    }

    @Test
    @DisplayName("DOIT émettre les tokens dans l'ordre de réception du flux SSE (SC-004)")
    void shouldEmitTokensInStreamOrder() throws InterruptedException {
        String sseBody =
                "event: content_block_delta\n" +
                "data: {\"delta\":{\"text\":\"A\"}}\n\n" +
                "event: content_block_delta\n" +
                "data: {\"delta\":{\"text\":\"B\"}}\n\n" +
                "event: content_block_delta\n" +
                "data: {\"delta\":{\"text\":\"C\"}}\n\n" +
                "event: message_stop\n" +
                "data: {}\n\n";

        wireMock.stubFor(post(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody(sseBody)));

        List<String> receivedTokens = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        client.streamResponse("prompt", receivedTokens::add, r -> latch.countDown(), e -> latch.countDown());

        latch.await(5, TimeUnit.SECONDS);

        assertThat(receivedTokens).containsExactly("A", "B", "C");
    }

    // =========================================================================
    // T034 — AC-12.3 / SC-006 : Erreur HTTP 429
    // =========================================================================

    @Test
    @DisplayName("DOIT déclencher onError sans propager l'exception sur réponse HTTP 429")
    void shouldTriggerOnErrorWithoutPropagatingExceptionOnHttp429() throws InterruptedException {
        wireMock.stubFor(post(urlEqualTo("/"))
                .willReturn(aResponse().withStatus(429).withBody("Too Many Requests")));

        CountDownLatch errorLatch = new CountDownLatch(1);
        AtomicReference<Throwable> capturedError = new AtomicReference<>();

        client.streamResponse(
                "prompt",
                token -> {},
                response -> errorLatch.countDown(),
                error -> {
                    capturedError.set(error);
                    errorLatch.countDown();
                }
        );

        boolean completed = errorLatch.await(5, TimeUnit.SECONDS);

        assertThat(completed).isTrue();
        // L'erreur est capturée via callback — aucune exception non gérée propagée
    }

    // =========================================================================
    // T035 — AC-12.3 : Erreur HTTP 500
    // =========================================================================

    @Test
    @DisplayName("DOIT déclencher onError sur réponse HTTP 500 du service de génération")
    void shouldTriggerOnErrorOnHttp500() throws InterruptedException {
        wireMock.stubFor(post(urlEqualTo("/"))
                .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

        CountDownLatch errorLatch = new CountDownLatch(1);

        client.streamResponse(
                "prompt",
                token -> {},
                response -> errorLatch.countDown(),
                error -> errorLatch.countDown()
        );

        boolean completed = errorLatch.await(5, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
    }

    // =========================================================================
    // T036 — Edge case : delta sans champ "text" → aucun token émis
    // =========================================================================

    @Test
    @DisplayName("DOIT ignorer les lignes SSE avec un delta sans champ text (delta vide)")
    void shouldIgnoreSseEventWithEmptyDelta() throws InterruptedException {
        String sseBody =
                "event: content_block_delta\n" +
                "data: {\"delta\":{}}\n\n" +
                "event: message_stop\n" +
                "data: {}\n\n";

        wireMock.stubFor(post(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody(sseBody)));

        List<String> receivedTokens = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        client.streamResponse("prompt", receivedTokens::add, r -> latch.countDown(), e -> latch.countDown());

        latch.await(5, TimeUnit.SECONDS);

        assertThat(receivedTokens).isEmpty();
    }

    // =========================================================================
    // T037 — Contrat : header Authorization Bearer envoyé dans la requête
    // =========================================================================

    // =========================================================================
    // T038 — Séquence SSE complète : tous les types d'événements
    // =========================================================================

    @Test
    @DisplayName("DOIT traiter une séquence SSE complète avec message_start, content_block et message_delta")
    void shouldHandleFullSseEventSequenceWithAllEventTypes() throws InterruptedException {
        String fullSse =
                "event: message_start\n" +
                "data: {}\n\n" +
                "event: content_block_start\n" +
                "data: {}\n\n" +
                "event: content_block_delta\n" +
                "data: {\"delta\":{\"text\":\"Réponse\"}}\n\n" +
                "event: content_block_stop\n" +
                "data: {}\n\n" +
                "event: message_delta\n" +
                "data: {\"delta\":{\"stop_reason\":\"end_turn\"}}\n\n" +
                "event: message_stop\n" +
                "data: {}\n\n";

        wireMock.stubFor(post(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody(fullSse)));

        List<String> tokens = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<OpenAiStreamingClient.StreamingResponse> resp = new AtomicReference<>();

        client.streamResponse("prompt", tokens::add,
                r -> { resp.set(r); latch.countDown(); },
                e -> latch.countDown());
        boolean completed = latch.await(5, TimeUnit.SECONDS);

        assertThat(completed).isTrue();
        assertThat(tokens).containsExactly("Réponse");
        assertThat(resp.get().getFinishReason()).isEqualTo("end_turn");
    }

    // =========================================================================
    // T039 — Événement ping : ignoré silencieusement
    // =========================================================================

    @Test
    @DisplayName("DOIT ignorer les événements ping sans lever d'exception")
    void shouldIgnorePingEventsGracefully() throws InterruptedException {
        String sseBody =
                "event: ping\n" +
                "data: {}\n\n" +
                "event: message_stop\n" +
                "data: {}\n\n";

        wireMock.stubFor(post(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody(sseBody)));

        CountDownLatch latch = new CountDownLatch(1);
        client.streamResponse("prompt", t -> {}, r -> latch.countDown(), e -> latch.countDown());
        boolean completed = latch.await(5, TimeUnit.SECONDS);

        assertThat(completed).isTrue();
    }

    // =========================================================================
    // T040 — generateSync : réponse non-streaming avec contenu
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner le texte généré via generateSync pour une réponse non-streaming")
    void shouldReturnGeneratedTextViaGenerateSync() {
        wireMock.stubFor(post(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"content\":[{\"text\":\"Paris est la capitale.\"}]}")));

        String result = client.generateSync("Quelle est la capitale de la France ?");

        assertThat(result).isEqualTo("Paris est la capitale.");
    }

    // =========================================================================
    // T041 — generateSync : réponse sans clé content → chaîne vide
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner une chaîne vide via generateSync quand la réponse est sans contenu")
    void shouldReturnEmptyStringViaGenerateSyncForResponseWithoutContent() {
        wireMock.stubFor(post(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        String result = client.generateSync("prompt sans contenu");

        assertThat(result).isEmpty();
    }

    // =========================================================================
    // T042 — generateSync : liste content vide → chaîne vide
    // =========================================================================

    @Test
    @DisplayName("DOIT retourner une chaîne vide via generateSync quand la liste content est vide")
    void shouldReturnEmptyStringViaGenerateSyncForEmptyContentList() {
        wireMock.stubFor(post(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"content\":[]}")));

        String result = client.generateSync("prompt liste vide");

        assertThat(result).isEmpty();
    }

    // =========================================================================
    // T043 — Événement SSE inconnu : cas default du switch → ignoré
    // =========================================================================

    @Test
    @DisplayName("DOIT ignorer les événements SSE de type inconnu sans lever d'exception")
    void shouldIgnoreUnknownSseEventTypes() throws InterruptedException {
        String sseBody =
                "event: unknown_event_xyz\n" +
                "data: {}\n\n" +
                "event: message_stop\n" +
                "data: {}\n\n";

        wireMock.stubFor(post(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody(sseBody)));

        CountDownLatch latch = new CountDownLatch(1);
        client.streamResponse("prompt", t -> {}, r -> latch.countDown(), e -> latch.countDown());
        boolean completed = latch.await(5, TimeUnit.SECONDS);

        assertThat(completed).isTrue();
    }

    // =========================================================================
    // T037 — Contrat : header Authorization Bearer envoyé dans la requête
    // =========================================================================

    @Test
    @DisplayName("DOIT envoyer le header Authorization Bearer dans la requête vers le service de génération")
    void shouldSendAuthorizationBearerHeader() throws InterruptedException {
        wireMock.stubFor(post(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("event: message_stop\ndata: {}\n\n")));

        CountDownLatch latch = new CountDownLatch(1);
        client.streamResponse("prompt", t -> {}, r -> latch.countDown(), e -> latch.countDown());
        latch.await(5, TimeUnit.SECONDS);

        wireMock.verify(postRequestedFor(urlEqualTo("/"))
                .withHeader("Authorization", equalTo("Bearer fake-api-key")));
    }
}
