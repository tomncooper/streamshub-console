package io.streamshub.console.legacy.dependents;

import jakarta.enterprise.context.ApplicationScoped;

import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@ApplicationScoped
@KubernetesDependent(informer = @Informer(labelSelector = LegacyConsoleResource.MANAGEMENT_SELECTOR))
public class LegacyConsoleServiceAccount extends LegacyBaseServiceAccount {

    public static final String NAME = "console-serviceaccount";

    public LegacyConsoleServiceAccount() {
        super("console", "console.serviceaccount.yaml", NAME);
    }

}
