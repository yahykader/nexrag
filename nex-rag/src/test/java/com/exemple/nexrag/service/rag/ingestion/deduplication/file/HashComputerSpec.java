package com.exemple.nexrag.service.rag.ingestion.deduplication.file;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("Spec : HashComputer — Hash SHA-256 déterministe (encodage Base64)")
class HashComputerSpec {

    private final HashComputer computer = new HashComputer();

    @Test
    @DisplayName("DOIT produire le même hash pour deux tableaux de bytes identiques")
    void devraitProduireMemeHashPourContenuIdentique() {
        byte[] a = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] b = "hello".getBytes(StandardCharsets.UTF_8);
        assertThat(computer.compute(a)).isEqualTo(computer.compute(b));
    }

    @Test
    @DisplayName("DOIT produire des hashs différents pour des contenus distincts")
    void devraitProduireHashsDifferentsPourContenusDistincts() {
        String hashA = computer.compute("contenu A".getBytes(StandardCharsets.UTF_8));
        String hashB = computer.compute("contenu B".getBytes(StandardCharsets.UTF_8));
        assertThat(hashA).isNotEqualTo(hashB);
    }

    @Test
    @DisplayName("DOIT retourner une chaîne Base64 valide (décodable sans erreur)")
    void devraitRetournerBase64Valide() {
        String hash = computer.compute("test".getBytes(StandardCharsets.UTF_8));
        assertThat(hash).isNotNull().isNotEmpty();
        // Vérifier que c'est du Base64 valide
        byte[] decoded = Base64.getDecoder().decode(hash);
        assertThat(decoded).hasSize(32); // SHA-256 = 32 bytes
    }

    @Test
    @DisplayName("DOIT retourner le hash SHA-256 Base64 connu pour un tableau vide")
    void devraitRetournerHashConnutPourTableauVide() {
        // SHA-256("") en Base64 = 47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=
        String hash = computer.compute(new byte[0]);
        assertThat(hash).isEqualTo("47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=");
    }

    @Test
    @DisplayName("DOIT être idempotent : même entrée → même résultat à chaque appel")
    void devraitEtreIdempotent() {
        byte[] content = "idempotence".getBytes(StandardCharsets.UTF_8);
        String h1 = computer.compute(content);
        String h2 = computer.compute(content);
        String h3 = computer.compute(content);
        assertThat(h1).isEqualTo(h2).isEqualTo(h3);
    }

    @Test
    @DisplayName("DOIT retourner un shortHash terminant par '...' avec 16 premiers caractères")
    void devraitRetournerShortHashPourLogs() {
        String full = computer.compute("quelquechose".getBytes(StandardCharsets.UTF_8));
        String short_ = computer.toShortHash(full);
        assertThat(short_).endsWith("...");
        assertThat(short_).hasSize(16 + 3);
        assertThat(full).startsWith(short_.replace("...", ""));
    }

    @Test
    @DisplayName("DOIT retourner null via toShortHash si le hash fourni est null")
    void devraitRetournerNullPourToShortHashNull() {
        assertThat(computer.toShortHash(null)).isNull();
    }
}
