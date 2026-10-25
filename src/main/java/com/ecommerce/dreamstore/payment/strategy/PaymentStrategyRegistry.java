package com.ecommerce.dreamstore.payment.strategy;

import com.ecommerce.dreamstore.entity.PaymentProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry implementing Strategy pattern resolution.
 * Automatically discovers all available {@link PaymentStrategy} Spring beans.
 */
@Component
public class PaymentStrategyRegistry {

    private final Map<PaymentProvider, PaymentStrategy> strategies = new EnumMap<>(PaymentProvider.class);

    public PaymentStrategyRegistry(List<PaymentStrategy> strategyList) {
        for (PaymentStrategy strategy : strategyList) {
            strategies.put(strategy.getProvider(), strategy);
        }
    }

    public PaymentStrategy getStrategy(PaymentProvider provider) {
        return Optional.ofNullable(strategies.get(provider))
                .orElseThrow(() -> new IllegalArgumentException("No payment strategy registered for provider: " + provider));
    }

    public boolean hasStrategy(PaymentProvider provider) {
        return strategies.containsKey(provider);
    }
}
