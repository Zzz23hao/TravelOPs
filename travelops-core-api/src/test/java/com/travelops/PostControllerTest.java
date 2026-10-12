package com.travelops;

import com.travelops.controller.PostController;
import com.travelops.dto.PageResponse;
import com.travelops.exception.ApiException;
import com.travelops.security.JwtService;
import com.travelops.security.SecurityConfig;
import com.travelops.service.PostService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web layer with the real security filter chain: validation, error body shape and which
 * endpoints need a token. No database.
 */
@WebMvcTest(PostController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-that-is-at-least-32-bytes-long",
        "jwt.expiration-minutes=60"})
class PostControllerTest {

    private static final String VALID_BODY = """
            {"title": "T", "content": "C", "destination_city": "Vancouver", "destination_country": "Canada"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private PostService postService;

    private String bearer() {
        return "Bearer " + jwtService.generateToken(1L);
    }

    @Test
    void listIsPublic() throws Exception {
        when(postService.list(isNull(), isNull(), anyInt(), anyInt(), anyString()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(0));
    }

    @Test
    void createWithoutTokenReturns401AndNeverReachesService() throws Exception {
        mockMvc.perform(post("/api/posts").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        verifyNoInteractions(postService);
    }

    @Test
    void patchAndDeleteWithoutTokenReturn401() throws Exception {
        mockMvc.perform(patch("/api/posts/1").contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"x\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/posts/1"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(postService);
    }

    @Test
    void createWithInvalidTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/posts").header(HttpHeaders.AUTHORIZATION, "Bearer forged.token.value")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(postService);
    }

    @Test
    void missingPostReturns404WithTypedErrorBody() throws Exception {
        when(postService.get(99L)).thenThrow(
                new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "Post 99 not found."));

        mockMvc.perform(get("/api/posts/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("POST_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Post 99 not found."));
    }

    @Test
    void createWithBlankFieldsReturns400AndNeverReachesService() throws Exception {
        String body = """
                {"title": " ", "content": "x", "destination_country": "Canada"}
                """;

        mockMvc.perform(post("/api/posts").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message")
                        .value("destination_city: must not be blank; title: must not be blank"));
        verifyNoInteractions(postService);
    }

    @Test
    void patchWithBlankFieldReturns400() throws Exception {
        mockMvc.perform(patch("/api/posts/1").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void pageSizeAboveLimitReturns400() throws Exception {
        mockMvc.perform(get("/api/posts").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void nonNumericIdReturns400() throws Exception {
        mockMvc.perform(get("/api/posts/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/posts").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
    }

    @Test
    void createWithTokenReachesService() throws Exception {
        when(postService.create(any())).thenReturn(null);

        mockMvc.perform(post("/api/posts").header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated());
    }
}
