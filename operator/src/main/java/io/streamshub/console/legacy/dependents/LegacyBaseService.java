package io.streamshub.console.legacy.dependents;

import java.util.Optional;

import io.streamshub.console.legacy.api.v1alpha1.LegacyConsole;

import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

abstract class LegacyBaseService extends CRUDKubernetesDependentResource<Service, LegacyConsole> implements LegacyConsoleResource<Service> {

    private final String appName;
    private final String templateName;
    private final String resourceName;

    protected LegacyBaseService(String appName, String templateName, String resourceName) {
        super(Service.class);
        this.appName = appName;
        this.templateName = templateName;
        this.resourceName = resourceName;
    }

    @Override
    public Optional<Service> getSecondaryResource(LegacyConsole primary, Context<LegacyConsole> context) {
        return LegacyConsoleResource.super.getSecondaryResource(primary, context);
    }

    @Override
    public String resourceName() {
        return resourceName;
    }

    @Override
    protected Service desired(LegacyConsole primary, Context<LegacyConsole> context) {
        return load(context, templateName, Service.class)
            .edit()
            .editMetadata()
                .withName(instanceName(primary))
                .withNamespace(primary.getMetadata().getNamespace())
                .withLabels(commonLabels(appName))
            .endMetadata()
            .editSpec()
                .addToSelector(INSTANCE_LABEL, appName(primary))
            .endSpec()
            .build();
    }

    protected abstract String appName(LegacyConsole primary);

}
