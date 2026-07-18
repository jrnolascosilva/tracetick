package com.tracetick.persistence;

import com.tracetick.domain.Customer;

final class UserMother {

    private UserMother() {}

    static Customer customer() {
        return Customer.create("TraceTick", "ops@tracetick.local");
    }
}
