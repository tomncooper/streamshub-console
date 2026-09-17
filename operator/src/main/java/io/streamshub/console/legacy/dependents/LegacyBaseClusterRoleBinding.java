package io.streamshub.console.legacy.dependents;

import java.util.Optional;
import java.util.Set;

import io.streamshub.console.legacy.api.v1alpha1.LegacyConsole;

import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Updater;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

abstract class LegacyBaseClusterRoleBinding extends KubernetesDependentResource<ClusterRoleBinding, LegacyConsole>
    implements SecondaryToPrimaryMapper<ClusterRoleBinding>,
        Creator<ClusterRoleBinding, LegacyConsole>,
        Updater<ClusterRoleBinding, LegacyConsole>,
        Deleter<LegacyConsole>,
        LegacyConsoleResource<ClusterRoleBinding> {

    private final String appName;
    private final String templateName;
    private final String resourceName;

    protected LegacyBaseClusterRoleBinding(String appName, String templateName, String resourceName) {
        super(ClusterRoleBinding.class);
        this.appName = appName;
        this.templateName = templateName;
        this.resourceName = resourceName;
    }

    @Override
    public String instanceName(LegacyConsole primary) {
        return primary.getMetadata().getNamespace() + "-" + LegacyConsoleResource.super.instanceName(primary);
    }

    @Override
    public Optional<ClusterRoleBinding> getSecondaryResource(LegacyConsole primary, Context<LegacyConsole> context) {
        return LegacyConsoleResource.super.getSecondaryResource(primary, context);
    }

    @Override
    public String resourceName() {
        return resourceName;
    }

    @Override
    protected ClusterRoleBinding desired(LegacyConsole primary, Context<LegacyConsole> context) {
        return load(context, templateName, ClusterRoleBinding.class)
            .edit()
            .editMetadata()
                .withName(instanceName(primary))
                .withLabels(commonLabels(appName, resourceName))
            .endMetadata()
            .editRoleRef()
                .withName(roleName(primary))
            .endRoleRef()
            .editFirstSubject()
                .withName(subjectName(primary))
                .withNamespace(primary.getMetadata().getNamespace())
            .endSubject()
            .build();
    }

    protected abstract String roleName(LegacyConsole primary);

    protected abstract String subjectName(LegacyConsole primary);

    @Override
    public Set<ResourceID> toPrimaryResourceIDs(ClusterRoleBinding resource) {
        return Mappers.fromDefaultAnnotations(LegacyConsole.class).toPrimaryResourceIDs(resource);
    }
}
