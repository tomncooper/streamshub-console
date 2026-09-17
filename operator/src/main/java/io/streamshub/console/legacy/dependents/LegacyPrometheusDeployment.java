package io.streamshub.console.legacy.dependents;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.streamshub.console.legacy.api.v1alpha1.LegacyConsole;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@ApplicationScoped
@KubernetesDependent(informer = @Informer(labelSelector = LegacyConsoleResource.MANAGEMENT_SELECTOR))
public class LegacyPrometheusDeployment extends LegacyBaseDeployment {

    public static final String NAME = "prometheus-deployment";

    @Inject
    LegacyPrometheusServiceAccount serviceAccount;

    @Inject
    LegacyPrometheusConfigMap configMap;

    public LegacyPrometheusDeployment() {
        super(NAME);
    }

    @Override
    protected Deployment desired(LegacyConsole primary, Context<LegacyConsole> context) {
        Deployment desired = load(context, "prometheus.deployment.yaml", Deployment.class);
        String name = instanceName(primary);

        return desired.edit()
            .editMetadata()
                .withName(name)
                .withNamespace(primary.getMetadata().getNamespace())
                .withLabels(commonLabels("prometheus"))
            .endMetadata()
            .editSpec()
                .editSelector()
                    .withMatchLabels(Map.of(INSTANCE_LABEL, name))
                .endSelector()
                .editTemplate()
                    .editMetadata()
                        .addToLabels(Map.of(INSTANCE_LABEL, name))
                        .addToAnnotations("streamshub.github.com/dependency-digest", serializeDigest(context, "prometheus-digest"))
                    .endMetadata()
                    .editSpec()
                        .withServiceAccountName(serviceAccount.instanceName(primary))
                        .editMatchingVolume(c -> "config-volume".equals(c.getName()))
                            .editConfigMap()
                                .withName(configMap.instanceName(primary))
                            .endConfigMap()
                        .endVolume()
                    .endSpec()
                .endTemplate()
            .endSpec()
            .build();
    }

}
