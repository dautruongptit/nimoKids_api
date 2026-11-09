package com.nimokids.service;

import java.util.Optional;
import java.util.UUID;

public interface ItemImageService {

    /**
     * The picture of an answer item, stored in {@code answer_items.metadata.image} as a data URI
     * ({@code data:image/webp;base64,...}). Empty when the item does not exist, is not active or has no usable picture.
     */
    Optional<ItemImage> find(UUID answerItemId);

    /** {@code etag} is a strong validator derived from the stored data, so a changed picture is fetched again. */
    record ItemImage(byte[] bytes, String contentType, String etag) {
    }
}
