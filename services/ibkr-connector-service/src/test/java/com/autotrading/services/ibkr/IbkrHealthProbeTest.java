package com.autotrading.services.ibkr;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.web.client.ResourceAccessException;

import com.autotrading.services.ibkr.client.BrokerStatus;
import com.autotrading.services.ibkr.client.IbkrHealthProbe;
import com.autotrading.services.ibkr.client.IbkrRestClient;

class IbkrHealthProbeTest {

  @Test
  void isUp_afterSuccessfulTickle() {
    IbkrRestClient client = mock(IbkrRestClient.class);
    var authStatus = new IbkrRestClient.AuthStatus(true, true, false, "");
    var iserver = new IbkrRestClient.IServerStatus(authStatus);
    when(client.tickle()).thenReturn(new IbkrRestClient.TickleResponse(iserver, "session-1"));

    IbkrHealthProbe probe = new IbkrHealthProbe(client, 30_000, false);
    probe.runTickle();

    assertThat(probe.getStatus()).isEqualTo(BrokerStatus.UP);
    assertThat(probe.isUp()).isTrue();
  }

  @Test
  void isDown_afterTickleReturnsUnauthenticated() {
    IbkrRestClient client = mock(IbkrRestClient.class);
    var authStatus = new IbkrRestClient.AuthStatus(false, false, false, "session expired");
    var iserver = new IbkrRestClient.IServerStatus(authStatus);
    when(client.tickle()).thenReturn(new IbkrRestClient.TickleResponse(iserver, "session-1"));

    IbkrHealthProbe probe = new IbkrHealthProbe(client, 30_000, false);
    probe.runTickle();

    assertThat(probe.getStatus()).isEqualTo(BrokerStatus.DOWN);
    assertThat(probe.isUp()).isFalse();
  }

  @Test
  void isDown_afterTickleThrowsNetworkError() {
    IbkrRestClient client = mock(IbkrRestClient.class);
    when(client.tickle()).thenThrow(new ResourceAccessException("Connection refused"));

    IbkrHealthProbe probe = new IbkrHealthProbe(client, 30_000, false);
    probe.runTickle();

    assertThat(probe.getStatus()).isEqualTo(BrokerStatus.DOWN);
    assertThat(probe.isUp()).isFalse();
  }

  @Test
  void simulatorMode_unknownStatus_treatedAsUp() {
    IbkrRestClient client = mock(IbkrRestClient.class);
    // Probe never called — status stays UNKNOWN
    IbkrHealthProbe probe = new IbkrHealthProbe(client, 30_000, true);

    assertThat(probe.getStatus()).isEqualTo(BrokerStatus.UNKNOWN);
    assertThat(probe.isUp()).isTrue(); // UNKNOWN is UP in simulator mode
  }

  @Test
  void simulatorMode_downStatus_notUp() {
    IbkrRestClient client = mock(IbkrRestClient.class);
    when(client.tickle()).thenThrow(new RuntimeException("sim down"));

    IbkrHealthProbe probe = new IbkrHealthProbe(client, 30_000, true);
    probe.runTickle(); // transitions to DOWN

    assertThat(probe.getStatus()).isEqualTo(BrokerStatus.DOWN);
    assertThat(probe.isUp()).isFalse(); // even in simulator mode, explicit DOWN = not up
  }

  @Test
  void transitions_upThenDownThenUp() {
    IbkrRestClient client = mock(IbkrRestClient.class);
    var authStatus = new IbkrRestClient.AuthStatus(true, true, false, "");
    var iserver = new IbkrRestClient.IServerStatus(authStatus);
    var upResponse = new IbkrRestClient.TickleResponse(iserver, "session-1");
    var downAuthStatus = new IbkrRestClient.AuthStatus(false, false, false, "");
    var downIserver = new IbkrRestClient.IServerStatus(downAuthStatus);
    var downResponse = new IbkrRestClient.TickleResponse(downIserver, "session-1");

    when(client.tickle())
        .thenReturn(upResponse)
        .thenReturn(downResponse)
        .thenReturn(upResponse);

    IbkrHealthProbe probe = new IbkrHealthProbe(client, 30_000, false);

    probe.runTickle();
    assertThat(probe.getStatus()).isEqualTo(BrokerStatus.UP);

    probe.runTickle();
    assertThat(probe.getStatus()).isEqualTo(BrokerStatus.DOWN);

    probe.runTickle();
    assertThat(probe.getStatus()).isEqualTo(BrokerStatus.UP);
  }
}
