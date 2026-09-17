package io.streamshub.console.legacy.dependents;

import jakarta.enterprise.context.ApplicationScoped;

import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@ApplicationScoped
@KubernetesDependent(informer = @Informer(labelSelector = LegacyConsoleResource.MANAGEMENT_SELECTOR))
public class LegacyPrometheusServiceAccount extends LegacyBaseServiceAccount {

    public static final String NAME = "prometheus-serviceaccount";

    public LegacyPrometheusServiceAccount() {
        super("prometheus", "prometheus.serviceaccount.yaml", NAME);
    }

}
