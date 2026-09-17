package io.streamshub.console.legacy.dependents;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.streamshub.console.legacy.api.v1alpha1.LegacyConsole;

import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@ApplicationScoped
@KubernetesDependent(
    informer = @Informer(
        namespaces = Constants.WATCH_ALL_NAMESPACES,
        labelSelector = LegacyConsoleResource.MANAGEMENT_SELECTOR))
public class LegacyPrometheusClusterRoleBinding extends LegacyBaseClusterRoleBinding {

    public static final String NAME = "prometheus-clusterrolebinding";

    @Inject
    LegacyPrometheusClusterRole clusterRole;

    @Inject
    LegacyPrometheusServiceAccount serviceAccount;

    public LegacyPrometheusClusterRoleBinding() {
        super("prometheus", "prometheus.clusterrolebinding.yaml", NAME);
    }

    @Override
    protected String roleName(LegacyConsole primary) {
        return clusterRole.instanceName(primary);
    }

    @Override
    protected String subjectName(LegacyConsole primary) {
        return serviceAccount.instanceName(primary);
    }
}
