package io.streamshub.console.legacy.dependents.conditions;

import java.util.Collections;
import java.util.Optional;

import io.streamshub.console.legacy.api.v1alpha1.LegacyConsole;
import io.streamshub.console.api.v1alpha1.spec.metrics.MetricsSource;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class LegacyPrometheusPrecondition implements Condition<Object, LegacyConsole> {

    @Override
    public boolean isMet(DependentResource<Object, LegacyConsole> dependentResource, LegacyConsole primary, Context<LegacyConsole> context) {
        var metricsSources = Optional.ofNullable(primary.getSpec().getMetricsSources())
                .orElseGet(Collections::emptyList);

        if (metricsSources.isEmpty()) {
            return true;
        }

        return metricsSources.stream()
                .anyMatch(prometheus -> prometheus.getType() == MetricsSource.Type.EMBEDDED);
    }

}
