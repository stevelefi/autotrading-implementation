package com.autotrading.services.ibkr;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import com.autotrading.libs.idempotency.InMemoryIdempotencyService;
import com.autotrading.libs.kafka.DirectKafkaPublisher;
import com.autotrading.services.ibkr.db.BrokerOrderRepository;
import com.autotrading.services.ibkr.db.ExecutionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import com.autotrading.command.v1.RequestContext;
import com.autotrading.command.v1.SubmitOrderRequest;
import com.autotrading.services.ibkr.api.IbkrSmokeController;
import com.autotrading.services.ibkr.core.BrokerConnectorEngine;

class IbkrSmokeControllerTest {

    @Test
    void statsReturnsZeroSubmitCountInitially() {
        IbkrSmokeController controller = new IbkrSmokeController(
                new BrokerConnectorEngine(new InMemoryIdempotencyService(),
                        mock(BrokerOrderRepository.class), mock(ExecutionRepository.class),
                        mock(DirectKafkaPublisher.class), new ObjectMapper()));

        Map<String, Object> stats = controller.stats();

        assertThat(stats.get("total_submit_count")).isEqualTo(0);
        assertThat(stats).containsKey("timestamp_utc");
    }

    @Test
    void statsReflectsTotalSubmitCountAfterSubmissions() {
        BrokerConnectorEngine engine = new BrokerConnectorEngine(new InMemoryIdempotencyService(),
                mock(BrokerOrderRepository.class), mock(ExecutionRepository.class),
                mock(DirectKafkaPublisher.class), new ObjectMapper());
        IbkrSmokeController controller = new IbkrSmokeController(engine);

        engine.submit(SubmitOrderRequest.newBuilder()
                .setRequestContext(RequestContext.newBuilder()
                        .setTraceId("trc-1")
                        .setRequestId("req-1")
                        .setIdempotencyKey("idem-smoke-1")
                        .setPrincipalId("svc-order")
                        .build())
                .setOrderIntentId("ord-smoke-1")
                .setSide("BUY").setQty(1).setOrderType("MKT").setTimeInForce("DAY")
                .setSubmissionDeadlineMs(60000)
                .build());

        Map<String, Object> stats = controller.stats();
        assertThat(stats.get("total_submit_count")).isEqualTo(1);
    }
}
