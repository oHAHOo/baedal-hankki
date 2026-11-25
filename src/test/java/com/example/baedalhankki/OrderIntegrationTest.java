package com.example.baedalhankki;

import com.example.baedalhankki.member.entity.Member;
import com.example.baedalhankki.member.entity.MemberRole;
import com.example.baedalhankki.member.repository.MemberRepository;
import com.example.baedalhankki.menu.entity.Menu;
import com.example.baedalhankki.menu.repository.MenuRepository;
import com.example.baedalhankki.restaurant.entity.Restaurant;
import com.example.baedalhankki.restaurant.repository.RestaurantRepository;

import java.util.List;
import java.util.Map;
import jakarta.persistence.EntityManager;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
            "app.seed=true"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderIntegrationTest {
    private final MockMvc mvc;
    private final ObjectMapper json;
    private final MemberRepository members;
    private final PasswordEncoder passwords;
    private final RestaurantRepository restaurants;
    private final MenuRepository menus;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    @Autowired
    OrderIntegrationTest(MockMvc mvc, ObjectMapper json, MemberRepository members, PasswordEncoder passwords,
            RestaurantRepository restaurants, MenuRepository menus, JdbcTemplate jdbcTemplate,
            EntityManager entityManager) {
        this.mvc = mvc;
        this.json = json;
        this.members = members;
        this.passwords = passwords;
        this.restaurants = restaurants;
        this.menus = menus;
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
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
        mvc.perform(get("/api/orders").session(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders[0].restaurantName").value("한끼식당"));
        mvc.perform(get("/api/owner/orders").session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantName").value("한끼식당"))
                .andExpect(jsonPath("$.orders[0].restaurantName").doesNotExist());
        for (var request : java.util.List.of(get("/api/orders").session(customer),
                get("/api/owner/orders").session(owner))) {
            mvc.perform(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orders[0].orderId").value(orderId))
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
    @Transactional
    void 여러_음식점에_주문하고_사장님별로_접근을_분리한다() throws Exception {
        var secondOwner = members.save(
                Member.create("owner02", passwords.encode("password123!"), "두 번째 사장님", MemberRole.OWNER));
        var restaurant = restaurants.save(Restaurant.create("두 번째 식당", 1000, secondOwner));
        var menu = menus.save(Menu.create(restaurant.getId(), "국수", 7000));
        var customerSession = login("customer01", "customer123!");
        var firstOwnerSession = login("owner01", "owner123!");
        var secondOwnerSession = login("owner02", "password123!");
        long firstOrderId = order(customerSession);
        String request = json.writeValueAsString(Map.of(
                "restaurantId", restaurant.getId(),
                "items", List.of(Map.of("menuId", menu.getId(), "quantity", 2)),
                "deliveryAddress", "서울시 마포구 한끼로 12",
                "addressDetail", "202호",
                "contactPhone", "01012345678"));
        var result = mvc.perform(post("/api/orders").session(customerSession)
                        .contentType("application/json").content(request))
                .andExpect(status().isCreated()).andReturn();
        long secondOrderId = json.readTree(result.getResponse().getContentAsString()).get("orderId").asLong();
        entityManager.flush();
        entityManager.clear();
        mvc.perform(get("/api/owner/orders").session(secondOwnerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantName").value("두 번째 식당"))
                .andExpect(jsonPath("$.orders.length()").value(1))
                .andExpect(jsonPath("$.orders[0].orderId").value(secondOrderId));
        mvc.perform(get("/api/orders/" + secondOrderId).session(customerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(15000));
        mvc.perform(get("/api/owner/orders/" + firstOrderId).session(secondOwnerSession))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/owner/orders/" + secondOrderId).session(firstOwnerSession))
                .andExpect(status().isNotFound());
        for (String action : List.of("accept", "dispatch", "complete")) {
            mvc.perform(patch("/api/owner/orders/" + secondOrderId + "/" + action).session(firstOwnerSession))
                    .andExpect(status().isNotFound());
            mvc.perform(patch("/api/owner/orders/" + secondOrderId + "/" + action).session(secondOwnerSession))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/orders").session(customerSession).contentType("application/json")
                        .content(request.replace("\"restaurantId\":" + restaurant.getId(), "\"restaurantId\":1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void 소유자_정보가_없는_기존_음식점은_사장님이_접근할_수_없다() throws Exception {
        var customer = login("customer01", "customer123!");
        var owner = login("owner01", "owner123!");
        long orderId = order(customer);
        jdbcTemplate.update("update restaurant set owner_id = null where id = ?", 1L);
        mvc.perform(get("/api/owner/orders").session(owner))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/owner/orders/" + orderId).session(owner))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/owner/orders/" + orderId + "/accept").session(owner))
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
