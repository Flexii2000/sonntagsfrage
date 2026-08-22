package com.fherrmann.wahlen.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Die JSON-API ist ohnehin oeffentlich (die Datengrundlage steht unter der
 * ODbL). Fuer das Statusboard auf einer anderen Subdomain braucht der Browser
 * trotzdem eine ausdrueckliche Freigabe.
 *
 * <p>Bewusst eng gefasst: nur die konfigurierten Herkuenfte, nur GET, keine
 * Credentials. Die Seiten selbst bleiben aussen vor.
 */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

    private final WahlenProperties properties;

    public WebConfig(WahlenProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        var origins = properties.cors().allowedOrigins();
        if (origins.isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(origins.toArray(String[]::new))
                .allowedMethods("GET")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
