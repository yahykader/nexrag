package com.exemple.nexrag.service.rag.ingestion.security;

import com.exemple.nexrag.dto.ScanResult;
import com.exemple.nexrag.dto.ScanStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("Spec : ClamAvResponseParser — Parsing des réponses ClamAV")
class ClamAvResponseParserSpec {

    private final ClamAvResponseParser parser = new ClamAvResponseParser();

    @Test
    @DisplayName("DOIT retourner CLEAN pour une réponse contenant 'OK'")
    void devraitRetournerCleanPourReponseOk() {
        ScanResult result = parser.parse("document.pdf", "stream: OK");
        assertThat(result.isClean()).isTrue();
        assertThat(result.getStatus()).isEqualTo(ScanStatus.CLEAN);
    }

    @Test
    @DisplayName("DOIT conserver le nom du fichier dans le résultat CLEAN")
    void devraitConserverNomFichierDansResultatClean() {
        ScanResult result = parser.parse("rapport.docx", "stream: OK");
        assertThat(result.getFilename()).isEqualTo("rapport.docx");
    }

    @Test
    @DisplayName("DOIT retourner INFECTED avec le nom du virus pour une réponse FOUND")
    void devraitRetournerInfectedAvecNomVirus() {
        ScanResult result = parser.parse("malware.exe", "stream: Eicar-Test-Signature FOUND");
        assertThat(result.isInfected()).isTrue();
        assertThat(result.getStatus()).isEqualTo(ScanStatus.INFECTED);
        assertThat(result.getVirusName()).isEqualTo("Eicar-Test-Signature");
    }

    @Test
    @DisplayName("DOIT retourner ERROR pour une réponse contenant 'ERROR'")
    void devraitRetournerErrorPourReponseErreur() {
        ScanResult result = parser.parse("fichier.zip", "stream: ERROR - scan failure");
        assertThat(result.hasError()).isTrue();
        assertThat(result.getStatus()).isEqualTo(ScanStatus.ERROR);
    }

    @Test
    @DisplayName("DOIT retourner ERROR pour une réponse au format inconnu")
    void devraitRetournerErrorPourReponseInconnue() {
        ScanResult result = parser.parse("test.bin", "réponse inattendue du daemon");
        assertThat(result.hasError()).isTrue();
        assertThat(result.getStatus()).isEqualTo(ScanStatus.ERROR);
    }

    @Test
    @DisplayName("DOIT être sans état : appels successifs avec mêmes args donnent même résultat")
    void devraitEtreSansEtat() {
        ScanResult r1 = parser.parse("f.pdf", "stream: OK");
        ScanResult r2 = parser.parse("f.pdf", "stream: OK");
        assertThat(r1.isClean()).isEqualTo(r2.isClean());
        assertThat(r1.getStatus()).isEqualTo(r2.getStatus());
    }

    @Test
    @DisplayName("DOIT ne pas lever d'exception pour une réponse vide")
    void devraitNePasLeverExceptionPourReponseVide() {
        ScanResult result = parser.parse("file.pdf", "");
        assertThat(result).isNotNull();
        assertThat(result.hasError()).isTrue();
    }
}
