package io.streamshub.console.legacy.dependents;

import java.util.Optional;

import io.streamshub.console.legacy.api.v1alpha1.LegacyConsole;

import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

abstract class LegacyBaseServiceAccount extends CRUDKubernetesDependentResource<ServiceAccount, LegacyConsole>
    implements LegacyConsoleResource<ServiceAccount> {

    private final String appName;
    private final String templateName;
    private final String resourceName;

    protected LegacyBaseServiceAccount(String appName, String templateName, String resourceName) {
        super(ServiceAccount.class);
        this.appName = appName;
        this.templateName = templateName;
        this.resourceName = resourceName;
    }

    @Override
    public Optional<ServiceAccount> getSecondaryResource(LegacyConsole primary, Context<LegacyConsole> context) {
        return LegacyConsoleResource.super.getSecondaryResource(primary, context);
    }

    @Override
    public String resourceName() {
        return resourceName;
    }

    @Override
    protected ServiceAccount desired(LegacyConsole primary, Context<LegacyConsole> context) {
        return load(context, templateName, ServiceAccount.class)
            .edit()
            .editMetadata()
                .withName(instanceName(primary))
                .withNamespace(primary.getMetadata().getNamespace())
                .withLabels(commonLabels(appName))
            .endMetadata()
            .build();
    }
}
