package com.autotrading.services.risk;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.autotrading.services.risk.runtime.RiskRuntimeConfiguration;

/** Basic accessibility test — bean-level tests live in the runtime sub-package. */
class RiskRuntimeConfigurationAccessTest {

    @Test
    void configurationClassCanBeInstantiated() {
        assertThat(new RiskRuntimeConfiguration()).isNotNull();
    }
}
