package io.streamshub.console.legacy.dependents;

import jakarta.enterprise.context.ApplicationScoped;

import io.streamshub.console.legacy.api.v1alpha1.LegacyConsole;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@ApplicationScoped
@KubernetesDependent(informer = @Informer(labelSelector = LegacyConsoleResource.MANAGEMENT_SELECTOR))
public class LegacyPrometheusConfigMap extends CRUDKubernetesDependentResource<ConfigMap, LegacyConsole>
        implements LegacyConsoleResource<ConfigMap> {

    public static final String NAME = "prometheus-configmap";

    public LegacyPrometheusConfigMap() {
        super(ConfigMap.class);
    }

    @Override
    public String resourceName() {
        return NAME;
    }

    @Override
    protected ConfigMap desired(LegacyConsole primary, Context<LegacyConsole> context) {
        ConfigMap template = load(context, "prometheus.configmap.yaml", ConfigMap.class);
        updateDigest(context, "prometheus-digest", template.getData());

        return template.edit()
                .withNewMetadata()
                    .withName(instanceName(primary))
                    .withNamespace(primary.getMetadata().getNamespace())
                    .withLabels(commonLabels("prometheus"))
                .endMetadata()
                .build();
    }

}
