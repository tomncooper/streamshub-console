package io.streamshub.console.support;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * CR-type-agnostic guard preventing the maintained ({@code console.streamshub.io}) and legacy
 * ({@code console.streamshub.github.com}) Console controllers from fighting over identically-named
 * children when a same-named CR exists in both groups during migration.
 * <p>
 * Used as a {@code reconcilePrecondition} on each reconciler's workflow-root
 * {@code ConfigurationProcessor} dependent: gating the root short-circuits the whole
 * {@code @Workflow} dependent graph for the losing side, since every dependent transitively
 * {@code dependsOn} it. A plain {@code reconcile()}-body check cannot suppress {@code @Workflow}
 * dependent writes, so the precondition is the only correct place to enforce this.
 */
public final class MigrationGuard {

    /**
     * Key used to stash the verdict in the workflow's managed dependent resource context so that
     * the top-level reconciler can surface it as a status condition and adjust the requeue delay.
     */
    public static final String CONTEXT_KEY = "MigrationGuardVerdict";

    /**
     * Deterministic tie-breaker group for the case where both a maintained and legacy CR exist
     * with no children created yet by either (cold start): the old group wins so that operators
     * already running against the legacy group are not disrupted by a newly-created CR under the
     * new group racing to claim the same children first.
     */
    private static final String OLD_GROUP = "console.streamshub.github.com";

    private static final String CHILD_DEPLOYMENT_SUFFIX = "-console-deployment";
    private static final String OWNED_KIND = "Console";

    private MigrationGuard() {
    }

    public enum Verdict {
        /** No conflicting peer, or this side already owns the children: proceed normally. */
        OWN,
        /** A same-named peer in the other group owns the children: skip all dependents. */
        DEFER
    }

    /**
     * Evaluates whether the calling side ({@code selfGroup}) should own or defer reconciliation of
     * a Console CR that may have a same-named peer under {@code otherGroup}.
     *
     * @param client     Kubernetes client from the reconciler {@code Context}
     * @param namespace  namespace of the primary CR being reconciled
     * @param name       name of the primary CR being reconciled
     * @param selfGroup  API group of the CR type currently reconciling (the caller's own group)
     * @param otherType  the peer CR's Java type (the other group's Console CR class)
     * @param otherGroup API group of the peer CR type
     */
    public static <T extends HasMetadata> Verdict evaluate(
            KubernetesClient client,
            String namespace,
            String name,
            String selfGroup,
            Class<T> otherType,
            String otherGroup) {

        T other = client.resources(otherType).inNamespace(namespace).withName(name).get();

        if (other == null) {
            // No peer exists under the other group; no conflict is possible.
            return Verdict.OWN;
        }

        Deployment deployment = client.apps().deployments()
                .inNamespace(namespace)
                .withName(name + CHILD_DEPLOYMENT_SUFFIX)
                .get();

        String owningGroup = owningGroup(deployment, name);

        if (selfGroup.equals(owningGroup)) {
            // Children already ours (identified via ownerReference group); proceed.
            return Verdict.OWN;
        }

        if (owningGroup != null) {
            // A peer already owns the children; back off rather than fight over them.
            return Verdict.DEFER;
        }

        // Cold-start tie: neither side's children exist yet. Deterministic old-group-wins avoids
        // both sides racing to create children simultaneously.
        return OLD_GROUP.equals(selfGroup) ? Verdict.OWN : Verdict.DEFER;
    }

    /**
     * Determines which group's Console CR owns the given child Deployment, by inspecting its
     * ownerReferences for a {@code Console}-kind owner matching {@code name} and reading the
     * group out of that reference's {@code apiVersion}. Returns {@code null} if the deployment
     * doesn't exist yet, or has no matching owner reference (e.g. adopted by neither side yet).
     */
    private static String owningGroup(Deployment deployment, String name) {
        if (deployment == null) {
            return null;
        }

        List<OwnerReference> owners = Optional.ofNullable(deployment.getMetadata())
                .map(m -> m.getOwnerReferences())
                .orElseGet(Collections::emptyList);

        return owners.stream()
                .filter(ref -> OWNED_KIND.equals(ref.getKind()) && name.equals(ref.getName()))
                .map(OwnerReference::getApiVersion)
                .map(MigrationGuard::groupOf)
                .findFirst()
                .orElse(null);
    }

    private static String groupOf(String apiVersion) {
        int slash = apiVersion.indexOf('/');
        return slash < 0 ? null : apiVersion.substring(0, slash);
    }
}
