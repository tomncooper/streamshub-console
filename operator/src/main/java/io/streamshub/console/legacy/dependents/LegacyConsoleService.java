package io.streamshub.console.legacy.dependents;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.streamshub.console.legacy.api.v1alpha1.LegacyConsole;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@ApplicationScoped
@KubernetesDependent(informer = @Informer(labelSelector = LegacyConsoleResource.MANAGEMENT_SELECTOR))
public class LegacyConsoleService extends LegacyBaseService {

    public static final String NAME = "console-service";

    @Inject
    LegacyConsoleDeployment deployment;

    public LegacyConsoleService() {
        super("console", "console.service.yaml", NAME);
    }

    @Override
    protected String appName(LegacyConsole primary) {
        return deployment.instanceName(primary);
    }

    @Override
    protected Service desired(LegacyConsole primary, Context<LegacyConsole> context) {
        boolean tls = primary.getSpec().getTls() != null;

        return super.desired(primary, context)
            .edit()
            .editSpec()
                .editFirstPort()
                    .withName(tls ? "https" : "http")
                    .withPort(tls ? 443 : 80)
                    .withTargetPort(new IntOrString(tls ? 8443 : 8080))
                .endPort()
            .endSpec()
            .build();
    }
}
