package com.grun.calorietracker.service;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.w3c.dom.Element;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.*;
import java.math.BigDecimal;
import java.math.MathContext;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@Slf4j
public class EcbReferenceRateService {
    private static final URI SOURCE = URI.create("https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml");
    private static final URI BACKUP = URI.create("https://api.frankfurter.dev/v2/rate/USD/EUR?providers=ECB");
    @FunctionalInterface interface Fetcher { byte[] get(URI uri) throws Exception; }
    private final Fetcher fetcher;
    public EcbReferenceRateService() {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NORMAL).build();
        fetcher = uri -> {
            var response = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body().length > 100_000) throw new IllegalStateException("Invalid rate response: " + response.statusCode());
            return response.body();
        };
    }
    EcbReferenceRateService(Fetcher fetcher) { this.fetcher = fetcher; }
    private Rate cached;
    private Instant nextAttempt = Instant.EPOCH;

    public record Rate(String source, LocalDate rateDate, BigDecimal usdToEur, Instant fetchedAt, boolean stale) {}

    public synchronized Rate latest() {
        Instant now = Instant.now();
        if (now.isAfter(nextAttempt)) {
            nextAttempt = now.plus(Duration.ofMinutes(1));
            if (cached != null) cached = new Rate(cached.source(), cached.rateDate(), cached.usdToEur(), cached.fetchedAt(), true);
            for (URI source : new URI[]{SOURCE, BACKUP}) {
                try {
                    byte[] body = fetcher.get(source);
                    Rate candidate = source.equals(SOURCE) ? parse(body, now) : parseBackup(body, now);
                    if (candidate.rateDate().isBefore(LocalDate.ofInstant(now, ZoneOffset.UTC).minusDays(7))) throw new IllegalArgumentException("Expired reference rate");
                    cached = candidate;
                    nextAttempt = now.plus(Duration.ofHours(1));
                    break;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception unavailable) {
                    log.warn("Reference rate refresh failed for {}: {}", source.getHost(), unavailable.toString());
                }
            }
        }
        if (cached == null || cached.rateDate().isBefore(LocalDate.now(ZoneOffset.UTC).minusDays(7))) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ECB reference rate unavailable");
        }
        return cached;
    }

    static Rate parseBackup(byte[] json, Instant fetchedAt) throws Exception {
        var node = new ObjectMapper().readTree(json);
        if (!"USD".equals(node.path("base").asText()) || !"EUR".equals(node.path("quote").asText()) || !node.path("rate").isNumber()) throw new IllegalArgumentException("Invalid currency pair");
        var date = LocalDate.parse(node.path("date").asText());
        var rate = node.path("rate").decimalValue();
        if (rate.signum() <= 0 || date.isAfter(LocalDate.ofInstant(fetchedAt, ZoneOffset.UTC))) throw new IllegalArgumentException("Invalid reference rate");
        return new Rate("Frankfurter (ECB)", date, rate, fetchedAt, false);
    }

    static Rate parse(byte[] xml, Instant fetchedAt) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var nodes = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getElementsByTagName("Cube");
        LocalDate date = null;
        BigDecimal usdPerEuro = null;
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if (element.hasAttribute("time")) date = LocalDate.parse(element.getAttribute("time"));
            if ("USD".equals(element.getAttribute("currency"))) usdPerEuro = new BigDecimal(element.getAttribute("rate"));
        }
        if (date == null || date.isAfter(LocalDate.ofInstant(fetchedAt, ZoneOffset.UTC)) || usdPerEuro == null || usdPerEuro.signum() <= 0) throw new IllegalArgumentException("Invalid ECB rate");
        return new Rate("ECB", date, BigDecimal.ONE.divide(usdPerEuro, MathContext.DECIMAL128), fetchedAt, false);
    }
}
