package com.travelops.controller;

import com.travelops.dto.CommentCreateRequest;
import com.travelops.dto.CommentResponse;
import com.travelops.dto.PageResponse;
import com.travelops.security.AuthUser;
import com.travelops.service.CommentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping("/api/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse create(@AuthenticationPrincipal AuthUser user, @PathVariable Long postId,
                                  @Valid @RequestBody CommentCreateRequest request) {
        return commentService.create(postId, user.id(), request);
    }

    @GetMapping("/api/posts/{postId}/comments")
    public PageResponse<CommentResponse> list(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return commentService.list(postId, page, size);
    }

    @DeleteMapping("/api/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long commentId) {
        commentService.delete(commentId, user.id());
    }
}
