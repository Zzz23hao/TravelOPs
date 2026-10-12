package com.travelops;

import com.travelops.controller.CommentController;
import com.travelops.dto.PageResponse;
import com.travelops.security.JwtService;
import com.travelops.security.SecurityConfig;
import com.travelops.service.CommentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommentController.class)
@Import({SecurityConfig.class, JwtService.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-that-is-at-least-32-bytes-long",
        "jwt.expiration-minutes=60"})
class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private CommentService commentService;

    @Test
    void listingCommentsIsPublic() throws Exception {
        when(commentService.list(10L, 0, 20)).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/posts/10/comments"))
                .andExpect(status().isOk());
    }

    @Test
    void commentingWithoutTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/posts/10/comments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Great itinerary!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        verifyNoInteractions(commentService);
    }

    @Test
    void deletingWithoutTokenReturns401() throws Exception {
        mockMvc.perform(delete("/api/comments/5"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(commentService);
    }

    @Test
    void blankCommentReturns400() throws Exception {
        mockMvc.perform(post("/api/posts/10/comments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(2L))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
        verifyNoInteractions(commentService);
    }

    @Test
    void commentIsCreatedAsTheTokenSubject() throws Exception {
        mockMvc.perform(post("/api/posts/10/comments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(2L))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\": \"Great itinerary!\"}"))
                .andExpect(status().isCreated());
        verify(commentService).create(eq(10L), eq(2L), any());
    }
}
