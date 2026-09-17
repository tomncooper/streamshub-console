package io.streamshub.console.legacy.dependents.conditions;

import io.streamshub.console.api.v1alpha1.Console;
import io.streamshub.console.legacy.api.v1alpha1.LegacyConsole;
import io.streamshub.console.support.MigrationGuard;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

/**
 * Gates the legacy ({@code console.streamshub.github.com}) reconciler's workflow root so that it
 * defers to a same-named maintained-group peer that already owns the children. See
 * {@link MigrationGuard} for the shared algorithm; this class only supplies this side's
 * group/peer-type identity and stashes the verdict for {@code LegacyConsoleReconciler} to surface
 * as a status condition.
 */
public class LegacyMigrationGuardCondition implements Condition<Object, LegacyConsole> {

    public static final String SELF_GROUP = "console.streamshub.github.com";
    private static final String OTHER_GROUP = "console.streamshub.io";

    @Override
    public boolean isMet(DependentResource<Object, LegacyConsole> dependentResource, LegacyConsole primary, Context<LegacyConsole> context) {
        var verdict = MigrationGuard.evaluate(
                context.getClient(),
                primary.getMetadata().getNamespace(),
                primary.getMetadata().getName(),
                SELF_GROUP,
                Console.class,
                OTHER_GROUP);

        context.managedWorkflowAndDependentResourceContext().put(MigrationGuard.CONTEXT_KEY, verdict);

        return verdict == MigrationGuard.Verdict.OWN;
    }
}
