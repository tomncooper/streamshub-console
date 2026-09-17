package io.streamshub.console.legacy.dependents;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.streamshub.console.legacy.api.v1alpha1.LegacyConsole;

import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@ApplicationScoped
@KubernetesDependent(informer = @Informer(labelSelector = LegacyConsoleResource.MANAGEMENT_SELECTOR))
public class LegacyPrometheusService extends LegacyBaseService {

    public static final String NAME = "prometheus-service";

    @Inject
    LegacyPrometheusDeployment deployment;

    public LegacyPrometheusService() {
        super("prometheus", "prometheus.service.yaml", NAME);
    }

    @Override
    protected String appName(LegacyConsole primary) {
        return deployment.instanceName(primary);
    }

    String getUrl(LegacyConsole primary, Context<LegacyConsole> context) {
        Service desired = super.desired(primary, context);

        return "http://%s.%s.svc.cluster.local:%d".formatted(
                desired.getMetadata().getName(),
                desired.getMetadata().getNamespace(),
                desired.getSpec().getPorts().get(0).getPort());
    }
}
