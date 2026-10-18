package in.anytimework.atwbackend.shared.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionCheckerTest {

    private final PermissionChecker checker = new PermissionChecker();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateWith(String... authorities) {
        var granted = List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "n/a", granted));
    }

    @Test
    void grantsWhenExactPermissionPresent() {
        authenticateWith("staff.view", "roles.view");
        assertThat(checker.has("staff.view")).isTrue();
    }

    @Test
    void deniesWhenPermissionMissing() {
        authenticateWith("staff.view");
        assertThat(checker.has("payroll.manage_deductions")).isFalse();
    }

    @Test
    void superAdminPassesEveryCheck() {
        authenticateWith("SUPER_ADMIN");
        assertThat(checker.has("anything.at_all")).isTrue();
        assertThat(checker.has("payroll.delete_salary_structures")).isTrue();
    }

    @Test
    void deniesWhenUnauthenticated() {
        assertThat(checker.has("staff.view")).isFalse();
    }
}
