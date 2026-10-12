package com.travelops.service;

import com.travelops.dto.CommentCreateRequest;
import com.travelops.dto.CommentResponse;
import com.travelops.dto.PageResponse;
import com.travelops.entity.Comment;
import com.travelops.entity.Post;
import com.travelops.entity.User;
import com.travelops.exception.ApiException;
import com.travelops.repository.CommentRepository;
import com.travelops.repository.PostRepository;
import com.travelops.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

    private static final Sort OLDEST_FIRST =
            Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id"));

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public CommentService(CommentRepository commentRepository, PostRepository postRepository,
                          UserRepository userRepository) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CommentResponse create(Long postId, Long userId, CommentCreateRequest request) {
        Post post = postRepository.findById(postId).orElseThrow(() -> postNotFound(postId));
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "User account no longer exists."));
        return CommentResponse.from(commentRepository.save(new Comment(post, author, request.content().strip())));
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> list(Long postId, int page, int size) {
        // Without this check a missing post would look like a post with zero comments.
        if (!postRepository.existsById(postId)) {
            throw postNotFound(postId);
        }
        return PageResponse.from(commentRepository.findByPostId(postId, PageRequest.of(page, size, OLDEST_FIRST))
                .map(CommentResponse::from));
    }

    @Transactional
    public void delete(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND",
                        "Comment " + commentId + " not found."));
        if (!comment.getAuthor().getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only the author can delete this comment.");
        }
        commentRepository.delete(comment);
    }

    private static ApiException postNotFound(Long postId) {
        return new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "Post " + postId + " not found.");
    }
}
