package com.example.baedalhankki;

import com.example.baedalhankki.member.entity.Member;
import com.example.baedalhankki.member.entity.MemberRole;
import com.example.baedalhankki.member.repository.MemberRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "app.seed=true"
        })
@AutoConfigureMockMvc
class OrderIntegrationTest {
    private final MockMvc mvc;
    private final ObjectMapper json;
    private final MemberRepository members;
    private final PasswordEncoder passwords;

    @Autowired
    OrderIntegrationTest(MockMvc mvc, ObjectMapper json, MemberRepository members, PasswordEncoder passwords) {
        this.mvc = mvc;
        this.json = json;
        this.members = members;
        this.passwords = passwords;
    }

    MockHttpSession login(String loginId, String password) throws Exception {
        return (MockHttpSession)
                mvc.perform(
                                post("/api/auth/login")
                                        .contentType("application/json")
                                        .content(
                                                json.writeValueAsString(
                                                        java.util.Map.of(
                                                                "loginId",
                                                                loginId,
                                                                "password",
                                                                password))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getRequest()
                        .getSession(false);
    }

    String body() {
        return """
{"restaurantId":1,"items":[{"menuId":1,"quantity":2},{"menuId":2,"quantity":1}],
"deliveryAddress":"서울시 마포구 한끼로 12","addressDetail":"101동 202호","contactPhone":"01012345678"}
""";
    }

    long order(MockHttpSession customer) throws Exception {
        var result =
                mvc.perform(
                                post("/api/orders")
                                        .session(customer)
                                        .contentType("application/json")
                                        .content(body()))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("PENDING"))
                        .andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("orderId").asLong();
    }

    @Test
    void 정상_주문의_금액과_상태를_양쪽에서_확인한다() throws Exception {
        var customer = login("customer01", "customer123!");
        var owner = login("owner01", "owner123!");
        long id = order(customer);
        mvc.perform(get("/api/orders/" + id).session(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(29000))
                .andExpect(jsonPath("$.itemsAmount").value(26000))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.contactPhone").value("010-1234-5678"));
        mvc.perform(get("/api/owner/orders/" + id).session(owner))
                .andExpect(jsonPath("$.totalAmount").value(29000));
        String[] actions = {"accept", "dispatch", "complete"};
        String[] states = {"COOKING", "DELIVERING", "COMPLETED"};
        for (int i = 0; i < actions.length; i++) {
            mvc.perform(patch("/api/owner/orders/" + id + "/" + actions[i]).session(owner))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(states[i]));
            mvc.perform(get("/api/orders/" + id).session(customer))
                    .andExpect(jsonPath("$.status").value(states[i]));
        }
    }

    @Test
    void 고객과_사장님_목록에_주문_요약이_표시된다() throws Exception {
        var customer = login("customer01", "customer123!");
        var owner = login("owner01", "owner123!");
        long orderId = order(customer);
        for (var request : java.util.List.of(get("/api/orders").session(customer),
                get("/api/owner/orders").session(owner))) {
            mvc.perform(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orders[0].orderId").value(orderId))
                    .andExpect(jsonPath("$.orders[0].restaurantName").value("한끼식당"))
                    .andExpect(jsonPath("$.orders[0].totalAmount").value(29000))
                    .andExpect(jsonPath("$.orders[0].items[0].quantity").value(2));
        }
    }

    @Test
    void 취소된_주문은_접수할_수_없다() throws Exception {
        var customer = login("customer01", "customer123!");
        var owner = login("owner01", "owner123!");
        long id = order(customer);
        mvc.perform(patch("/api/orders/" + id + "/cancel").session(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
        mvc.perform(patch("/api/owner/orders/" + id + "/accept").session(owner))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/owner/orders/" + id).session(owner))
                .andExpect(jsonPath("$.status").value("CANCELED"));
    }

    @Test
    void 접수된_주문은_취소할_수_없다() throws Exception {
        var customer = login("customer01", "customer123!");
        var owner = login("owner01", "owner123!");
        long id = order(customer);
        mvc.perform(patch("/api/owner/orders/" + id + "/accept").session(owner))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/orders/" + id + "/cancel").session(customer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELABLE"));
        mvc.perform(patch("/api/owner/orders/" + id + "/complete").session(owner))
                .andExpect(status().isConflict());
    }

    @Test
    void 미인증과_다른_역할과_타인_주문을_차단한다() throws Exception {
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        var customer = login("customer01", "customer123!");
        var owner = login("owner01", "owner123!");
        long id = order(customer);
        mvc.perform(get("/api/owner/orders").session(customer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/orders").session(owner)).andExpect(status().isForbidden());
        members.save(
                Member.create(
                        "other", passwords.encode("password123!"), "다른 고객", MemberRole.CUSTOMER));
        var other = login("other", "password123!");
        mvc.perform(get("/api/orders/" + id).session(other)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/orders/" + id + "/cancel").session(other))
                .andExpect(status().isNotFound());
    }

    @Test
    void 잘못된_입력과_중복메뉴와_금액_초과를_거절한다() throws Exception {
        var customer = login("customer01", "customer123!");
        for (String invalid :
                new String[] {
                    body().replace("\"quantity\":2", "\"quantity\":0"),
                    body().replace("\"menuId\":2", "\"menuId\":1"),
                    body().replace("\"quantity\":2", "\"quantity\":2147483647"),
                    body().replace("01012345678", "123")
                }) {
            mvc.perform(
                            post("/api/orders")
                                    .session(customer)
                                    .contentType("application/json")
                                    .content(invalid))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/orders/not-a-number").session(customer))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/restaurants/1/menus").session(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.menus.length()").value(4));
        mvc.perform(get("/api/members/me").session(customer))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
        mvc.perform(
                        post("/api/auth/login")
                                .contentType("application/json")
                                .content("{\"loginId\":\"customer01\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }
}
