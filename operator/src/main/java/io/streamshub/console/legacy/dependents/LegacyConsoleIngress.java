package io.streamshub.console.legacy.dependents;

import java.util.Collection;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.streamshub.console.legacy.api.v1alpha1.LegacyConsole;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressLoadBalancerStatus;
import io.fabric8.kubernetes.api.model.networking.v1.IngressStatus;
import io.fabric8.openshift.api.model.Route;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

import static io.streamshub.console.legacy.dependents.support.LegacyConfigSupport.resourceSupported;

@ApplicationScoped
@KubernetesDependent(informer = @Informer(labelSelector = LegacyConsoleResource.MANAGEMENT_SELECTOR))
public class LegacyConsoleIngress extends CRUDKubernetesDependentResource<Ingress, LegacyConsole> implements LegacyConsoleResource<Ingress> {

    public static final String NAME = "console-ingress";

    @Inject
    LegacyConsoleService service;

    public LegacyConsoleIngress() {
        super(Ingress.class);
    }

    @Override
    public String resourceName() {
        return NAME;
    }

    @Override
    protected Ingress desired(LegacyConsole primary, Context<LegacyConsole> context) {
        boolean tls = primary.getSpec().getTls() != null;
        String backendProtocol = tls ? "HTTPS" : "HTTP";
        int servicePort = tls ? 443 : 80;
        String serviceName = service.instanceName(primary);

        return load(context, "console.ingress.yaml", Ingress.class)
            .edit()
            .editMetadata()
                .withName(instanceName(primary))
                .withNamespace(primary.getMetadata().getNamespace())
                .withLabels(commonLabels("console"))
                .addToAnnotations("nginx.ingress.kubernetes.io/backend-protocol", backendProtocol)
            .endMetadata()
            .editSpec()
                // Plain Kubernetes (non-OCP) clusters don't need a class name; the
                // default ingress controller picks it up automatically.
                .withIngressClassName(null)
                .editDefaultBackend()
                    .editService()
                        .withName(serviceName)
                        .editPort()
                            .withNumber(servicePort)
                        .endPort()
                    .endService()
                .endDefaultBackend()
                .editFirstRule()
                    .withHost(primary.getSpec().getHostname())
                    .editHttp()
                        .editFirstPath()
                            .editBackend()
                                .editService()
                                    .withName(serviceName)
                                    .editPort()
                                        .withNumber(servicePort)
                                    .endPort()
                                .endService()
                            .endBackend()
                        .endPath()
                    .endHttp()
                .endRule()
            .endSpec()
            .build();
    }

    /**
     * Only create the plain Ingress on clusters that do NOT support OpenShift
     * Routes. On OpenShift / MicroShift, {@link LegacyConsoleRoute} is used instead.
     */
    public static class Precondition implements Condition<Ingress, LegacyConsole> {
        @Override
        public boolean isMet(DependentResource<Ingress, LegacyConsole> dependentResource, LegacyConsole primary, Context<LegacyConsole> context) {
            return !resourceSupported(context, Route.class);
        }
    }

    public static class Postcondition implements Condition<Ingress, LegacyConsole> {
        private static final Logger LOGGER = Logger.getLogger(Postcondition.class);

        @Override
        public boolean isMet(DependentResource<Ingress, LegacyConsole> dependentResource,
                LegacyConsole primary,
                Context<LegacyConsole> context) {

            boolean ready = dependentResource.getSecondaryResource(primary, context).map(this::isReady).orElse(false);
            context.managedWorkflowAndDependentResourceContext().put(NAME, ready);
            return ready;
        }

        private boolean isReady(Ingress ingress) {
            Boolean ready = Optional.ofNullable(ingress.getStatus())
                .map(IngressStatus::getLoadBalancer)
                .map(IngressLoadBalancerStatus::getIngress)
                .map(Collection::isEmpty)
                .map(Boolean.FALSE::equals)
                .orElse(false);

            LOGGER.debugf("Ingress %s ready: %s", ingress.getMetadata().getName(), ready);
            return ready;
        }
    }
}
