package com.travelops;

import com.travelops.dto.CommentCreateRequest;
import com.travelops.dto.CommentResponse;
import com.travelops.entity.Comment;
import com.travelops.entity.Post;
import com.travelops.entity.User;
import com.travelops.exception.ApiException;
import com.travelops.repository.CommentRepository;
import com.travelops.repository.PostRepository;
import com.travelops.repository.UserRepository;
import com.travelops.service.CommentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    private static final Long ALICE_ID = 1L;
    private static final Long BOB_ID = 2L;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    private CommentService commentService;
    private User alice;
    private User bob;
    private Post post;

    @BeforeEach
    void setUp() {
        commentService = new CommentService(commentRepository, postRepository, userRepository);
        alice = withId(new User("alice@example.com", "hash", "Alice"), ALICE_ID);
        bob = withId(new User("bob@example.com", "hash", "Bob"), BOB_ID);
        post = withId(new Post(alice, "Title", "Content", "Vancouver", "Canada"), 10L);
    }

    @Test
    void loggedInUserCanCommentOnAnotherUsersPost() {
        when(postRepository.findById(10L)).thenReturn(Optional.of(post));
        when(userRepository.findById(BOB_ID)).thenReturn(Optional.of(bob));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        CommentResponse response = commentService.create(10L, BOB_ID, new CommentCreateRequest(" Great itinerary! "));

        assertThat(response.postId()).isEqualTo(10L);
        assertThat(response.authorName()).isEqualTo("Bob");
        assertThat(response.content()).isEqualTo("Great itinerary!");
    }

    @Test
    void commentOnMissingPostReturns404() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.create(99L, BOB_ID, new CommentCreateRequest("Hi")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("POST_NOT_FOUND"));
        verifyNoInteractions(commentRepository);
    }

    @Test
    void listingCommentsOfMissingPostReturns404NotAnEmptyPage() {
        when(postRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> commentService.list(99L, 0, 20))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void authorCanDeleteOwnComment() {
        Comment comment = withId(new Comment(post, bob, "Hi"), 5L);
        when(commentRepository.findById(5L)).thenReturn(Optional.of(comment));

        commentService.delete(5L, BOB_ID);

        verify(commentRepository).delete(comment);
    }

    @Test
    void cannotDeleteSomeoneElsesCommentEvenOnOwnPost() {
        Comment bobsComment = withId(new Comment(post, bob, "Hi"), 5L);
        when(commentRepository.findById(5L)).thenReturn(Optional.of(bobsComment));

        assertThatThrownBy(() -> commentService.delete(5L, ALICE_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(commentRepository, never()).delete(any(Comment.class));
    }

    @Test
    void deletingMissingCommentReturns404() {
        when(commentRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.delete(5L, ALICE_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("COMMENT_NOT_FOUND"));
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
