package io.streamshub.console.dependents.conditions;

import io.streamshub.console.api.v1alpha1.Console;
import io.streamshub.console.legacy.api.v1alpha1.LegacyConsole;
import io.streamshub.console.support.MigrationGuard;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

/**
 * Gates the maintained ({@code console.streamshub.io}) reconciler's workflow root so that it
 * defers to a same-named legacy-group peer that already owns the children. See
 * {@link MigrationGuard} for the shared algorithm; this class only supplies this side's
 * group/peer-type identity and stashes the verdict for {@code ConsoleReconciler} to surface as a
 * status condition.
 */
public class MigrationGuardCondition implements Condition<Object, Console> {

    public static final String SELF_GROUP = "console.streamshub.io";
    private static final String OTHER_GROUP = "console.streamshub.github.com";

    @Override
    public boolean isMet(DependentResource<Object, Console> dependentResource, Console primary, Context<Console> context) {
        var verdict = MigrationGuard.evaluate(
                context.getClient(),
                primary.getMetadata().getNamespace(),
                primary.getMetadata().getName(),
                SELF_GROUP,
                LegacyConsole.class,
                OTHER_GROUP);

        context.managedWorkflowAndDependentResourceContext().put(MigrationGuard.CONTEXT_KEY, verdict);

        return verdict == MigrationGuard.Verdict.OWN;
    }
}
