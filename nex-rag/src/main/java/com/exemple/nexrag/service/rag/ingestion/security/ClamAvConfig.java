package com.exemple.nexrag.config;

import com.exemple.nexrag.service.rag.ingestion.security.AntivirusScanner;
import com.exemple.nexrag.service.rag.ingestion.security.ClamAvResponseParser;
import com.exemple.nexrag.service.rag.ingestion.security.ClamAvSocketClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration Spring pour le service antivirus ClamAV.
 *
 * Principe SRP : unique responsabilité → créer et câbler les beans antivirus.
 *                Le health check est délégué à {@link com.exemple.nexrag.service.rag.ingestion.security.ClamAvHealthScheduler}.
 * Clean code   : supprime l'appel direct {@code antivirusScanner()} dans le scheduler
 *                (qui pouvait sembler créer une nouvelle instance hors proxy Spring).
 *
 * @author ayahyaoui
 * @version 2.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(ClamAvProperties.class)
public class ClamAvConfig {

    private final ClamAvProperties   props;
    private final ClamAvResponseParser responseParser;

    @Bean
    public ClamAvSocketClient clamAvSocketClient() {
        return new ClamAvSocketClient(props);
    }

    /**
     * Bean principal du scanner antivirus.
     * Vérifie la disponibilité au démarrage et log le résultat.
     */
    @Bean
    public AntivirusScanner antivirusScanner(ClamAvSocketClient socketClient) {
        AntivirusScanner scanner = new AntivirusScanner(props, socketClient, responseParser);
        logStartupStatus(scanner);
        return scanner;
    }

    // -------------------------------------------------------------------------
    // Privé
    // -------------------------------------------------------------------------

    private void logStartupStatus(AntivirusScanner scanner) {
        if (!props.isEnabled()) {
            log.info("🦠 ClamAV désactivé (antivirus.enabled=false)");
            return;
        }

        if (scanner.isAvailable()) {
            log.info("✅ ClamAV connecté — version : {}", scanner.getVersion());
        } else {
            log.warn("⚠️ ClamAV activé mais indisponible ({}:{})",
                props.getHost(), props.getPort());
        }
    }
}