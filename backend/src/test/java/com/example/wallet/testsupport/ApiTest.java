package com.example.wallet.testsupport;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.example.wallet.user.Role;
import com.example.wallet.testsupport.TestUsers.TestUser;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Base for HTTP-level integration tests: MockMvc, users, ledger fixtures. */
@AutoConfigureMockMvc
public abstract class ApiTest extends AbstractIntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected TestUsers users;

    @Autowired
    protected TestLedger ledger;

    protected Api api;

    @BeforeEach
    void setUpApi() {
        api = new Api(mvc, json);
    }

    protected TestUser customer() {
        return users.create(Role.CUSTOMER);
    }

    protected TestUser support() {
        return users.create(Role.SUPPORT);
    }

    protected TestUser admin() {
        return users.create(Role.ADMIN);
    }
}
