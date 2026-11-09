package com.nimokids.service.impl;

import com.nimokids.entity.AnswerItem;
import com.nimokids.repository.AnswerItemRepository;
import com.nimokids.service.ItemImageService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemImageServiceImpl implements ItemImageService {

    public static final String IMAGE_KEY = "image";

    /** Only raster picture types are served: the endpoint must never become a way to deliver scripts or SVG. */
    private static final Set<String> ALLOWED_TYPES = Set.of("image/webp", "image/png", "image/jpeg");
    private static final Pattern DATA_URI = Pattern.compile("^data:([a-z]+/[a-z0-9.+-]+);base64,(.+)$", Pattern.DOTALL);

    private final AnswerItemRepository answerItemRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<ItemImage> find(UUID answerItemId) {
        return answerItemRepository.findById(answerItemId)
                .filter(AnswerItem::isActive)
                .flatMap(item -> parse(item.getCode(), item.getMetadata() == null ? null : item.getMetadata().get(IMAGE_KEY)));
    }

    private static Optional<ItemImage> parse(String code, Object raw) {
        if (!(raw instanceof String uri) || uri.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = DATA_URI.matcher(uri);
        if (!matcher.matches() || !ALLOWED_TYPES.contains(matcher.group(1))) {
            log.warn("Item {} has an image that is not a base64 data URI of an allowed type", code);
            return Optional.empty();
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(matcher.group(2));
            return Optional.of(new ItemImage(bytes, matcher.group(1), etagOf(bytes)));
        } catch (IllegalArgumentException ex) {
            log.warn("Item {} has an image with invalid base64 data", code);
            return Optional.empty();
        }
    }

    private static String etagOf(byte[] bytes) {
        try {
            return "\"" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes), 0, 8) + "\"";
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is always available", ex);
        }
    }
}
