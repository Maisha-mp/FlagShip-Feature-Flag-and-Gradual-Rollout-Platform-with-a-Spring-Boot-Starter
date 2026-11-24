package com.flagship.server.api;

import com.flagship.server.entity.AppUser;
import com.flagship.server.repository.AppUserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Creating and listing users. Only site admins may call this (see ApiAuthorization). */
@RestController
@RequestMapping("/api/users")
public class UserController {

    public record UserRequest(
            @NotBlank
            @Pattern(regexp = "^[a-z0-9._-]{3,50}$",
                     message = "must be 3 to 50 characters: lowercase letters, digits, dot, dash, underscore")
            String username,

            @NotBlank @Size(min = 8, max = 72) String password,

            boolean siteAdmin) { }

    public record UserResponse(String username, boolean siteAdmin) { }

    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public UserController(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody UserRequest request) {
        if (users.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A user named '" + request.username() + "' already exists");
        }
        AppUser user = new AppUser();
        user.setUsername(request.username());
        user.setPasswordHash(encoder.encode(request.password()));
        user.setSiteAdmin(request.siteAdmin());
        users.save(user);
        return new UserResponse(user.getUsername(), user.isSiteAdmin());
    }

    @GetMapping
    public List<UserResponse> list() {
        return users.findAll().stream()
                .sorted(Comparator.comparing(AppUser::getUsername))
                .map(u -> new UserResponse(u.getUsername(), u.isSiteAdmin()))
                .toList();
    }
}