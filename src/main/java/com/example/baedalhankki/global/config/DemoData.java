package com.example.baedalhankki.global.config;

import lombok.RequiredArgsConstructor;

import com.example.baedalhankki.member.entity.Member;
import com.example.baedalhankki.member.entity.MemberRole;
import com.example.baedalhankki.member.repository.MemberRepository;
import com.example.baedalhankki.menu.entity.Menu;
import com.example.baedalhankki.menu.repository.MenuRepository;
import com.example.baedalhankki.restaurant.entity.Restaurant;
import com.example.baedalhankki.restaurant.repository.RestaurantRepository;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.seed", havingValue = "true")
@RequiredArgsConstructor
public class DemoData implements CommandLineRunner {
    private final MemberRepository members;
    private final RestaurantRepository restaurants;
    private final MenuRepository menus;
    private final PasswordEncoder encoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (members.findByLoginId("customer01").isEmpty())
            members.save(
                    Member.create(
                            "customer01",
                            encoder.encode("customer123!"),
                            "고객",
                            MemberRole.CUSTOMER));
        if (members.findByLoginId("owner01").isEmpty())
            members.save(
                    Member.create("owner01", encoder.encode("owner123!"), "사장님", MemberRole.OWNER));
        if (restaurants.count() == 0) {
            var restaurant = restaurants.save(Restaurant.create("한끼식당", 3000));
            menus.save(Menu.create(restaurant.getId(), "제육덮밥", 9000));
            menus.save(Menu.create(restaurant.getId(), "김치찌개", 8000));
            menus.save(Menu.create(restaurant.getId(), "비빔밥", 8500));
            menus.save(Menu.create(restaurant.getId(), "계란말이", 6000));
        }
    }
}
