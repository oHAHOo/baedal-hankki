package com.example.baedalhankki;

import com.example.baedalhankki.refund.facade.RefundRecoveryWorker;
import com.example.baedalhankki.refund.config.RefundSettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:scheduler-test;DB_CLOSE_DELAY=-1",
        "app.refund.enabled=true", "app.refund.poll-interval=1h"})
@ActiveProfiles("test")
class RefundSchedulerConfigurationTest {
    private final RefundRecoveryWorker worker;
    private final RefundSettings settings;

    @Autowired
    RefundSchedulerConfigurationTest(RefundRecoveryWorker worker, RefundSettings settings) {
        this.worker = worker;
        this.settings = settings;
    }

    @Test
    void 설정을_활성화하면_복구_스케줄러가_등록된다() {
        assertNotNull(worker);
        assertEquals(Duration.ofHours(1), settings.pollInterval());
    }
}
