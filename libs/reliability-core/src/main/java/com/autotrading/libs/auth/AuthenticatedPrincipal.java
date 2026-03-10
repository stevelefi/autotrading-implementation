package com.autotrading.libs.auth;

/**
 * Immutable value object returned by {@link ApiKeyAuthenticator#authenticate(String)}.
 *
 * <p>Carries just enough identity data to populate {@code principal_id} and
 * {@code principal_json} on the ingress raw event record and in MDC log fields.
 *
 * @param accountId  the resolved account identifier (e.g. {@code "acc-local-dev"})
 * @param keyHash    the SHA-256 hex digest of the raw Bearer token
 * @param generation key generation — used in {@code principal_json} to aid rotation audits
 */
public record AuthenticatedPrincipal(String accountId, String keyHash, int generation) {
}
