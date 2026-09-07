package com.fherrmann.wahlen.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class RestClientConfig {

    /**
     * Eigener Client fuer DAWUM: fester User-Agent (Hoeflichkeit gegenueber einer
     * kostenlos betriebenen Quelle) und knappe Timeouts, damit ein haengender
     * Abruf nicht den Scheduler blockiert.
     *
     * <p>Redirects werden nicht automatisch gefolgt — die API liegt unter einer
     * festen URL, ein Redirect waere ein Hinweis auf ein Problem, keine Normalitaet.
     */
    @Bean
    RestClient dawumRestClient(WahlenProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(30));

        // Bewusst RestClient.builder() statt des autokonfigurierten Builders:
        // Spring Boot 4 stellt den nur mit dem Modul spring-boot-restclient
        // bereit, und dieser Client braucht ohnehin eine eigene Konfiguration.
        return RestClient.builder()
                .baseUrl(properties.dawum().baseUrl())
                .defaultHeader("User-Agent", properties.dawum().userAgent())
                .requestFactory(factory)
                .build();
    }

    /**
     * Client fuer die Landeswahlleitungen am Wahlabend: keine Basis-URL (jede
     * Quelle bringt ihre eigene mit), derselbe User-Agent, Redirects erlaubt —
     * einige Landesportale leiten zwischen Hostnamen um.
     */
    @Bean
    RestClient wahlabendRestClient(WahlenProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder()
                .defaultHeader("User-Agent", properties.dawum().userAgent())
                .requestFactory(factory)
                .build();
    }
}
