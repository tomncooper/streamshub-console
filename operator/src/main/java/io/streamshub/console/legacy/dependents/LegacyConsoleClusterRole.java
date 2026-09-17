package io.streamshub.console.legacy.dependents;

import jakarta.enterprise.context.ApplicationScoped;

import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@ApplicationScoped
@KubernetesDependent(
    informer = @Informer(
        namespaces = Constants.WATCH_ALL_NAMESPACES,
        labelSelector = LegacyConsoleResource.MANAGEMENT_SELECTOR))
public class LegacyConsoleClusterRole extends LegacyBaseClusterRole {

    public static final String NAME = "console-clusterrole";

    public LegacyConsoleClusterRole() {
        super("console", "console.clusterrole.yaml", NAME);
    }

}
