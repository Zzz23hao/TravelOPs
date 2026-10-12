package com.travelops;

import com.travelops.controller.PostController;
import com.travelops.exception.ApiException;
import com.travelops.service.PostService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web layer only: verifies request validation and the error body shape without a database.
 */
@WebMvcTest(PostController.class)
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostService postService;

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

        mockMvc.perform(post("/api/posts").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message")
                        .value("destination_city: must not be blank; title: must not be blank"));
        verifyNoInteractions(postService);
    }

    @Test
    void patchWithBlankFieldReturns400() throws Exception {
        mockMvc.perform(patch("/api/posts/1").contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"\"}"))
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
        mockMvc.perform(post("/api/posts").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
    }
}
