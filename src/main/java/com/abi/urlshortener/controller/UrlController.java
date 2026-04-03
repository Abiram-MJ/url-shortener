package com.abi.urlshortener.controller;

import com.abi.urlshortener.dto.ShortenRequest;
import com.abi.urlshortener.dto.ShortenResponse;
import com.abi.urlshortener.dto.UrlStatsResponse;
import com.abi.urlshortener.service.UrlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Slf4j
public class UrlController {

    private final UrlService urlService;

    /**
     * Creates a new short URL.
     *
     * @param request body containing the original URL and optional TTL
     * @return 201 Created with the generated short URL details
     */
    @PostMapping("/api/shorten")
    public ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        ShortenResponse response = urlService.shorten(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Redirects to the original URL corresponding to the given short code.
     *
     * @param shortCode the short code segment from the URL
     * @return 302 Found redirect to the original URL
     */
    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        String originalUrl = urlService.resolve(shortCode);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, originalUrl)
                .build();
    }

    /**
     * Returns click analytics for the given short code.
     *
     * @param shortCode the short code to look up
     * @return 200 OK with stats payload
     */
    @GetMapping("/api/stats/{shortCode}")
    public ResponseEntity<UrlStatsResponse> stats(@PathVariable String shortCode) {
        return ResponseEntity.ok(urlService.getStats(shortCode));
    }
}
