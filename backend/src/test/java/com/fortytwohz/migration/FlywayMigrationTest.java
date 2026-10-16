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
        TestTime.now = null;
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
        return new SpringApplicationBuilder(FortytwohzApplication.class, TestTime.class)
                .web(web ? WebApplicationType.SERVLET : WebApplicationType.NONE)
                .run(args.toArray(String[]::new));
    }

    private String hash(String token) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private List<Map<String, Object>> snapshot(String table) {
        String columns = switch (table) {
            case "rooms" -> "id, invite_code, status, created_at, expires_at";
            case "shared_results" -> "id, room_id, share_token, matched_count, different_count, created_at";
            default -> "*";
        };
        return jdbc.queryForList("SELECT " + columns + " FROM " + table + " ORDER BY id");
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
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class));
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

    @Test void v1LegacyDatabaseRequiresExplicitBaselineAndPreservesAllData() throws Exception {
        // Reproduce the pre-V2 schema without creating a Flyway history table.
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V1__initial_schema.sql"))
                .execute(Objects.requireNonNull(jdbc.getDataSource()));
        // Legacy Hibernate names need not match V1 names; V2 must not rely on FK names.
        jdbc.execute("ALTER TABLE participants RENAME CONSTRAINT fk_participants_room TO legacy_participant_room");
        jdbc.execute("ALTER TABLE answers RENAME CONSTRAINT fk_answers_room TO legacy_answer_room");
        jdbc.execute("ALTER TABLE answers RENAME CONSTRAINT fk_answers_participant TO legacy_answer_participant");
        jdbc.execute("ALTER TABLE shared_results RENAME CONSTRAINT fk_shared_results_room TO legacy_share_room");
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
        assertEquals(1, flyway().migrate().migrationsExecuted);
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
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='3'", Integer.class));
        assertEquals(0, flyway().migrate().migrationsExecuted);
        assertTrue(flyway().validateWithResult().validationSuccessful);
    }

    @Test void modifiedAppliedMigrationFailsValidation(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        flyway().migrate();
        String original = new ClassPathResource("db/migration/V1__initial_schema.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("V1__initial_schema.sql"), original.replace("varchar(12)", "varchar(13)"));
        Files.writeString(directory.resolve("V2__add_share_lifecycle.sql"),
                new ClassPathResource("db/migration/V2__add_share_lifecycle.sql")
                        .getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
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
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM shared_results WHERE expires_at=created_at+interval '168 hours'", Integer.class));
            context.getBean(com.fortytwohz.room.SharedResultService.class).revokeShare(code, "Bearer " + second);
            request(base + "/shared-results/" + token, "GET", null, null, 404);
            assertEquals(true, request(room + "/results", "GET", null, first, 200).read("$.allComplete"));
            assertEquals(true, request(room + "/results", "GET", null, second, 200).read("$.allComplete"));
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
    @Test void migratedShareExpiresAtOriginalSevenDayBoundaryWithoutRenewal() throws Exception {
        try (var context = app(true, "--spring.flyway.enabled=true")) {
            var roomService = context.getBean(com.fortytwohz.room.RoomService.class);
            var sharing = context.getBean(com.fortytwohz.room.SharedResultService.class);
            var room = roomService.create("나");
            String token = "7".repeat(64);
            jdbc.update("INSERT INTO shared_results(id,room_id,share_token,matched_count,different_count,created_at,expires_at) SELECT ?,id,?,12,0,created_at,created_at+interval '168 hours' FROM rooms WHERE invite_code=?", UUID.randomUUID(), token, room.roomCode());
            var created = jdbc.queryForObject("SELECT created_at FROM shared_results", java.time.OffsetDateTime.class).toInstant();
            TestTime.now = created.plusSeconds(604800).minusNanos(1);
            assertEquals(12, sharing.getSharedResult(token).matched());
            TestTime.now = created.plusSeconds(604800);
            assertStatus(410, () -> sharing.getSharedResult(token));
            try (var client = HttpClient.newHttpClient()) {
                var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                        + context.getEnvironment().getProperty("local.server.port") + "/api/v1/shared-results/" + token))
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(410, response.statusCode());
                assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            }
            assertStatus(410, () -> sharing.createShare(room.roomCode(), "Bearer " + room.participantToken()));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM shared_results", Integer.class));
            assertEquals(created, jdbc.queryForObject("SELECT created_at FROM shared_results", java.time.OffsetDateTime.class).toInstant());
        }
    }

    @Test void eitherParticipantCanRevokeIdempotentlyAndPrivateResultsRemainAvailable() throws Exception {
        try (var context = app(true, "--spring.flyway.enabled=true")) {
            String base = "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port") + "/api/v1";
            var rooms = context.getBean(com.fortytwohz.room.RoomService.class);
            var share = context.getBean(com.fortytwohz.room.SharedResultService.class);
            var first = rooms.create("나");
            var second = rooms.join(first.roomCode(), "상대");
            String token = seedShare(first.roomCode());
            assertStatus(401, () -> share.revokeShare(first.roomCode(), "Bearer wrong"));
            assertEquals(12, share.getSharedResult(token).matched());
            for (String bearer : List.of(second.participantToken(), first.participantToken())) {
                try (var client = HttpClient.newHttpClient()) {
                    var response = client.send(HttpRequest.newBuilder(URI.create(base + "/rooms/" + first.roomCode() + "/share"))
                            .header("Authorization", "Bearer " + bearer).DELETE().build(), HttpResponse.BodyHandlers.ofString());
                    assertEquals(204, response.statusCode());
                    assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
                }
            }
            assertStatus(404, () -> share.getSharedResult(token));
            assertStatus(409, () -> share.createShare(first.roomCode(), "Bearer " + first.participantToken()));
            assertStatus(409, () -> share.createShare(first.roomCode(), "Bearer " + second.participantToken()));
            assertFalse(rooms.results(first.roomCode(), "Bearer " + first.participantToken()).allComplete());
            try (var client = HttpClient.newHttpClient()) {
                var response = client.send(HttpRequest.newBuilder(URI.create(base + "/shared-results/" + token)).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(404, response.statusCode());
                assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
                for (String origin : List.of("https://42-hz.vercel.app", "http://localhost:3000", "https://untrusted.example")) {
                    var cors = client.send(HttpRequest.newBuilder(URI.create(base + "/rooms/" + first.roomCode() + "/share"))
                            .header("Origin", origin).header("Access-Control-Request-Method", "DELETE")
                            .header("Access-Control-Request-Headers", "Authorization").method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
                    assertEquals(origin.contains("untrusted") ? 403 : 200, cors.statusCode());
                    if (!origin.contains("untrusted")) assertEquals(origin, cors.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
                }
            }
        }
    }

    @Test void revokeWithoutSharePreventsFirstCreationAndExpiredRoomCannotRevoke() {
        try (var context = app(false, "--spring.flyway.enabled=true")) {
            var rooms = context.getBean(com.fortytwohz.room.RoomService.class);
            var share = context.getBean(com.fortytwohz.room.SharedResultService.class);
            var room = rooms.create("나");
            share.revokeShare(room.roomCode(), "Bearer " + room.participantToken());
            assertStatus(409, () -> share.createShare(room.roomCode(), "Bearer " + room.participantToken()));
            TestTime.now = jdbc.queryForObject("SELECT expires_at FROM rooms", java.time.OffsetDateTime.class).toInstant();
            assertStatus(410, () -> share.revokeShare(room.roomCode(), "Bearer " + room.participantToken()));
            assertStatus(410, () -> rooms.results(room.roomCode(), "Bearer " + room.participantToken()));
        }
    }

    @Test void revokeDeletionFailureRollsBackRoomBlockAndTokenRemovalTogether() {
        try (var context = app(false, "--spring.flyway.enabled=true")) {
            var room = context.getBean(com.fortytwohz.room.RoomService.class).create("나");
            String token = seedShare(room.roomCode());
            jdbc.execute("CREATE TABLE revoke_failure_probe (share_id uuid REFERENCES shared_results(id))");
            jdbc.update("INSERT INTO revoke_failure_probe SELECT id FROM shared_results");
            var share = context.getBean(com.fortytwohz.room.SharedResultService.class);
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> share.revokeShare(room.roomCode(), "Bearer " + room.participantToken()));
            assertFalse(jdbc.queryForObject("SELECT sharing_blocked FROM rooms", Boolean.class));
            assertEquals(12, share.getSharedResult(token).matched());
        }
    }

    @Test void createAndRevokeSerializeOnTheSameRoomLockInBothOrders() throws Exception {
        try (var context = app(false, "--spring.flyway.enabled=true")) {
            var rooms = context.getBean(com.fortytwohz.room.RoomService.class);
            var share = context.getBean(com.fortytwohz.room.SharedResultService.class);
            var tx = new org.springframework.transaction.support.TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
            for (boolean revokeFirst : List.of(false, true)) {
                var room = rooms.create("나");
                rooms.join(room.roomCode(), "상대");
                var token = new java.util.concurrent.atomic.AtomicReference<String>();
                if (revokeFirst) token.set(seedShare(room.roomCode()));
                else jdbc.update("INSERT INTO answers(id,room_id,participant_id,question_index,answer,submitted_at) SELECT gen_random_uuid(),p.room_id,p.id,q,'같음',r.created_at FROM participants p JOIN rooms r ON r.id=p.room_id CROSS JOIN generate_series(0,12) q WHERE r.invite_code=?", room.roomCode());
                var held = new java.util.concurrent.CountDownLatch(1);
                var release = new java.util.concurrent.CountDownLatch(1);
                try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                    var first = executor.submit(() -> tx.execute(status -> {
                        if (revokeFirst) share.revokeShare(room.roomCode(), "Bearer " + room.participantToken());
                        else token.set(share.createShare(room.roomCode(), "Bearer " + room.participantToken()).shareToken());
                        held.countDown();
                        try { if (!release.await(15, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("test lock timeout"); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                        return null;
                    }));
                    try {
                        assertTrue(held.await(15, java.util.concurrent.TimeUnit.SECONDS));
                        var second = executor.submit(() -> {
                            if (revokeFirst) assertStatus(409, () -> share.createShare(room.roomCode(), "Bearer " + room.participantToken()));
                            else share.revokeShare(room.roomCode(), "Bearer " + room.participantToken());
                        });
                        // Observe actual PostgreSQL blocking, not a timing guess about thread scheduling.
                        long until = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
                        while (jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND cardinality(pg_blocking_pids(pid))>0", Integer.class) == 0 && System.nanoTime() < until) Thread.sleep(10);
                        assertTrue(jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND cardinality(pg_blocking_pids(pid))>0", Integer.class) > 0);
                        assertFalse(second.isDone());
                        release.countDown();
                        first.get(15, java.util.concurrent.TimeUnit.SECONDS);
                        second.get(15, java.util.concurrent.TimeUnit.SECONDS);
                        // Fresh transaction starts after the revoke transaction has committed.
                        assertStatus(404, () -> share.getSharedResult(token.get()));
                        assertStatus(409, () -> share.createShare(room.roomCode(), "Bearer " + room.participantToken()));
                    } finally { release.countDown(); }
                }
            }
        }
    }

    @Test void v1ToV2BackfillsOriginalTimesAndQuarantinesOnlyInvalidRows() {
        Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).target("1").cleanDisabled(true).baselineOnMigrate(false).load().migrate();
        for (int i = 0; i < 4; i++) {
            String time = switch (i) { case 0 -> "now()-interval '8 days'"; case 1 -> "now()"; case 2 -> "now()+interval '1 day'"; default -> "'infinity'::timestamptz"; };
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO rooms VALUES (?,?,'READY'," + time + "," + time + "+interval '24 hours')", id, "TIME000" + i);
            jdbc.update("INSERT INTO shared_results VALUES (?,?,?,12,0," + time + ")", UUID.randomUUID(), id, Integer.toString(i).repeat(64));
        }
        var original = snapshot("shared_results");
        assertEquals(1, flyway().migrate().migrationsExecuted);
        assertEquals(original, snapshot("shared_results"));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM shared_results WHERE retention_review_required", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM rooms WHERE retention_review_required", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM shared_results WHERE expires_at=created_at+interval '168 hours' AND NOT retention_review_required", Integer.class));
        try (var context = app(false)) {
            var share = context.getBean(com.fortytwohz.room.SharedResultService.class);
            assertStatus(410, () -> share.getSharedResult("0".repeat(64)));
            assertEquals(12, share.getSharedResult("1".repeat(64)).matched());
            for (String bad : List.of("2", "3")) assertStatus(410, () -> share.getSharedResult(bad.repeat(64)));
        }
        assertEquals(0, flyway().migrate().migrationsExecuted);
    }

    private String seedShare(String code) {
        String token = UUID.randomUUID().toString().replace("-", "").repeat(2);
        jdbc.update("INSERT INTO shared_results(id,room_id,share_token,matched_count,different_count,created_at,expires_at) SELECT ?,id,?,12,0,created_at,created_at+interval '168 hours' FROM rooms WHERE invite_code=?", UUID.randomUUID(), token, code);
        return token;
    }

    private void assertStatus(int status, org.junit.jupiter.api.function.Executable action) {
        assertEquals(status, assertThrows(org.springframework.web.server.ResponseStatusException.class, action).getStatusCode().value());
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class TestTime {
        static volatile java.time.Instant now;
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        java.time.Clock controlledClock() {
            return new java.time.Clock() {
                public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
                public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
                public java.time.Instant instant() { return now == null ? java.time.Instant.now() : now; }
            };
        }
    }
}
