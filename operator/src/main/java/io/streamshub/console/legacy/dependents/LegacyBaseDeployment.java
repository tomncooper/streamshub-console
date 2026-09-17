package io.streamshub.console.legacy.dependents;

import java.util.Optional;

import io.streamshub.console.legacy.api.v1alpha1.LegacyConsole;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

abstract class LegacyBaseDeployment extends CRUDKubernetesDependentResource<Deployment, LegacyConsole>
    implements LegacyConsoleResource<Deployment> {

    private final String resourceName;

    protected LegacyBaseDeployment(String resourceName) {
        super(Deployment.class);
        this.resourceName = resourceName;
    }

    @Override
    public Optional<Deployment> getSecondaryResource(LegacyConsole primary, Context<LegacyConsole> context) {
        return LegacyConsoleResource.super.getSecondaryResource(primary, context);
    }

    @Override
    public String resourceName() {
        return resourceName;
    }
}
