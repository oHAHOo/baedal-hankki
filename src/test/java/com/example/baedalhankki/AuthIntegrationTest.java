package com.example.baedalhankki;

import com.example.baedalhankki.member.entity.Member;
import com.example.baedalhankki.member.entity.MemberRole;
import com.example.baedalhankki.member.repository.MemberRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.seed=false"
})
@AutoConfigureMockMvc
class AuthIntegrationTest {
    private final MockMvc mvc;
    private final ObjectMapper json;
    private final MemberRepository members;
    private final PasswordEncoder passwords;
    private final JdbcTemplate jdbc;

    @Autowired
    AuthIntegrationTest(MockMvc mvc, ObjectMapper json, MemberRepository members,
            PasswordEncoder passwords, JdbcTemplate jdbc) {
        this.mvc = mvc;
        this.json = json;
        this.members = members;
        this.passwords = passwords;
        this.jdbc = jdbc;
    }

    @Test
    void 가입은_고객과_해시를_저장하고_로그인_전에는_세션을_발급하지_않는다() throws Exception {
        ObjectNode request = signupRequest();
        String password = "  secret password  ";
        request.put("password", password);
        MvcResult signup = signup(request);
        assertEquals(201, signup.getResponse().getStatus());
        assertEquals("", signup.getResponse().getContentAsString());
        assertNull(signup.getRequest().getSession(false));
        assertNull(signup.getResponse().getHeader("Set-Cookie"));

        Member member = members.findByLoginId(request.get("loginId").asText()).orElseThrow();
        assertEquals(MemberRole.CUSTOMER, member.getRole());
        assertEquals("홍길동", member.getName());
        assertNotEquals(password, member.getPasswordHash());
        assertTrue(passwords.matches(password, member.getPasswordHash()));
        assertFalse(passwords.matches(password.trim(), member.getPasswordHash()));

        mvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        MvcResult login = login(request.get("loginId").asText(), password, new MockHttpSession());
        assertEquals(200, login.getResponse().getStatus());
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session);
        mvc.perform(get("/api/members/me").session(session))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"role\":\"CUSTOMER\"}"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.sessionId").doesNotExist());
    }

    @Test
    void 같은_아이디로_재가입하면_409를_반환한다() throws Exception {
        ObjectNode request = signupRequest();
        assertEquals(201, signup(request).getResponse().getStatus());
        MvcResult duplicate = signup(request);
        assertEquals(409, duplicate.getResponse().getStatus());
        assertEquals("LOGIN_ID_ALREADY_EXISTS", json.readTree(duplicate.getResponse().getContentAsString())
                .get("code").asText());
    }

    @Test
    void 같은_아이디의_동시_가입은_하나만_성공한다() throws Exception {
        String body = signupRequest().toString();
        String loginId = json.readTree(body).get("loginId").asText();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var requests = Stream.generate(() -> executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                        .andReturn();
            })).limit(2).toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            MvcResult first = requests.get(0).get(10, TimeUnit.SECONDS);
            MvcResult second = requests.get(1).get(10, TimeUnit.SECONDS);
            assertEquals(List.of(201, 409), Stream.of(first, second)
                    .map(result -> result.getResponse().getStatus()).sorted().toList());
            MvcResult duplicate = first.getResponse().getStatus() == 409 ? first : second;
            assertEquals("LOGIN_ID_ALREADY_EXISTS", json.readTree(duplicate.getResponse().getContentAsString())
                    .get("code").asText());
        } finally {
            start.countDown();
        }
        assertEquals(1L, jdbc.queryForObject("select count(*) from member where login_id = ?", Long.class, loginId));
    }

    @ParameterizedTest
    @MethodSource("invalidSignupValues")
    void 가입_필드의_누락과_경계값을_검증한다(String field, Object value) throws Exception {
        ObjectNode request = signupRequest();
        request.set(field, json.valueToTree(value));
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value(field));
        assertTrue(members.findByLoginId(request.get("loginId").asText()).isEmpty());
    }

    static Stream<Arguments> invalidSignupValues() {
        return Stream.of(
                Arguments.of("name", null), Arguments.of("name", ""), Arguments.of("name", " "),
                Arguments.of("name", "가".repeat(101)),
                Arguments.of("loginId", null), Arguments.of("loginId", ""), Arguments.of("loginId", " "),
                Arguments.of("loginId", "abc"), Arguments.of("loginId", "a".repeat(51)),
                Arguments.of("loginId", "Uppercase"), Arguments.of("loginId", "한글아이디"),
                Arguments.of("loginId", "user-id"), Arguments.of("loginId", " user01"),
                Arguments.of("password", null), Arguments.of("password", ""), Arguments.of("password", " ".repeat(8)),
                Arguments.of("password", "a".repeat(7)), Arguments.of("password", "a".repeat(73)),
                Arguments.of("password", "가".repeat(25)), Arguments.of("password", "😀".repeat(7)),
                Arguments.of("password", "😀".repeat(19)));
    }

    @ParameterizedTest
    @MethodSource("validSignupBoundaries")
    void 가입_길이의_허용_경계값을_저장한다(String name, int loginIdLength, String password) throws Exception {
        ObjectNode request = signupRequest();
        request.put("name", name);
        request.put("loginId", "u" + UUID.randomUUID().toString().replace("-", "").repeat(2)
                .substring(0, loginIdLength - 1));
        request.put("password", password);
        assertEquals(201, signup(request).getResponse().getStatus());
        Member member = members.findByLoginId(request.get("loginId").asText()).orElseThrow();
        assertTrue(passwords.matches(password, member.getPasswordHash()));
    }

    static Stream<Arguments> validSignupBoundaries() {
        return Stream.of(
                Arguments.of("가", 4, "a".repeat(8)),
                Arguments.of("가".repeat(100), 50, "a".repeat(72)),
                Arguments.of("고객", 36, "가".repeat(24)),
                Arguments.of("고객", 36, "😀".repeat(18)));
    }

    @ParameterizedTest
    @MethodSource("unknownSignupFields")
    void 역할과_비밀번호_확인_등_미정의_가입_필드는_거절한다(String field) throws Exception {
        ObjectNode request = signupRequest();
        request.put(field, "OWNER");
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertFalse(members.existsByLoginId(request.get("loginId").asText()));
    }

    static Stream<String> unknownSignupFields() {
        return Stream.of("role", "passwordConfirm", "unknown");
    }

    @ParameterizedTest
    @EnumSource(MemberRole.class)
    void 기존_계정은_역할을_반환하고_세션_ID를_변경한다(MemberRole role) throws Exception {
        String loginId = "Legacy-" + UUID.randomUUID();
        members.save(Member.create(loginId, passwords.encode("legacy"), "기존 계정", role));
        var session = new MockHttpSession();
        String previousId = session.getId();
        MvcResult result = login(loginId, "legacy", session);
        assertEquals(200, result.getResponse().getStatus());
        assertEquals(json.readTree("{\"role\":\"" + role + "\"}"),
                json.readTree(result.getResponse().getContentAsString()));
        assertNotEquals(previousId, result.getRequest().getSession(false).getId());
        mvc.perform(get("/api/members/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value(role.name()));
    }

    @Test
    void 없는_아이디와_틀린_비밀번호의_응답은_같다() throws Exception {
        ObjectNode request = signupRequest();
        assertEquals(201, signup(request).getResponse().getStatus());
        MvcResult missing = login("missing_" + UUID.randomUUID(), "wrong-password", new MockHttpSession());
        MvcResult wrong = login(request.get("loginId").asText(), "wrong-password", new MockHttpSession());
        assertEquals(401, missing.getResponse().getStatus());
        assertEquals(401, wrong.getResponse().getStatus());
        assertEquals(missing.getResponse().getContentAsString(), wrong.getResponse().getContentAsString());
        assertEquals("INVALID_CREDENTIALS", json.readTree(wrong.getResponse().getContentAsString()).get("code").asText());
    }

    @Test
    void 잘못된_JSON과_로그인_입력을_거절한다() throws Exception {
        for (String body : List.of("{", "{}", "{\"loginId\":\"user01\"}",
                "{\"loginId\":\"user01\",\"password\":\"password123!\",\"role\":\"OWNER\"}",
                "{\"loginId\":\"" + "a".repeat(51) + "\",\"password\":\"password123!\"}",
                "{\"loginId\":\"user01\",\"password\":\"" + "a".repeat(101) + "\"}")) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/members/me").cookie(new jakarta.servlet.http.Cookie("JSESSIONID", "expired")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private ObjectNode signupRequest() {
        return json.createObjectNode().put("name", "홍길동")
                .put("loginId", "user_" + UUID.randomUUID().toString().replace("-", ""))
                .put("password", "password123!");
    }

    @ParameterizedTest
    @MethodSource("nonStringSignupValues")
    void 문자열_필드에_숫자나_불리언을_보내면_거절한다(String field, Object value) throws Exception {
        ObjectNode request = signupRequest();
        request.set(field, json.valueToTree(value));
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    static Stream<Arguments> nonStringSignupValues() {
        return Stream.of(Arguments.of("name", 1234), Arguments.of("name", true), Arguments.of("name", 1.25),
                Arguments.of("loginId", 1234), Arguments.of("password", 12345678));
    }

    @Test
    void 비밀번호의_72바이트_이후에_문자가_붙으면_로그인을_거절한다() throws Exception {
        ObjectNode request = signupRequest().put("password", "a".repeat(72));
        assertEquals(201, signup(request).getResponse().getStatus());
        MvcResult result = login(request.get("loginId").asText(), "a".repeat(72) + "wrong", new MockHttpSession());
        assertEquals(401, result.getResponse().getStatus());
        assertEquals("INVALID_CREDENTIALS", json.readTree(result.getResponse().getContentAsString()).get("code").asText());
    }

    @Test
    void 잘못된_유니코드_비밀번호는_가입할_수_없다() throws Exception {
        String request = signupRequest().toString().replace("password123!", "\\uD800aaaaaaa");
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 문자열이_아닌_로그인_값은_400으로_거절한다() throws Exception {
        for (String request : List.of("{\"loginId\":1234,\"password\":\"password123!\"}",
                "{\"loginId\":\"user01\",\"password\":12345678}",
                "{\"loginId\":true,\"password\":\"password123!\"}")) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
    }

    @Test
    void 잘못된_유니코드_로그인은_대체문자_비밀번호와_일치하지_않는다() throws Exception {
        ObjectNode request = signupRequest().put("password", "?aaaaaaa");
        assertEquals(201, signup(request).getResponse().getStatus());
        String loginRequest = json.createObjectNode().put("loginId", request.get("loginId").asText())
                .put("password", "placeholder").toString().replace("placeholder", "\\uD800aaaaaaa");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginRequest))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    private MvcResult signup(ObjectNode request) throws Exception {
        return mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andReturn();
    }

    private MvcResult login(String loginId, String password, MockHttpSession session) throws Exception {
        String request = json.createObjectNode().put("loginId", loginId).put("password", password).toString();
        return mvc.perform(post("/api/auth/login").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(request)).andReturn();
    }
}
