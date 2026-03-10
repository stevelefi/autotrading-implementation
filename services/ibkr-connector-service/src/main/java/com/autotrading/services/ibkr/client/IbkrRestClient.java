package com.autotrading.services.ibkr.client;

import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Thin wrapper around the IBKR Client Portal REST API.
 *
 * <p>All methods throw {@link org.springframework.web.client.RestClientException} (unchecked) on
 * HTTP or network failure — callers are responsible for catching and logging.
 *
 * <p>In simulator mode the base URL points at WireMock, which serves the same REST contract.
 */
public class IbkrRestClient {

  private static final Logger log = LoggerFactory.getLogger(IbkrRestClient.class);

  private final RestClient restClient;
  private final Function<String, String> accountResolver;

  /**
   * Primary constructor — uses a {@link Function} to resolve the IBKR account ID from an
   * agent ID at call time, enabling per-agent sub-account routing.
   */
  public IbkrRestClient(String baseUrl, Function<String, String> accountResolver) {
    this.restClient = RestClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader("User-Agent", "autotrading-ibkr-connector/1.0")
        .build();
    this.accountResolver = accountResolver;
  }

  /**
   * Convenience constructor for tests and legacy code — uses a fixed account ID.
   */
  public IbkrRestClient(String baseUrl, String accountId) {
    this(baseUrl, ignored -> accountId);
  }

  // ------------------------------------------------------------------
  // Session keepalive + health
  // ------------------------------------------------------------------

  /**
   * {@code GET /v1/api/tickle} — keeps the CP session alive and returns auth status.
   */
  public TickleResponse tickle() {
    return restClient.get()
        .uri("/v1/api/tickle")
        .retrieve()
        .body(TickleResponse.class);
  }

  // ------------------------------------------------------------------
  // Order management
  // ------------------------------------------------------------------

  /**
   * {@code POST /v1/api/iserver/order/{accountId}} — submits a new order.
   *
   * @param agentId   the trading agent ID used to resolve the IBKR sub-account
   * @return list of order-status entries (IBKR returns an array)
   */
  @SuppressWarnings("unchecked")
  public List<IbkrOrderStatus> submitOrder(String agentId, String conid, String side, int quantity,
                                            String orderType, String tif,
                                            String instrumentId, String orderRef) {
    String resolvedAccount = accountResolver.apply(agentId);
    var body = new java.util.HashMap<String, Object>();
    body.put("conid", conid);
    body.put("side", side);
    body.put("quantity", quantity);
    body.put("orderType", orderType);
    body.put("tif", tif);
    body.put("outsideRTH", false);
    if (orderRef != null && !orderRef.isBlank()) {
      body.put("cOID", orderRef);
    }

    log.debug("ibkr submitOrder accountId={} side={} qty={} orderType={}", resolvedAccount, side, quantity, orderType);
    return restClient.post()
        .uri("/v1/api/iserver/order/{accountId}", resolvedAccount)
        .body(body)
        .retrieve()
        .body(new org.springframework.core.ParameterizedTypeReference<List<IbkrOrderStatus>>() {});
  }

  /**
   * {@code GET /v1/api/iserver/account/orders} — retrieves open/recent orders.
   */
  public AccountOrdersResponse getOrders() {
    return restClient.get()
        .uri("/v1/api/iserver/account/orders")
        .retrieve()
        .body(AccountOrdersResponse.class);
  }

  /**
   * {@code GET /v1/api/iserver/account/trades} — retrieves recent fills.
   */
  @SuppressWarnings("unchecked")
  public List<IbkrTrade> getTrades() {
    var result = restClient.get()
        .uri("/v1/api/iserver/account/trades")
        .retrieve()
        .body(new org.springframework.core.ParameterizedTypeReference<List<IbkrTrade>>() {});
    return result == null ? List.of() : result;
  }

  // ------------------------------------------------------------------
  // Response record types
  // ------------------------------------------------------------------

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record TickleResponse(
      @JsonProperty("iserver") IServerStatus iserver,
      @JsonProperty("session") String session) {

    public boolean isAuthenticated() {
      return iserver != null
          && iserver.authStatus() != null
          && Boolean.TRUE.equals(iserver.authStatus().authenticated())
          && Boolean.TRUE.equals(iserver.authStatus().connected());
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record IServerStatus(
      @JsonProperty("authStatus") AuthStatus authStatus) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AuthStatus(
      @JsonProperty("authenticated") Boolean authenticated,
      @JsonProperty("connected") Boolean connected,
      @JsonProperty("competing") Boolean competing,
      @JsonProperty("message") String message) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record IbkrOrderStatus(
      @JsonProperty("order_id") Long orderId,
      @JsonProperty("order_status") String orderStatus,
      @JsonProperty("encrypt_message") String encryptMessage) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AccountOrdersResponse(
      @JsonProperty("orders") List<IbkrOrder> orders,
      @JsonProperty("snapshot") Boolean snapshot) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record IbkrOrder(
      @JsonProperty("orderId") Long orderId,
      @JsonProperty("status") String status,
      @JsonProperty("conid") Long conid,
      @JsonProperty("side") String side,
      @JsonProperty("totalSize") Double totalSize,
      @JsonProperty("filledQuantity") Double filledQuantity,
      @JsonProperty("cOID") String coid) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record IbkrTrade(
      @JsonProperty("execution_id") String executionId,
      @JsonProperty("orderId") String orderId,
      @JsonProperty("side") String side,
      @JsonProperty("size") Double size,
      @JsonProperty("price") Double price,
      @JsonProperty("commission") Double commission,
      @JsonProperty("trade_time_r") Long tradeTimeMs,
      @JsonProperty("conid") Long conid,
      @JsonProperty("symbol") String symbol) {}
}
