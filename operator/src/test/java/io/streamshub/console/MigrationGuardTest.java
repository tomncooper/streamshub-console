package io.streamshub.console;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.streamshub.console.api.v1alpha1.Console;
import io.streamshub.console.api.v1alpha1.ConsoleBuilder;
import io.streamshub.console.api.v1alpha1.status.Condition;
import io.streamshub.console.dependents.ConsoleResource;
import io.streamshub.console.legacy.api.v1alpha1.LegacyConsole;
import io.streamshub.console.legacy.api.v1alpha1.LegacyConsoleBuilder;

import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.quarkus.test.junit.QuarkusTest;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the {@link io.streamshub.console.support.MigrationGuard}-based duplicate-management
 * behavior between the maintained ({@code console.streamshub.io}) and legacy
 * ({@code console.streamshub.github.com}) Console controllers when a same-named CR exists in
 * both groups. See PR-B item 3 of the streamshub.io namespace migration plan.
 */
@QuarkusTest
class MigrationGuardTest extends ConsoleReconcilerTestBase {

    private static final String DEPLOYMENT_SUFFIX = "-console-deployment";
    private static final String MAINTAINED_GROUP = "console.streamshub.io";
    private static final String LEGACY_GROUP = "console.streamshub.github.com";

    /**
     * {@link ConsoleReconcilerTestBase#setUp} cleans up Console/Deployment/ConfigMap/Secret/
     * Ingress between tests, but not LegacyConsole, ServiceAccount, Service, ClusterRole, or
     * ClusterRoleBinding. Since maintained and legacy dependents intentionally produce
     * identically-named children (by design, so MigrationGuard has something to arbitrate), a
     * leftover object from an earlier test method racing with a fresh reconcile of the same name
     * in the next test method causes a genuine (if test-induced) resourceVersion conflict, not a
     * MigrationGuard defect.
     */
    @BeforeEach
    void cleanupLegacyConsolesAndSharedChildren() {
        delete(client.resources(LegacyConsole.class).inAnyNamespace());
        delete(client.resources(ServiceAccount.class).inAnyNamespace().withLabels(ConsoleResource.MANAGEMENT_LABEL));
        delete(client.resources(Service.class).inAnyNamespace().withLabels(ConsoleResource.MANAGEMENT_LABEL));
        delete(client.resources(ClusterRole.class).withLabels(ConsoleResource.MANAGEMENT_LABEL));
        delete(client.resources(ClusterRoleBinding.class).withLabels(ConsoleResource.MANAGEMENT_LABEL));
    }

    private LegacyConsole createLegacyConsole(LegacyConsoleBuilder builder, String namespace) {
        var meta = new io.fabric8.kubernetes.api.model.ObjectMetaBuilder(builder.getMetadata())
                .withNamespace(namespace)
                .withName(CONSOLE_NAME)
                .build();

        return client.resource(builder.withMetadata(meta).build()).create();
    }

    private LegacyConsoleBuilder minimalLegacySpec() {
        return new LegacyConsoleBuilder()
                .withNewSpec()
                    .withHostname("example.com")
                    .addNewKafkaCluster()
                        .withName(kafkaCR.getMetadata().getName())
                        .withNamespace(kafkaCR.getMetadata().getNamespace())
                        .withListener(kafkaCR.getSpec().getKafka().getListeners().get(0).getName())
                    .endKafkaCluster()
                .endSpec();
    }

    private ConsoleBuilder minimalMaintainedSpec() {
        return new ConsoleBuilder()
                .withNewSpec()
                    .withHostname("example.com")
                    .addNewKafkaCluster()
                        .withName(kafkaCR.getMetadata().getName())
                        .withNamespace(kafkaCR.getMetadata().getNamespace())
                        .withListener(kafkaCR.getSpec().getKafka().getListeners().get(0).getName())
                    .endKafkaCluster()
                .endSpec();
    }

    private Deployment awaitDeploymentCreated(String namespace) {
        return await().atMost(LIMIT).until(() -> client.apps().deployments()
                .inNamespace(namespace)
                .withName(CONSOLE_NAME + DEPLOYMENT_SUFFIX)
                .get(), notNullValue());
    }

    private void awaitMigrationConflict(Class<? extends io.fabric8.kubernetes.client.CustomResource<?, io.streamshub.console.api.v1alpha1.status.ConsoleStatus>> type,
            String namespace) {
        await().ignoreException(NullPointerException.class).atMost(LIMIT).untilAsserted(() -> {
            var resource = client.resources(type).inNamespace(namespace).withName(CONSOLE_NAME).get();
            var conditions = resource.getStatus().getConditions();
            assertTrue(conditions.size() >= 1, () -> "Unexpected conditions: " + conditions);
            var condition = conditions.iterator().next();

            assertEquals(Condition.Types.READY, condition.getType(), condition::toString);
            assertEquals("False", condition.getStatus(), condition::toString);
            assertEquals(Condition.Reasons.MIGRATION_CONFLICT, condition.getReason(), condition::toString);
        });
    }

    private String owningGroup(Deployment deployment) {
        return deployment.getMetadata().getOwnerReferences().stream()
                .filter(ref -> "Console".equals(ref.getKind()) && CONSOLE_NAME.equals(ref.getName()))
                .map(OwnerReference::getApiVersion)
                .map(v -> v.substring(0, v.indexOf('/')))
                .findFirst()
                .orElse(null);
    }

    /**
     * Test 1 (deprecation metadata): the legacy CRD, as actually applied to the cluster, carries
     * native deprecated/deprecationWarning metadata on its version.
     */
    @Test
    void testLegacyCrdCarriesDeprecationMetadata() {
        CustomResourceDefinition legacyCrd = client.resources(CustomResourceDefinition.class)
                .withName("consoles.console.streamshub.github.com")
                .get();

        assertNotNull(legacyCrd, "legacy Console CRD was not applied to the cluster");
        var version = legacyCrd.getSpec().getVersions().get(0);

        assertTrue(Boolean.TRUE.equals(version.getDeprecated()), "expected legacy CRD version to be marked deprecated");
        assertNotNull(version.getDeprecationWarning());
        assertFalse(version.getDeprecationWarning().isBlank());
    }

    /**
     * Test 2 (collision, critical): a legacy Console is created first and owns its children; a
     * same-named maintained-group Console is then created and must defer without touching the
     * legacy-owned children.
     */
    @Test
    void testCollisionLegacyFirstNewGroupDefers() {
        createLegacyConsole(minimalLegacySpec(), CONSOLE_NS);
        Deployment legacyDeployment = awaitDeploymentCreated(CONSOLE_NS);
        assertEquals(LEGACY_GROUP, owningGroup(legacyDeployment));

        String resourceVersionBeforeCollision = legacyDeployment.getMetadata().getResourceVersion();

        createConsole(minimalMaintainedSpec());

        awaitMigrationConflict(Console.class, CONSOLE_NS);

        // The legacy-owned deployment must not churn (be re-reconciled/rewritten) while the
        // maintained side is deferring.
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            Deployment current = client.apps().deployments()
                    .inNamespace(CONSOLE_NS)
                    .withName(CONSOLE_NAME + DEPLOYMENT_SUFFIX)
                    .get();
            assertEquals(resourceVersionBeforeCollision, current.getMetadata().getResourceVersion(),
                    "legacy-owned Deployment resourceVersion churned while a peer held the conflict");
            assertEquals(LEGACY_GROUP, owningGroup(current), "child ownership moved away from the legacy owner");
        });
    }

    /**
     * Test 3 (inverse ordering): a maintained-group Console is created first and owns its
     * children; a same-named legacy Console is then created and must defer.
     */
    @Test
    void testCollisionNewGroupFirstLegacyDefers() {
        createConsole(minimalMaintainedSpec());
        Deployment maintainedDeployment = awaitDeploymentCreated(CONSOLE_NS);
        assertEquals(MAINTAINED_GROUP, owningGroup(maintainedDeployment));

        createLegacyConsole(minimalLegacySpec(), CONSOLE_NS);

        awaitMigrationConflict(LegacyConsole.class, CONSOLE_NS);

        assertThat(owningGroup(client.apps().deployments()
                .inNamespace(CONSOLE_NS)
                .withName(CONSOLE_NAME + DEPLOYMENT_SUFFIX)
                .get()), is(equalTo(MAINTAINED_GROUP)));
    }

    /**
     * Test 4 (convergence): once the winning side's CR is deleted, the deferring side must take
     * over ownership within its backoff window.
     */
    @Test
    void testConvergenceAfterWinnerDeletedLoserTakesOver() {
        createLegacyConsole(minimalLegacySpec(), CONSOLE_NS);
        awaitDeploymentCreated(CONSOLE_NS);

        createConsole(minimalMaintainedSpec());
        awaitMigrationConflict(Console.class, CONSOLE_NS);

        client.resources(LegacyConsole.class).inNamespace(CONSOLE_NS).withName(CONSOLE_NAME).delete();

        // Once the legacy peer is gone, the maintained side should adopt/rebuild the children
        // rather than remain deferred, within the fixed migration-conflict backoff.
        await().atMost(Duration.ofSeconds(45)).untilAsserted(() -> {
            Deployment current = client.apps().deployments()
                    .inNamespace(CONSOLE_NS)
                    .withName(CONSOLE_NAME + DEPLOYMENT_SUFFIX)
                    .get();
            assertNotNull(current);
            assertEquals(MAINTAINED_GROUP, owningGroup(current));

            var console = client.resources(Console.class).inNamespace(CONSOLE_NS).withName(CONSOLE_NAME).get();
            var readyCondition = console.getStatus().getConditions().iterator().next();
            assertThat(readyCondition.getReason(), is(not(equalTo(Condition.Reasons.MIGRATION_CONFLICT))));
        });
    }

    /**
     * Test 5 (single-CR regression): with only one group's Console ever created, no
     * MigrationConflict is ever reported (guards against false positives from the guard).
     */
    @Test
    void testSingleGroupOnlyNeverReportsMigrationConflict() {
        createConsole(minimalMaintainedSpec());
        awaitDeploymentCreated(CONSOLE_NS);

        var console = client.resources(Console.class).inNamespace(CONSOLE_NS).withName(CONSOLE_NAME).get();
        var readyCondition = console.getStatus().getConditions().iterator().next();
        assertThat(readyCondition.getReason(), is(not(equalTo(Condition.Reasons.MIGRATION_CONFLICT))));
    }
}
