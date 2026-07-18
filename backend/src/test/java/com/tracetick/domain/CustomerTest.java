package com.tracetick.domain;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerTest {

    @Test
    void createStoresOrgNameContactEmailAndCreatedAt() throws Exception {
        Customer customer = Customer.create("TraceTick", "ops@tracetick.local");

        assertThat(customer.getOrgName()).isEqualTo("TraceTick");
        assertThat(customer.getContactEmail()).isEqualTo("ops@tracetick.local");
        assertThat(customer.getCreatedAt()).isNotNull();
    }

    @Test
    void newInstancesAreUnpersistedUntilAssignedAnId() throws Exception {
        Customer customer = Customer.create("TraceTick", "ops@tracetick.local");

        Field idField = Customer.class.getDeclaredField("id");
        idField.setAccessible(true);
        Object idValue = idField.get(customer);
        assertThat(idValue).isNull();
    }
}
