package com.tracetick.auth;

import com.tracetick.domain.Customer;
import com.tracetick.domain.Role;
import com.tracetick.domain.User;
import com.tracetick.persistence.CustomerRepository;
import com.tracetick.persistence.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

public final class TestFixtures {

    private TestFixtures() {}

    public static void seedUser(UserRepository userRepository,
                                CustomerRepository customerRepository,
                                PasswordEncoder passwordEncoder,
                                String email,
                                String password,
                                Role role) {
        Customer customer = customerRepository.save(Customer.create("TraceTick", "ops@tracetick.local"));
        String hash = passwordEncoder.encode(password);
        User user = User.create(customer, email, hash, role);
        userRepository.save(user);
    }

    public static void deactivate(UserRepository userRepository, String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        user.deactivate();
        userRepository.save(user);
    }
}
