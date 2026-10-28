package com.example.wallet.testsupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Base class for every integration test.
 *
 * <p>WHY a singleton container (started in a static block, never stopped by us):
 * starting Postgres takes seconds; doing it per test class would make the suite slow, and
 * Spring caches the application context across classes only if the DB URL stays the same.
 * Testcontainers' Ryuk sidecar removes the container when the JVM exits.
 *
 * <p>WHY the same init script as docker compose: tests run as the least-privilege
 * {@code wallet_app} role and Flyway as {@code wallet_owner}, exactly like dev/prod. A test
 * suite running as superuser would hide missing GRANTs and silently bypass nothing-but-trigger
 * protections.
 *
 * <p>Isolation: {@link #cleanDatabase()} truncates all tables before each test through a
 * superuser connection with triggers disabled ({@code session_replication_role = replica}),
 * because the ledger tables are append-only and the app role may not touch them.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String DB_NAME = "wallet";
    protected static final String SUPERUSER = "postgres";
    protected static final String SUPERUSER_PASSWORD = "postgres_test";
    protected static final String OWNER_USER = "wallet_owner";
    protected static final String OWNER_PASSWORD = "owner_test";
    protected static final String APP_USER = "wallet_app";
    protected static final String APP_PASSWORD = "app_test";

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName(DB_NAME)
                    .withUsername(SUPERUSER)
                    .withPassword(SUPERUSER_PASSWORD)
                    // Several Spring test contexts (each with its own pool) share this one container.
                    .withCommand("postgres", "-c", "max_connections=300")
                    .withEnv("DB_OWNER_USER", OWNER_USER)
                    .withEnv("DB_OWNER_PASSWORD", OWNER_PASSWORD)
                    .withEnv("DB_APP_USER", APP_USER)
                    .withEnv("DB_APP_PASSWORD", APP_PASSWORD)
                    .withCopyFileToContainer(
                            MountableFile.forHostPath(rolesInitScript(), 0755),
                            "/docker-entrypoint-initdb.d/01-roles.sh");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_USER);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", () -> OWNER_USER);
        registry.add("spring.flyway.password", () -> OWNER_PASSWORD);
        registry.add("spring.flyway.placeholders.app_role", () -> APP_USER);
    }

    @BeforeEach
    void cleanDatabase() throws SQLException {
        try (Connection conn = superuserConnection(); Statement st = conn.createStatement()) {
            // accounts and users are emptied with DELETE below, so the system accounts
            // seeded by V3 survive between tests.
            List<String> tables = new ArrayList<>();
            try (var rs = st.executeQuery("""
                    SELECT tablename FROM pg_tables
                    WHERE schemaname = 'public'
                      AND tablename NOT IN ('flyway_schema_history', 'accounts', 'users')
                    """)) {
                while (rs.next()) {
                    tables.add('"' + rs.getString(1) + '"');
                }
            }
            st.execute("SET session_replication_role = replica");
            st.execute("TRUNCATE " + String.join(", ", tables) + " RESTART IDENTITY");
            st.execute("DELETE FROM accounts WHERE type <> 'SYSTEM'");
            st.execute("DELETE FROM users");
            st.execute("SET session_replication_role = origin");
        }
    }

    /** Superuser: only for test plumbing (cleanup, injecting corruption). Never used by the app. */
    protected static Connection superuserConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), SUPERUSER, SUPERUSER_PASSWORD);
    }

    /** Schema owner: has every table privilege, but triggers still apply to it. */
    protected static Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), OWNER_USER, OWNER_PASSWORD);
    }

    /** The same least-privilege role the application uses. */
    protected static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), APP_USER, APP_PASSWORD);
    }

    // Maven runs tests from backend/, some IDEs from the repo root.
    private static Path rolesInitScript() {
        return Stream.of("../docker/postgres/init/01-roles.sh", "docker/postgres/init/01-roles.sh")
                .map(Path::of)
                .filter(Files::exists)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot find docker/postgres/init/01-roles.sh from " + Path.of("").toAbsolutePath()));
    }
}
