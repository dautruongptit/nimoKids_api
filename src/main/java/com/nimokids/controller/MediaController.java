package com.nimokids.controller;

import com.nimokids.exception.ResourceNotFoundException;
import com.nimokids.service.ItemImageService;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/** Pictures kept in the database. The question snapshot only stores the item id; the picture is read here. */
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaController {

    private static final Duration BROWSER_CACHE = Duration.ofDays(1);

    private final ItemImageService itemImageService;

    @GetMapping("/answer-items/{itemId}/image")
    public ResponseEntity<byte[]> getAnswerItemImage(@PathVariable UUID itemId, WebRequest request) {
        ItemImageService.ItemImage image = itemImageService.find(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item image", itemId));
        // The browser already has this exact picture: answer 304 without the body.
        if (request.checkNotModified(image.etag())) {
            return null;
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(BROWSER_CACHE).cachePublic())
                .eTag(image.etag())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.bytes());
    }
}
