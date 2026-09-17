package io.streamshub.console.legacy.dependents;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.streamshub.console.legacy.api.v1alpha1.LegacyConsole;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@ApplicationScoped
@KubernetesDependent(informer = @Informer(labelSelector = LegacyConsoleResource.MANAGEMENT_SELECTOR))
public class LegacyConsoleSecret extends CRUDKubernetesDependentResource<Secret, LegacyConsole> implements LegacyConsoleResource<Secret> {

    public static final String NAME = "console-secret";

    public LegacyConsoleSecret() {
        super(Secret.class);
    }

    @Override
    public String resourceName() {
        return NAME;
    }

    @Override
    protected Secret desired(LegacyConsole primary, Context<LegacyConsole> context) {
        @SuppressWarnings("unchecked")
        Map<String, String> data = context.managedWorkflowAndDependentResourceContext()
                .getMandatory("ConsoleSecretData", Map.class);

        updateDigest(context, "console-digest", data);

        return new SecretBuilder()
                .withNewMetadata()
                    .withName(instanceName(primary))
                    .withNamespace(primary.getMetadata().getNamespace())
                    .withLabels(commonLabels("console"))
                .endMetadata()
                .withData(data)
                .build();
    }
}
