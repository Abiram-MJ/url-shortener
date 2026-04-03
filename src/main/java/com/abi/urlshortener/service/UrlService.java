package com.abi.urlshortener.service;

import com.abi.urlshortener.dto.ShortenRequest;
import com.abi.urlshortener.dto.ShortenResponse;
import com.abi.urlshortener.dto.UrlStatsResponse;
import com.abi.urlshortener.entity.Url;
import com.abi.urlshortener.exception.UrlExpiredException;
import com.abi.urlshortener.exception.UrlNotFoundException;
import com.abi.urlshortener.repository.UrlRepository;
import com.abi.urlshortener.util.Base62Encoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlService {

    private final UrlRepository urlRepository;
    private final Base62Encoder base62Encoder;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.cache.url-ttl-seconds}")
    private long cacheTtlSeconds;

    /**
     * Creates a new short URL entry in the database and caches it in Redis.
     *
     * @param request the request containing the original URL and optional TTL
     * @return a {@link ShortenResponse} with the generated short code and full short URL
     */
    @Transactional
    public ShortenResponse shorten(ShortenRequest request) {
        Url url = Url.builder()
                .originalUrl(request.getUrl())
                .shortCode("pending") // placeholder before ID is assigned
                .expiresAt(request.getTtlSeconds() != null
                        ? LocalDateTime.now().plusSeconds(request.getTtlSeconds())
                        : null)
                .build();

        Url saved = urlRepository.save(url);

        String shortCode = base62Encoder.encode(saved.getId());
        saved.setShortCode(shortCode);
        urlRepository.save(saved);

        cacheUrl(shortCode, saved.getOriginalUrl());
        log.info("Created short code '{}' for URL: {}", shortCode, saved.getOriginalUrl());

        return toShortenResponse(saved);
    }

    /**
     * Resolves a short code to its original URL, incrementing the click counter.
     * Checks Redis first; falls back to PostgreSQL on a cache miss.
     *
     * @param shortCode the short code to resolve
     * @return the original URL to redirect to
     * @throws UrlNotFoundException if no entry exists for the given code
     * @throws UrlExpiredException  if the entry has passed its expiry time
     */
    @Transactional
    public String resolve(String shortCode) {
        String cached = redisTemplate.opsForValue().get(cacheKey(shortCode));
        if (cached != null) {
            log.debug("Cache hit for short code: {}", shortCode);
            incrementClickCountAsync(shortCode);
            return cached;
        }

        log.debug("Cache miss for short code: {}", shortCode);
        Url url = fetchOrThrow(shortCode);
        checkExpiry(url);

        cacheUrl(shortCode, url.getOriginalUrl());
        urlRepository.incrementClickCount(shortCode);

        return url.getOriginalUrl();
    }

    /**
     * Returns analytics for the given short code.
     *
     * @param shortCode the short code to look up
     * @return a {@link UrlStatsResponse} with click count and metadata
     * @throws UrlNotFoundException if no entry exists for the given code
     */
    @Transactional(readOnly = true)
    public UrlStatsResponse getStats(String shortCode) {
        Url url = fetchOrThrow(shortCode);
        return toStatsResponse(url);
    }

    // --- private helpers ---

    private Url fetchOrThrow(String shortCode) {
        return urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));
    }

    private void checkExpiry(Url url) {
        if (url.getExpiresAt() != null && LocalDateTime.now().isAfter(url.getExpiresAt())) {
            throw new UrlExpiredException(url.getShortCode());
        }
    }

    private void cacheUrl(String shortCode, String originalUrl) {
        redisTemplate.opsForValue().set(cacheKey(shortCode), originalUrl, cacheTtlSeconds, TimeUnit.SECONDS);
    }

    private void incrementClickCountAsync(String shortCode) {
        // Still update the DB counter; called from within an active transaction via resolve()
        urlRepository.incrementClickCount(shortCode);
    }

    private String cacheKey(String shortCode) {
        return "url:" + shortCode;
    }

    private ShortenResponse toShortenResponse(Url url) {
        return ShortenResponse.builder()
                .shortCode(url.getShortCode())
                .shortUrl(baseUrl + "/" + url.getShortCode())
                .originalUrl(url.getOriginalUrl())
                .createdAt(url.getCreatedAt())
                .expiresAt(url.getExpiresAt())
                .build();
    }

    private UrlStatsResponse toStatsResponse(Url url) {
        return UrlStatsResponse.builder()
                .shortCode(url.getShortCode())
                .shortUrl(baseUrl + "/" + url.getShortCode())
                .originalUrl(url.getOriginalUrl())
                .clickCount(url.getClickCount())
                .createdAt(url.getCreatedAt())
                .expiresAt(url.getExpiresAt())
                .build();
    }
}
