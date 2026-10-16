package com.fortytwohz.migration;

import com.fortytwohz.FortytwohzApplication;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;

/** All JDBC endpoints are obtained from a newly created container. No external DB configuration is used. */
class FlywayMigrationTest {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("migration_verification").withUsername("migration_test").withPassword("isolated_test_only");
    private String schema;
    private String url;
    private JdbcTemplate jdbc;

    @BeforeAll static void startIsolatedDatabase() { POSTGRES.start(); }
    @AfterAll static void stopIsolatedDatabase() { POSTGRES.stop(); }

    @BeforeEach void createFreshSchema() throws Exception {
        schema = "test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        url = POSTGRES.getJdbcUrl() + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema;
        jdbc = new JdbcTemplate(new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    private Flyway flyway(String... locations) {
        return Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).baselineVersion("1")
                .baselineOnMigrate(false).cleanDisabled(true)
                .locations(locations.length == 0 ? new String[]{"classpath:db/migration"} : locations).load();
    }

    private ConfigurableApplicationContext app(boolean web, String... extra) {
        List<String> args = new ArrayList<>(List.of(
                "--spring.config.location=classpath:/application.yml",
                "--spring.profiles.active=migration-test",
                "--spring.datasource.url=" + url,
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.flyway.schemas=" + schema,
                "--spring.flyway.default-schema=" + schema,
                "--spring.jpa.properties.hibernate.default_schema=" + schema,
                "--server.port=0", "--spring.main.banner-mode=off"));
        args.addAll(List.of(extra));
        return new SpringApplicationBuilder(FortytwohzApplication.class)
                .web(web ? WebApplicationType.SERVLET : WebApplicationType.NONE)
                .run(args.toArray(String[]::new));
    }

    private String hash(String token) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private List<Map<String, Object>> snapshot(String table) {
        return jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id");
    }
    private int tableCount(String name) {
        return jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=? AND table_name=?",
                Integer.class, schema, name);
    }

    @Test void emptyDatabaseMigratesBeforeHibernateValidateAndSecondRunIsNoop() {
        try (var context = app(false, "--spring.flyway.enabled=true")) {
            assertEquals("validate", context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"));
            assertEquals("false", context.getEnvironment().getProperty("spring.flyway.baseline-on-migrate"));
            assertEquals("true", context.getEnvironment().getProperty("spring.flyway.clean-disabled"));
            for (String table : List.of("rooms", "participants", "answers", "shared_results")) assertEquals(1, tableCount(table));
        }
        assertEquals(0, flyway().migrate().migrationsExecuted);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class));
        assertTrue(flyway().validateWithResult().validationSuccessful);
        assertEquals(5, jdbc.queryForObject("SELECT count(*) FROM information_schema.table_constraints WHERE table_schema=? AND constraint_type='UNIQUE'", Integer.class, schema));
        assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM information_schema.table_constraints WHERE table_schema=? AND constraint_type='FOREIGN KEY'", Integer.class, schema));
    }

    @Test void explicitLocalProfileMigratesOnlyTheSuppliedIsolatedDatabase() {
        try (var context = new SpringApplicationBuilder(FortytwohzApplication.class)
                .web(WebApplicationType.NONE).run(
                        "--spring.config.location=classpath:/application.yml",
                        "--spring.profiles.active=local",
                        "--LOCAL_DB_URL=" + url,
                        "--LOCAL_DB_USER=" + POSTGRES.getUsername(),
                        "--LOCAL_DB_PASSWORD=" + POSTGRES.getPassword(),
                        "--spring.flyway.schemas=" + schema,
                        "--spring.flyway.default-schema=" + schema,
                        "--spring.jpa.properties.hibernate.default_schema=" + schema,
                        "--spring.main.banner-mode=off")) {
            var migration = context.getBean(Flyway.class);
            assertFalse(migration.getConfiguration().isBaselineOnMigrate());
            assertTrue(migration.getConfiguration().isCleanDisabled());
            assertEquals(1, tableCount("rooms"));
            assertEquals(0, migration.migrate().migrationsExecuted);
        }
    }

    @Test void hibernateCreatedLegacyDatabaseRequiresExplicitBaselineAndPreservesAllData() throws Exception {
        // Only CREATE in a fresh, test-owned schema. Never use Hibernate create-drop or ddl-auto=create.
        try (var ignored = app(false, "--spring.flyway.enabled=false", "--spring.jpa.hibernate.ddl-auto=none",
                "--spring.jpa.properties.jakarta.persistence.schema-generation.database.action=create")) { }
        UUID room = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
        jdbc.update("INSERT INTO rooms(id,invite_code,status,created_at,expires_at) VALUES (?,?,'READY',now(),now()+interval '1 day')", room, "LEGACY01");
        jdbc.update("INSERT INTO participants(id,room_id,nickname,token_hash) VALUES (?,?,?,?)", first, room, "나", hash("legacy-first"));
        jdbc.update("INSERT INTO participants(id,room_id,nickname,token_hash) VALUES (?,?,?,?)", second, room, "상대", hash("legacy-second"));
        for (int i = 0; i < 13; i++) {
            for (UUID participant : List.of(first, second))
                jdbc.update("INSERT INTO answers(id,room_id,participant_id,question_index,answer,submitted_at) VALUES (?,?,?,?,?,now())", UUID.randomUUID(), room, participant, i, "기존 답변 " + i);
        }
        jdbc.update("INSERT INTO shared_results(id,room_id,share_token,matched_count,different_count,created_at) VALUES (?,?,?,12,0,now())", UUID.randomUUID(), room, "3".repeat(64));
        Map<String, List<Map<String, Object>>> before = new LinkedHashMap<>();
        for (String table : List.of("rooms", "participants", "answers", "shared_results")) before.put(table, snapshot(table));
        assertThrows(FlywayException.class, () -> flyway().migrate());
        assertEquals(0, tableCount("flyway_schema_history"));
        flyway().baseline();
        assertEquals(0, flyway().migrate().migrationsExecuted);
        assertEquals("BASELINE", jdbc.queryForObject("SELECT type FROM flyway_schema_history WHERE version='1'", String.class));
        try (var context = app(false, "--spring.flyway.enabled=true")) {
            var shared = context.getBean(com.fortytwohz.room.SharedResultService.class).getSharedResult("3".repeat(64));
            assertEquals(12, shared.matched());
            assertTrue(context.getBean(com.fortytwohz.room.RoomService.class)
                    .results("LEGACY01", "Bearer legacy-first").allComplete());
        }
        before.forEach((table, rows) -> assertEquals(rows, snapshot(table), table + " data changed"));
        assertEquals(26, snapshot("answers").size());
    }

    @Test void failedTransactionalMigrationRollsBackAndCanRetryWithoutRepair() {
        flyway().migrate();
        Flyway broken = flyway("classpath:db/migration", "classpath:db/failing");
        assertThrows(FlywayException.class, broken::migrate);
        assertEquals(0, tableCount("migration_rollback_probe"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='2'", Integer.class));
        assertEquals(0, flyway().migrate().migrationsExecuted);
        assertTrue(flyway().validateWithResult().validationSuccessful);
    }

    @Test void modifiedAppliedMigrationFailsValidation(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        flyway().migrate();
        String original = new ClassPathResource("db/migration/V1__initial_schema.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("V1__initial_schema.sql"), original.replace("varchar(12)", "varchar(13)"));
        assertThrows(FlywayException.class, () -> flyway("filesystem:" + directory).validate());
        assertTrue(flyway().validateWithResult().validationSuccessful);
    }

    @Test void productionDefaultDoesNotMigrateAndEmptyDatabaseFailsValidation() {
        Exception failure = assertThrows(Exception.class, () -> { try (var ignored = app(false)) { } });
        assertTrue(causes(failure).contains("Schema validation: missing table"), causes(failure));
        assertEquals(0, tableCount("rooms"));
        assertEquals(0, tableCount("flyway_schema_history"));
    }

    @Test void hibernateAcceptsWidenedIntegerSoValidationDoesNotProveExactSchemaEquality() {
        flyway().migrate();
        jdbc.execute("ALTER TABLE answers ALTER COLUMN question_index TYPE bigint");
        try (var ignored = app(false)) { }
        assertEquals("bigint", jdbc.queryForObject("SELECT data_type FROM information_schema.columns WHERE table_schema=? AND table_name='answers' AND column_name='question_index'", String.class, schema));
    }

    @Test void hibernateValidateRejectsIncompatibleColumnWithoutChangingIt() {
        flyway().migrate();
        jdbc.execute("ALTER TABLE answers ALTER COLUMN question_index TYPE text USING question_index::text");
        Exception failure = assertThrows(Exception.class, () -> { try (var ignored = app(false)) { } });
        assertTrue(causes(failure).contains("Schema validation: wrong column type"), causes(failure));
        assertEquals("text", jdbc.queryForObject("SELECT data_type FROM information_schema.columns WHERE table_schema=? AND table_name='answers' AND column_name='question_index'", String.class, schema));
    }

    private String causes(Throwable error) {
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = error; cause != null; cause = cause.getCause()) messages.append(cause.getMessage()).append('\n');
        return messages.toString();
    }

    @Test void fullTwoParticipantApiAndPublicShareWorkOnMigratedDatabase() throws Exception {
        try (var context = app(true, "--spring.flyway.enabled=true")) {
            String base = "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port") + "/api/v1";
            var created = request(base + "/rooms", "POST", "{\"nickname\":\"나\"}", null, 201);
            String code = created.read("$.roomCode"), first = created.read("$.participantToken");
            var joined = request(base + "/rooms/" + code + "/join", "POST", "{\"nickname\":\"상대\"}", null, 201);
            String second = joined.read("$.participantToken");
            String room = base + "/rooms/" + code;
            request(room + "/join", "POST", "{\"nickname\":\"세번째\"}", null, 409);
            assertEquals("READY", request(room, "GET", null, first, 200).read("$.status"));
            request(room + "/results", "GET", null, "wrong", 401);
            request(room + "/share", "POST", null, first, 409);
            request(room + "/answers", "POST", "{\"questionIndex\":0,\"answer\":\"임의 선택지\"}", first, 400);
            String[] choices = {"바로 다른 숙소를 찾는다", "하루 종일 같이 보내기", "바로 어떤 부분이 서운했는지 말한다", "근처를 함께 구경한다", "해결 방법을 같이 찾아주기", "평소 갖고 싶다고 했던 물건", "번갈아 가며 고른다", "바쁜가 보다 하고 기다린다", "맛집과 카페를 찾아다니기", "신나고 리듬감 있는 노래", "진솔한 이야기를 나눴기 때문에", "솔직하게 이야기하기", "기억하고 싶은 하루"};
            for (int i = 0; i < choices.length; i++) {
                String body = "{\"questionIndex\":" + i + ",\"answer\":\"" + choices[i] + "\"}";
                assertEquals(false, request(room + "/answers", "POST", body, first, 201).read("$.bothSubmitted"));
                assertNull(request(room + "/answers/" + i, "GET", null, second, 200).read("$.partnerAnswer"));
                assertEquals(true, request(room + "/answers", "POST", body, second, 201).read("$.bothSubmitted"));
                request(room + "/answers", "POST", body, first, 409);
            }
            assertEquals(true, request(room + "/results", "GET", null, first, 200).read("$.allComplete"));
            String token = request(room + "/share", "POST", null, first, 201).read("$.shareToken");
            assertEquals(token, request(room + "/share", "POST", null, second, 201).read("$.shareToken"));
            var publicResult = request(base + "/shared-results/" + token, "GET", null, null, 200);
            assertEquals(12, (int) publicResult.read("$.matched"));
            assertFalse(publicResult.jsonString().contains("기억하고 싶은 하루"));
        }
    }

    private com.jayway.jsonpath.DocumentContext request(String url, String method, String body, String token, int expected) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json")
                .timeout(java.time.Duration.ofSeconds(15))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        try (HttpClient client = HttpClient.newHttpClient()) {
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(expected, response.statusCode(), method + " " + url + ": " + response.body());
            return JsonPath.parse(response.body());
        }
    }
}
