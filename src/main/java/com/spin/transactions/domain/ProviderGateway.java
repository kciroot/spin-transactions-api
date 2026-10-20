package com.spin.transactions.domain;

/** Output port: external provider. */
public interface ProviderGateway {

    /** @throws ProviderUnavailableException when no definitive answer could be obtained */
    ProviderResult execute(ProviderRequest request);
}
