package com.tracetick.auth;

import com.tracetick.domain.Customer;
import com.tracetick.domain.Role;
import com.tracetick.domain.User;
import com.tracetick.persistence.CustomerRepository;
import com.tracetick.persistence.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapInitializer implements CommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(BootstrapInitializer.class);

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapProperties properties;

    public BootstrapInitializer(UserRepository userRepository,
                                CustomerRepository customerRepository,
                                PasswordEncoder passwordEncoder,
                                BootstrapProperties properties) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!properties.enabled()) {
            LOG.info("Bootstrap admin seed disabled (tracetick.bootstrap.enabled=false)");
            return;
        }
        if (userRepository.findByEmail(properties.adminEmail()).isPresent()) {
            LOG.info("Bootstrap admin already exists: {}", properties.adminEmail());
            return;
        }
        Customer customer = ensureSingleCustomer();
        User admin = User.create(
                customer,
                properties.adminEmail(),
                passwordEncoder.encode(properties.adminPassword()),
                Role.TECHNICIAN);
        userRepository.save(admin);
        LOG.info("Bootstrap admin user created: email={} role=TECHNICIAN", properties.adminEmail());
    }

    private Customer ensureSingleCustomer() {
        return customerRepository.findAll().stream().findFirst().orElseGet(() ->
                customerRepository.save(Customer.create("TraceTick", "ops@tracetick.local")));
    }
}
