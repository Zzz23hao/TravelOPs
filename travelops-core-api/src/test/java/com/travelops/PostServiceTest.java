package com.travelops;

import com.travelops.dto.PostCreateRequest;
import com.travelops.dto.PostResponse;
import com.travelops.dto.PostUpdateRequest;
import com.travelops.entity.Post;
import com.travelops.entity.User;
import com.travelops.exception.ApiException;
import com.travelops.repository.PostRepository;
import com.travelops.repository.UserRepository;
import com.travelops.service.PostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    private static final Long ALICE_ID = 1L;
    private static final Long BOB_ID = 2L;

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    private PostService postService;
    private User alice;

    @BeforeEach
    void setUp() {
        postService = new PostService(postRepository, userRepository);
        alice = user(ALICE_ID, "Alice");
    }

    @Test
    void createAssignsTheCallerAsAuthorAndStripsWhitespace() {
        when(userRepository.findById(ALICE_ID)).thenReturn(Optional.of(alice));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        PostResponse response = postService.create(ALICE_ID,
                new PostCreateRequest("  Three Days in Vancouver ", "Day 1: Stanley Park", " Vancouver ", "Canada"));

        assertThat(response.title()).isEqualTo("Three Days in Vancouver");
        assertThat(response.destinationCity()).isEqualTo("Vancouver");
        assertThat(response.authorId()).isEqualTo(ALICE_ID);
        assertThat(response.authorName()).isEqualTo("Alice");
    }

    @Test
    void createForDeletedUserReturns401InsteadOfForeignKeyError() {
        when(userRepository.findById(ALICE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.create(ALICE_ID, new PostCreateRequest("T", "C", "City", "Country")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(postRepository, never()).save(any());
    }

    @Test
    void getMissingPostThrowsNotFound() {
        when(postRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.get(42L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.getCode()).isEqualTo("POST_NOT_FOUND");
                });
    }

    @Test
    void ownerCanUpdateAndOnlyProvidedFieldsChange() {
        Post existing = alicesPost();
        when(postRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(postRepository.saveAndFlush(existing)).thenReturn(existing);

        PostResponse response = postService.update(10L, ALICE_ID, new PostUpdateRequest("New title", null, null, null));

        assertThat(response.title()).isEqualTo("New title");
        assertThat(response.content()).isEqualTo("Old content");
        assertThat(response.destinationCity()).isEqualTo("Vancouver");
    }

    @Test
    void nonOwnerCannotUpdate() {
        Post existing = alicesPost();
        when(postRepository.findById(10L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> postService.update(10L, BOB_ID, new PostUpdateRequest("Hijacked", null, null, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(existing.getTitle()).isEqualTo("Old title");
        verify(postRepository, never()).saveAndFlush(any());
    }

    @Test
    void ownerCanDelete() {
        Post existing = alicesPost();
        when(postRepository.findById(10L)).thenReturn(Optional.of(existing));

        postService.delete(10L, ALICE_ID);

        verify(postRepository).delete(existing);
    }

    @Test
    void nonOwnerCannotDelete() {
        when(postRepository.findById(10L)).thenReturn(Optional.of(alicesPost()));

        assertThatThrownBy(() -> postService.delete(10L, BOB_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("FORBIDDEN"));
        verify(postRepository, never()).delete(any(Post.class));
    }

    @Test
    void deleteMissingPostIs404EvenForNonOwner() {
        when(postRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.delete(7L, BOB_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(postRepository, never()).delete(any(Post.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listPassesPageSizeAndWhitelistedSortToRepository() {
        Page<Post> empty = new PageImpl<>(List.of(), PageRequest.of(2, 5), 0);
        when(postRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(empty);

        postService.list("vancouver", null, 2, 5, "createdAt_asc");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void listRejectsUnknownSort() {
        assertThatThrownBy(() -> postService.list(null, null, 0, 20, "password_desc"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("sort must be one of");
    }

    private Post alicesPost() {
        return new Post(alice, "Old title", "Old content", "Vancouver", "Canada");
    }

    private static User user(Long id, String name) {
        User user = new User(name.toLowerCase() + "@example.com", "hash", name);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
