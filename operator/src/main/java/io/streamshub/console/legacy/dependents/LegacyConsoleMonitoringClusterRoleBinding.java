package io.streamshub.console.legacy.dependents;

import java.util.Collections;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.streamshub.console.legacy.api.v1alpha1.LegacyConsole;
import io.streamshub.console.api.v1alpha1.spec.metrics.MetricsSource;

import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

@ApplicationScoped
@KubernetesDependent(
    informer = @Informer(
        namespaces = Constants.WATCH_ALL_NAMESPACES,
        labelSelector = LegacyConsoleResource.MANAGEMENT_SELECTOR))
public class LegacyConsoleMonitoringClusterRoleBinding extends LegacyBaseClusterRoleBinding {

    public static final String NAME = "console-monitoring-clusterrolebinding";

    @Inject
    LegacyConsoleClusterRole clusterRole;

    @Inject
    LegacyConsoleServiceAccount serviceAccount;

    public LegacyConsoleMonitoringClusterRoleBinding() {
        super("console", "console-monitoring.clusterrolebinding.yaml", NAME);
    }

    @Override
    protected String roleName(LegacyConsole primary) {
        // Hard-coded, pre-existing cluster role available in OCP
        return "cluster-monitoring-view";
    }

    @Override
    protected String subjectName(LegacyConsole primary) {
        return serviceAccount.instanceName(primary);
    }

    /**
     * The cluster role binding to `cluster-monitoring-view` will only be created
     * if one of the metrics sources is OpenShift Monitoring.
     */
    public static class Precondition implements Condition<ClusterRoleBinding, LegacyConsole> {
        @Override
        public boolean isMet(DependentResource<ClusterRoleBinding, LegacyConsole> dependentResource,
                LegacyConsole primary,
                Context<LegacyConsole> context) {

            var metricsSources = Optional.ofNullable(primary.getSpec().getMetricsSources())
                    .orElseGet(Collections::emptyList);

            if (metricsSources.isEmpty()) {
                return false;
            }

            return metricsSources.stream()
                .anyMatch(prometheus -> prometheus.getType() == MetricsSource.Type.OPENSHIFT_MONITORING);
        }
    }
}
