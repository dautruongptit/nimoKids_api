package com.nimokids.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimokids.entity.AnswerItem;
import com.nimokids.repository.AnswerItemRepository;
import com.nimokids.service.ItemImageService.ItemImage;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ItemImageServiceImplTest {

    private static final byte[] PICTURE = {1, 2, 3, 4, 5};

    private final AnswerItemRepository repository = mock(AnswerItemRepository.class);
    private final ItemImageServiceImpl service = new ItemImageServiceImpl(repository);

    private AnswerItem stored(Object image, boolean active) {
        AnswerItem item = AnswerItem.builder().code("COLORS_BLACK").name("Black").active(active).build();
        item.setId(UUID.randomUUID());
        if (image != null) {
            item.getMetadata().put("image", image);
        }
        when(repository.findById(item.getId())).thenReturn(Optional.of(item));
        return item;
    }

    private static String dataUri(String type) {
        return "data:" + type + ";base64," + Base64.getEncoder().encodeToString(PICTURE);
    }

    @Test
    void decodesTheStoredDataUriAndReportsItsType() {
        AnswerItem item = stored(dataUri("image/webp"), true);

        ItemImage image = service.find(item.getId()).orElseThrow();

        assertThat(image.bytes()).isEqualTo(PICTURE);
        assertThat(image.contentType()).isEqualTo("image/webp");
        assertThat(image.etag()).startsWith("\"").endsWith("\"");
    }

    @Test
    void theEtagChangesWhenThePictureChanges() {
        AnswerItem first = stored(dataUri("image/webp"), true);
        AnswerItem second = stored("data:image/webp;base64," + Base64.getEncoder().encodeToString(new byte[] {9, 9}), true);

        assertThat(service.find(first.getId()).orElseThrow().etag())
                .isNotEqualTo(service.find(second.getId()).orElseThrow().etag());
    }

    @Test
    void anItemWithoutAPictureHasNoImage() {
        assertThat(service.find(stored(null, true).getId())).isEmpty();
    }

    @Test
    void anUnknownItemHasNoImage() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThat(service.find(id)).isEmpty();
    }

    @Test
    void anInactiveItemHasNoImage() {
        assertThat(service.find(stored(dataUri("image/webp"), false).getId())).isEmpty();
    }

    @Test
    void onlyRasterPictureTypesAreServed() {
        assertThat(service.find(stored(dataUri("image/svg+xml"), true).getId())).isEmpty();
        assertThat(service.find(stored(dataUri("text/html"), true).getId())).isEmpty();
    }

    @Test
    void aValueThatIsNotADataUriOrNotValidBase64IsRejected() {
        assertThat(service.find(stored("/assets/vocab/colors/colors_black.webp", true).getId())).isEmpty();
        assertThat(service.find(stored("data:image/webp;base64,@@@not-base64@@@", true).getId())).isEmpty();
        assertThat(service.find(stored(Map.of("not", "a string"), true).getId())).isEmpty();
    }
}
