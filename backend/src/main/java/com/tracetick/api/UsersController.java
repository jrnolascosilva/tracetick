package com.tracetick.api;

import com.tracetick.api.dto.CreateUserRequest;
import com.tracetick.api.dto.UpdateUserRequest;
import com.tracetick.api.dto.UserDto;
import com.tracetick.domain.Customer;
import com.tracetick.domain.User;
import com.tracetick.persistence.CustomerRepository;
import com.tracetick.persistence.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UsersController {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    public UsersController(UserRepository userRepository,
                           CustomerRepository customerRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    @PreAuthorize("hasRole('TECHNICIAN')")
    public List<UserDto> list() {
        return userRepository.findAll().stream().map(UserDto::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Transactional
    public ResponseEntity<UserDto> create(@Valid @RequestBody CreateUserRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
        }
        Customer customer = singleCustomer();
        User user = User.create(
                customer,
                request.email(),
                passwordEncoder.encode(request.password()),
                request.role());
        User saved = userRepository.save(user);
        return ResponseEntity.created(URI.create("/api/v1/users/" + saved.getId()))
                .body(UserDto.from(saved));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Transactional
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (request.role() != null) {
            user.changeRole(request.role());
        }
        if (request.active() != null) {
            if (request.active()) {
                user.activate();
            } else {
                user.deactivate();
            }
        }
        return UserDto.from(userRepository.save(user));
    }

    private Customer singleCustomer() {
        return customerRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Customer not bootstrapped"));
    }
}
