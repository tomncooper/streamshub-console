package io.streamshub.console.legacy.dependents;

import java.util.Optional;
import java.util.Set;

import io.streamshub.console.legacy.api.v1alpha1.LegacyConsole;

import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Updater;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

abstract class LegacyBaseClusterRole extends KubernetesDependentResource<ClusterRole, LegacyConsole>
    implements SecondaryToPrimaryMapper<ClusterRole>,
        Creator<ClusterRole, LegacyConsole>,
        Updater<ClusterRole, LegacyConsole>,
        Deleter<LegacyConsole>,
        LegacyConsoleResource<ClusterRole> {

    private final String appName;
    private final String templateName;
    private final String resourceName;

    protected LegacyBaseClusterRole(String appName, String templateName, String resourceName) {
        super(ClusterRole.class);
        this.appName = appName;
        this.templateName = templateName;
        this.resourceName = resourceName;
    }

    @Override
    public String instanceName(LegacyConsole primary) {
        return primary.getMetadata().getNamespace() + "-" + LegacyConsoleResource.super.instanceName(primary);
    }

    @Override
    public Optional<ClusterRole> getSecondaryResource(LegacyConsole primary, Context<LegacyConsole> context) {
        return LegacyConsoleResource.super.getSecondaryResource(primary, context);
    }

    @Override
    public String resourceName() {
        return resourceName;
    }

    @Override
    protected ClusterRole desired(LegacyConsole primary, Context<LegacyConsole> context) {
        return load(context, templateName, ClusterRole.class)
            .edit()
            .editMetadata()
                .withName(instanceName(primary))
                .withLabels(commonLabels(appName))
            .endMetadata()
            .build();
    }

    @Override
    public Set<ResourceID> toPrimaryResourceIDs(ClusterRole resource) {
        return Mappers.fromDefaultAnnotations(LegacyConsole.class).toPrimaryResourceIDs(resource);
    }
}
