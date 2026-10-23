package com.spin.transactions.infrastructure.config;

import com.spin.transactions.application.TransactionService;
import com.spin.transactions.domain.ProviderGateway;
import com.spin.transactions.domain.TransactionRepository;
import com.spin.transactions.domain.TransactionRules;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the framework-free application layer. */
@Configuration
class ApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    TransactionRules transactionRules() {
        return new TransactionRules();
    }

    @Bean
    TransactionService transactionService(TransactionRepository repository, ProviderGateway provider,
                                          TransactionRules rules, Clock clock) {
        return new TransactionService(repository, provider, rules, clock);
    }
}
