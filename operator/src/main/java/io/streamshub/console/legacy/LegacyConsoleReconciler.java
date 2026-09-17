package io.streamshub.console.legacy;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.streamshub.console.legacy.api.v1alpha1.LegacyConsole;
import io.streamshub.console.api.v1alpha1.status.Condition;
import io.streamshub.console.api.v1alpha1.status.ConditionBuilder;
import io.streamshub.console.legacy.dependents.LegacyConfigurationProcessor;
import io.streamshub.console.legacy.dependents.LegacyConsoleClusterRole;
import io.streamshub.console.legacy.dependents.LegacyConsoleClusterRoleBinding;
import io.streamshub.console.legacy.dependents.LegacyConsoleDeployment;
import io.streamshub.console.legacy.dependents.LegacyConsoleIngress;
import io.streamshub.console.legacy.dependents.LegacyConsoleMonitoringClusterRoleBinding;
import io.streamshub.console.legacy.dependents.LegacyConsoleResource;
import io.streamshub.console.legacy.dependents.LegacyConsoleRoute;
import io.streamshub.console.legacy.dependents.LegacyConsoleSecret;
import io.streamshub.console.legacy.dependents.LegacyConsoleService;
import io.streamshub.console.legacy.dependents.LegacyConsoleServiceAccount;
import io.streamshub.console.legacy.dependents.LegacyPrometheusClusterRole;
import io.streamshub.console.legacy.dependents.LegacyPrometheusClusterRoleBinding;
import io.streamshub.console.legacy.dependents.LegacyPrometheusConfigMap;
import io.streamshub.console.legacy.dependents.LegacyPrometheusDeployment;
import io.streamshub.console.legacy.dependents.LegacyPrometheusService;
import io.streamshub.console.legacy.dependents.LegacyPrometheusServiceAccount;
import io.streamshub.console.legacy.dependents.conditions.LegacyDeploymentReadyCondition;
import io.streamshub.console.legacy.dependents.conditions.LegacyMigrationGuardCondition;
import io.streamshub.console.legacy.dependents.conditions.LegacyPrometheusPrecondition;
import io.streamshub.console.support.MigrationGuard;
import io.streamshub.console.support.RootCause;

import io.javaoperatorsdk.operator.AggregatedOperatorException;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.MaxReconciliationInterval;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowReconcileResult;

@ControllerConfiguration(
        name = "legacyconsolereconciler",
        maxReconciliationInterval = @MaxReconciliationInterval(
                interval = 60,
                timeUnit = TimeUnit.SECONDS)
)
@Workflow(
        dependents = {
            @Dependent(
                    name = LegacyConfigurationProcessor.NAME,
                    type = LegacyConfigurationProcessor.class,
                    reconcilePrecondition = LegacyMigrationGuardCondition.class,
                    readyPostcondition = LegacyConfigurationProcessor.Postcondition.class),
            @Dependent(
                    name = LegacyPrometheusClusterRole.NAME,
                    type = LegacyPrometheusClusterRole.class,
                    dependsOn = LegacyConfigurationProcessor.NAME,
                    reconcilePrecondition = LegacyPrometheusPrecondition.class),
            @Dependent(
                    name = LegacyPrometheusServiceAccount.NAME,
                    type = LegacyPrometheusServiceAccount.class,
                    dependsOn = LegacyConfigurationProcessor.NAME,
                    reconcilePrecondition = LegacyPrometheusPrecondition.class),
            @Dependent(
                    name = LegacyPrometheusClusterRoleBinding.NAME,
                    type = LegacyPrometheusClusterRoleBinding.class,
                    reconcilePrecondition = LegacyPrometheusPrecondition.class,
                    dependsOn = {
                        LegacyConfigurationProcessor.NAME,
                        LegacyPrometheusClusterRole.NAME,
                        LegacyPrometheusServiceAccount.NAME
                    }),
            @Dependent(
                    name = LegacyPrometheusConfigMap.NAME,
                    type = LegacyPrometheusConfigMap.class,
                    dependsOn = LegacyConfigurationProcessor.NAME,
                    reconcilePrecondition = LegacyPrometheusPrecondition.class),
            @Dependent(
                    name = LegacyPrometheusDeployment.NAME,
                    type = LegacyPrometheusDeployment.class,
                    reconcilePrecondition = LegacyPrometheusPrecondition.class,
                    dependsOn = {
                        LegacyPrometheusClusterRoleBinding.NAME,
                        LegacyPrometheusConfigMap.NAME
                    },
                    readyPostcondition = LegacyDeploymentReadyCondition.class),
            @Dependent(
                    name = LegacyPrometheusService.NAME,
                    type = LegacyPrometheusService.class,
                    reconcilePrecondition = LegacyPrometheusPrecondition.class,
                    dependsOn = LegacyPrometheusDeployment.NAME),
            @Dependent(
                    name = LegacyConsoleClusterRole.NAME,
                    type = LegacyConsoleClusterRole.class,
                    dependsOn = LegacyConfigurationProcessor.NAME),
            @Dependent(
                    name = LegacyConsoleServiceAccount.NAME,
                    type = LegacyConsoleServiceAccount.class,
                    dependsOn = LegacyConfigurationProcessor.NAME),
            @Dependent(
                    name = LegacyConsoleClusterRoleBinding.NAME,
                    type = LegacyConsoleClusterRoleBinding.class,
                    dependsOn = {
                        LegacyConsoleClusterRole.NAME,
                        LegacyConsoleServiceAccount.NAME
                    }),
            @Dependent(
                    name = LegacyConsoleMonitoringClusterRoleBinding.NAME,
                    type = LegacyConsoleMonitoringClusterRoleBinding.class,
                    reconcilePrecondition = LegacyConsoleMonitoringClusterRoleBinding.Precondition.class,
                    dependsOn = LegacyConsoleServiceAccount.NAME),
            @Dependent(
                    name = LegacyConsoleSecret.NAME,
                    type = LegacyConsoleSecret.class,
                    dependsOn = LegacyConfigurationProcessor.NAME),
            @Dependent(
                    name = LegacyConsoleService.NAME,
                    type = LegacyConsoleService.class,
                    dependsOn = LegacyConfigurationProcessor.NAME),
            @Dependent(
                    name = LegacyConsoleDeployment.NAME,
                    type = LegacyConsoleDeployment.class,
                    dependsOn = {
                        LegacyConsoleClusterRoleBinding.NAME,
                        LegacyConsoleSecret.NAME
                    },
                    readyPostcondition = LegacyDeploymentReadyCondition.class),
            // Only one of ingress or route will be active for Kubernetes/OpenShift cluster.
            @Dependent(
                    name = LegacyConsoleIngress.NAME,
                    type = LegacyConsoleIngress.class,
                    reconcilePrecondition = LegacyConsoleIngress.Precondition.class,
                    readyPostcondition = LegacyConsoleIngress.Postcondition.class,
                    dependsOn = LegacyConsoleDeployment.NAME),
            @Dependent(
                    name = LegacyConsoleRoute.NAME,
                    type = LegacyConsoleRoute.class,
                    activationCondition = LegacyConsoleRoute.Precondition.class,
                    readyPostcondition = LegacyConsoleRoute.Postcondition.class,
                    dependsOn = LegacyConsoleDeployment.NAME),
        })
public class LegacyConsoleReconciler implements Reconciler<LegacyConsole>, Cleaner<LegacyConsole> {

    /** Fixed backoff used to re-check a same-named peer's migration conflict. */
    private static final Duration MIGRATION_CONFLICT_BACKOFF = Duration.ofSeconds(30);

    @Override
    public UpdateControl<LegacyConsole> reconcile(LegacyConsole resource, Context<LegacyConsole> context) {
        var result = context.managedWorkflowAndDependentResourceContext().getWorkflowReconcileResult();
        boolean deferred = determineConditions(resource, context, result, null);

        Long generation = resource.getMetadata().getGeneration();

        if (!Objects.equals(generation, resource.getStatus().getObservedGeneration())) {
            resource.getStatus().setObservedGeneration(generation);
        }

        var update = UpdateControl.patchStatus(resource);
        return deferred ? update.rescheduleAfter(MIGRATION_CONFLICT_BACKOFF) : update;
    }

    @Override
    public ErrorStatusUpdateControl<LegacyConsole> updateErrorStatus(LegacyConsole resource,
            Context<LegacyConsole> context,
            Exception e) {

        Logger.getLogger(getClass()).debug("Exception during reconcile", e);
        var result = context.managedWorkflowAndDependentResourceContext().getWorkflowReconcileResult();
        determineConditions(resource, context, result, e);

        return ErrorStatusUpdateControl.patchStatus(resource)
                .rescheduleAfter(Duration.ofSeconds(10));
    }

    @Override
    public DeleteControl cleanup(LegacyConsole resource, Context<LegacyConsole> context) {
        return DeleteControl.defaultDelete();
    }

    /**
     * Sets the "Ready" condition as well as any "Error" conditions if applicable to the result of the
     * workflow reconcile processing. Returns {@code true} if a same-named peer in the other group
     * owns this Console's children and this side deferred (see {@link MigrationGuard}), in which
     * case the caller should requeue sooner than the default reconciliation interval.
     */
    private boolean determineConditions(LegacyConsole resource, Context<LegacyConsole> context, Optional<WorkflowReconcileResult> result, Exception e) {
        var status = resource.getOrCreateStatus();
        var readyCondition = status.getCondition("Ready");

        var verdict = context.managedWorkflowAndDependentResourceContext()
                .get(MigrationGuard.CONTEXT_KEY, MigrationGuard.Verdict.class);

        if (verdict.filter(MigrationGuard.Verdict.DEFER::equals).isPresent()) {
            readyCondition.setActive(true);
            readyCondition.setStatus("False");
            readyCondition.setLastTransitionTime(Instant.now().toString());
            readyCondition.setReason(Condition.Reasons.MIGRATION_CONFLICT);
            readyCondition.setMessage(("A Console named '%s' already exists under the "
                    + "console.streamshub.io group and owns its managed resources. "
                    + "Finish migrating to console.streamshub.io, or delete this deprecated "
                    + "console.streamshub.github.com resource, to stop reconciling it.").formatted(resource.getMetadata().getName()));
            status.clearStaleConditions();
            return true;
        }

        var notReady = result.map(r -> r.getNotReadyDependents()).filter(Predicate.not(Collection::isEmpty));
        var errored = result.map(r -> r.getErroredDependents()).filter(Predicate.not(Map::isEmpty));
        boolean isReady = notReady.isEmpty() && errored.isEmpty();

        String readyStatus = isReady ? "True" : "False";
        readyCondition.setActive(true);

        if (!readyStatus.equals(readyCondition.getStatus())) {
            readyCondition.setStatus(readyStatus);
            readyCondition.setLastTransitionTime(Instant.now().toString());
        }

        var erroredResources = errored.orElseGet(Collections::emptyMap);

        if (isReady) {
            readyCondition.setReason(null);
            readyCondition.setMessage("All resources ready");
        } else {
            var notReadyResources = notReady.orElseGet(Collections::emptyList);

            if (notReadyResources.isEmpty()) {
                readyCondition.setReason(Condition.Reasons.RECONCILIATION_EXCEPTION);
                readyCondition.setMessage("One or more exceptions occurred during Console reconciliation");
            } else if (notReadyResources.stream().anyMatch(LegacyConfigurationProcessor.class::isInstance)) {
                readyCondition.setReason(Condition.Reasons.INVALID_CONFIGURATION);
                readyCondition.setMessage("Console resource configuration is invalid");
            } else {
                readyCondition.setReason(Condition.Reasons.DEPENDENTS_NOT_READY);
                readyCondition.setMessage("Resources not ready: %s"
                        .formatted(notReadyResources.stream()
                                .map(LegacyConsoleResource.class::cast)
                                .map(r -> "%s[%s]".formatted(r.getClass().getSimpleName(), r.instanceName(resource)))
                                .collect(Collectors.joining("; "))));
            }

            for (var entry : erroredResources.entrySet()) {
                var erroredResource = (LegacyConsoleResource<?>) entry.getKey();
                var exception = entry.getValue();

                var message = "%s[%s] exception: %s".formatted(
                        erroredResource.getClass().getSimpleName(),
                        erroredResource.instanceName(resource),
                        exception.getMessage());

                status.updateCondition(new ConditionBuilder()
                        .withType(Condition.Types.ERROR)
                        .withStatus("True")
                        .withLastTransitionTime(Instant.now().toString())
                        .withReason(Condition.Reasons.RECONCILIATION_EXCEPTION)
                        .withMessage(message)
                        .build());
            }
        }

        /*
         * Only report the main exception if it is different than the exception already reported
         * for an "errored" resource.
         */
        RootCause.of(e).filter(hasDistinct(erroredResources.values())).ifPresent(rootCause ->
            status.updateCondition(new ConditionBuilder()
                    .withType(Condition.Types.ERROR)
                    .withStatus("True")
                    .withLastTransitionTime(Instant.now().toString())
                    .withReason(Condition.Reasons.RECONCILIATION_EXCEPTION)
                    .withMessage(getMessage(rootCause))
                    .build())
        );

        status.clearStaleConditions();
        return false;
    }

    /**
     * Provides a predicate that determines whether the tested throwable is itself
     * distinct from any of the provided exceptions, or in the case of an aggregated
     * exception, whether any of the nested exceptions are distinct from the
     * provided exceptions.
     */
    private Predicate<Throwable> hasDistinct(Collection<Exception> exceptions) {
        return rootCause -> {
            Collection<? extends Throwable> causes;

            if (rootCause instanceof AggregatedOperatorException aggregated) {
                causes = aggregated.getAggregatedExceptions().values();
            } else {
                causes = List.of(rootCause);
            }

            return !exceptions.containsAll(causes);
        };
    }

    private String getMessage(Throwable rootCause) {
        String message;

        if (rootCause instanceof AggregatedOperatorException aggregated) {
            message = aggregated.getAggregatedExceptions().values().stream()
                .map(Exception::getMessage)
                .collect(Collectors.joining("; "));
        } else {
            message = rootCause.getMessage();
        }

        return message;
    }
}
