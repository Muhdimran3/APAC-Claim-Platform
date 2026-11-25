package com.example.claims.user;

import com.example.claims.common.web.ApiExceptions.ForbiddenException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class UserController {

    private final AppUserRepository users;

    public UserController(AppUserRepository users) {
        this.users = users;
    }

    @GetMapping("/me")
    public CurrentUser me(CurrentUser user) {
        return user;
    }

    /** For the manager's "assign to" picker. */
    @GetMapping("/users/officers")
    public List<OfficerResponse> officers(CurrentUser user) {
        if (!user.isStaff()) {
            throw new ForbiddenException("Only staff can list officers");
        }
        return users.findByRoleOrderByDisplayName(Role.OFFICER).stream()
                .map(u -> new OfficerResponse(u.getId(), u.getUsername(), u.getDisplayName()))
                .toList();
    }

    public record OfficerResponse(UUID id, String username, String displayName) {
    }
}
