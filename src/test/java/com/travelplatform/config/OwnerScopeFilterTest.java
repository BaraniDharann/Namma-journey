package com.travelplatform.config;

import com.travelplatform.entity.Owner;
import com.travelplatform.repository.OwnerRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OwnerScopeFilterTest {

    private final OwnerRepository owners = mock(OwnerRepository.class);
    private final OwnerScopeFilter filter = new OwnerScopeFilter(owners, "NJO-0001");

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        OwnerScope.clear();
    }

    private static void login(String id, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(id, null, List.of(new SimpleGrantedAuthority(role))));
    }

    private static Owner owner(long id, String code) {
        Owner o = new Owner();
        o.setId(id);
        o.setOwnerCode(code);
        return o;
    }

    @Test
    @DisplayName("an owner's request runs in that owner's scope, and the scope is gone afterwards")
    void ownerRequestIsScoped() throws Exception {
        when(owners.findById(7L)).thenReturn(Optional.of(owner(7, "NJO-0007")));
        login("7", "ROLE_OWNER");
        AtomicReference<OwnerScope.Bound> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(OwnerScope.current().orElse(null));

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertEquals(new OwnerScope.Bound(7, false), seen.get());
        assertTrue(OwnerScope.current().isEmpty());
    }

    @Test
    @DisplayName("NJO-0001 is the platform owner")
    void platformOwnerIsMarked() throws Exception {
        when(owners.findById(1L)).thenReturn(Optional.of(owner(1, "NJO-0001")));
        login("1", "ROLE_OWNER");
        AtomicReference<OwnerScope.Bound> seen = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> seen.set(OwnerScope.current().orElse(null)));

        assertEquals(new OwnerScope.Bound(1, true), seen.get());
    }

    @Test
    @DisplayName("the scope is cleared even when the request fails")
    void clearedOnFailure() {
        when(owners.findById(7L)).thenReturn(Optional.of(owner(7, "NJO-0007")));
        login("7", "ROLE_OWNER");

        assertThrows(IllegalStateException.class, () -> filter.doFilter(new MockHttpServletRequest(),
                new MockHttpServletResponse(), (req, res) -> {
                    throw new IllegalStateException("boom");
                }));
        assertTrue(OwnerScope.current().isEmpty());
    }

    @Test
    @DisplayName("customers, drivers and admins get no owner scope")
    void otherRolesUnscoped() throws Exception {
        login("u-1", "ROLE_USER");
        AtomicReference<Boolean> scoped = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> scoped.set(OwnerScope.current().isPresent()));

        assertEquals(false, scoped.get());
    }
}
