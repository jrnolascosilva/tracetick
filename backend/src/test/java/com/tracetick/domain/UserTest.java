package com.tracetick.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void createStoresEmailPasswordRoleAndDefaultsActiveToTrue() {
        User user = User.create(stubCustomer(), "alex@tracetick.local", "hash", Role.TECHNICIAN);

        assertThat(user.getEmail()).isEqualTo("alex@tracetick.local");
        assertThat(user.getPasswordHash()).isEqualTo("hash");
        assertThat(user.getRole()).isEqualTo(Role.TECHNICIAN);
        assertThat(user.isActive()).isTrue();
        assertThat(user.getCreatedAt()).isNotNull();
    }

    @Test
    void deactivateFlipsActiveFalse() {
        User user = User.create(stubCustomer(), "alex@tracetick.local", "hash", Role.TECHNICIAN);

        user.deactivate();

        assertThat(user.isActive()).isFalse();
    }

    @Test
    void activateReturnsDeactivatedUserToActiveTrue() {
        User user = User.create(stubCustomer(), "alex@tracetick.local", "hash", Role.TECHNICIAN);
        user.deactivate();

        user.activate();

        assertThat(user.isActive()).isTrue();
    }

    @Test
    void changeRoleReplacesStoredRole() {
        User user = User.create(stubCustomer(), "alex@tracetick.local", "hash", Role.TECHNICIAN);

        user.changeRole(Role.CUSTOMER);

        assertThat(user.getRole()).isEqualTo(Role.CUSTOMER);
    }

    private static Customer stubCustomer() {
        return Customer.create("TraceTick", "ops@tracetick.local");
    }
}
