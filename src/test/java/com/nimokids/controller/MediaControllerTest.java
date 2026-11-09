package com.nimokids.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimokids.config.SecurityConfig;
import com.nimokids.security.JwtService;
import com.nimokids.security.SecurityErrorHandler;
import com.nimokids.security.StaticRoleAuthorityResolver;
import com.nimokids.service.ApiLogService;
import com.nimokids.service.ItemImageService;
import com.nimokids.service.ItemImageService.ItemImage;
import com.nimokids.service.auth.SessionGuard;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MediaController.class)
@Import({SecurityConfig.class, SecurityErrorHandler.class, StaticRoleAuthorityResolver.class, JwtService.class})
class MediaControllerTest {

    private static final byte[] PICTURE = {1, 2, 3, 4, 5};

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ItemImageService itemImageService;
    @MockitoBean private SessionGuard sessionGuard;
    @MockitoBean private ApiLogService apiLogService;

    private final UUID itemId = UUID.randomUUID();

    @Test
    void servesThePictureToAnyoneWithItsTypeAndCacheHeaders() throws Exception {
        when(itemImageService.find(itemId)).thenReturn(Optional.of(new ItemImage(PICTURE, "image/webp", "\"abc\"")));

        mockMvc.perform(get("/api/v1/media/answer-items/{id}/image", itemId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/webp"))
                .andExpect(header().string("ETag", "\"abc\""))
                .andExpect(header().string("Cache-Control", "max-age=86400, public"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(PICTURE));
    }

    @Test
    void answers304WhenTheBrowserAlreadyHasThisPicture() throws Exception {
        when(itemImageService.find(itemId)).thenReturn(Optional.of(new ItemImage(PICTURE, "image/webp", "\"abc\"")));

        mockMvc.perform(get("/api/v1/media/answer-items/{id}/image", itemId).header("If-None-Match", "\"abc\""))
                .andExpect(status().isNotModified());
    }

    @Test
    void anItemWithoutAPictureIs404() throws Exception {
        when(itemImageService.find(itemId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/media/answer-items/{id}/image", itemId))
                .andExpect(status().isNotFound());
    }
}
