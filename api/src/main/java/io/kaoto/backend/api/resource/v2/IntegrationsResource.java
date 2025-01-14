package io.kaoto.backend.api.resource.v2;

import io.kaoto.backend.api.resource.model.FlowsWrapper;
import io.kaoto.backend.api.resource.v1.model.Integration;
import io.kaoto.backend.api.service.deployment.DeploymentService;
import io.kaoto.backend.api.service.dsl.DSLSpecification;
import io.kaoto.backend.api.service.step.parser.StepParserService;
import io.kaoto.backend.model.step.Step;
import io.quarkus.cache.CacheResult;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 🐱class IntegrationsResource
 * 🐱relationship compositionOf DeploymentService, 0..1
 * <p>
 * This endpoint will return the yaml needed to deploy
 * the related integration and the
 * endpoints to interact with deployments.
 */
@Path("/v2/integrations")
@ApplicationScoped
public class IntegrationsResource {

    private final Logger LOG = Logger.getLogger(IntegrationsResource.class);
    private DeploymentService deploymentService;
    private Instance<DSLSpecification> dslSpecifications;

    @Inject
    public void setDeploymentService(
            final DeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    @Inject
    public void setDslSpecifications(final Instance<DSLSpecification> dslSpecifications) {
        this.dslSpecifications = dslSpecifications;
    }

    /*
     * 🐱method CRDs: Map
     * 🐱param dsl: String
     * 🐱param integration: List<Integration>
     *
     * Idempotent operation that given an array of integrations, returns the corresponding CRDs.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("text/yaml")
    @Path("/")
    @CacheResult(cacheName = "api")
    @Operation(summary = "Get CRDs",
            description = "Returns the associated custom resource definitions. This is an idempotent operation.")
    public String crds(final @RequestBody FlowsWrapper request) {
        ensureUniqueNames(request);
        return deploymentService.crds(request.flows(), request.metadata());
    }

    /*
     * 🐱method integration: Map
     * 🐱param dsl: String
     * 🐱param crd: String
     *
     * Idempotent operation that given a CRD, returns the JSON representation.
     *
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes("text/yaml")
    @Path("/")
    @CacheResult(cacheName = "api")
    @Operation(summary = "Get Integration Object",
            description = "Given the associated custom resource definition, returns the JSON object."
                    + " This is an idempotent operation.")
    public FlowsWrapper integration(
            final @RequestBody String crd,
            final @Parameter(description = "DSL to use. For example: 'Kamelet Binding'.")
            @QueryParam("dsl") String dsl) {
        List<Integration> integrations = new ArrayList<>();
        Map<String, Object> metadata = new LinkedHashMap<>();
        FlowsWrapper answer = new FlowsWrapper(integrations, metadata, Map.of());

        boolean found = false;
        if (dsl != null) {
            for (DSLSpecification dslSpecification : dslSpecifications) {
                try {
                    if (dslSpecification.identifier().equalsIgnoreCase(dsl) && dslSpecification.appliesTo(crd)) {
                        var parsed = dslSpecification.getStepParserService().getParsedFlows(crd);
                        decorateIntegration(dsl, answer, parsed);
                        found = true;
                        break;
                    }
                } catch (Exception e) {
                    LOG.warn("Parser " + dslSpecification.getClass() + "threw an unexpected error.", e);
                }
            }
        }

        if (!found) {
            for (var dslSpecification : dslSpecifications) {
                try {
                    if (dslSpecification.appliesTo(crd)) {
                        var parsed = dslSpecification.getStepParserService().getParsedFlows(crd);
                        decorateIntegration(dslSpecification.identifier(), answer, parsed);
                        LOG.warn("Gurl, the DSL you gave me is so wrong. This is a " + dslSpecification.identifier()
                                + " not a " + dsl);
                        break;
                    }
                } catch (Exception e) {
                    LOG.trace("Parser " + dslSpecification.getClass() + "threw an unexpected error.", e);
                }
            }
        }

        ensureUniqueNames(answer);

        return answer;
    }

    private static void ensureUniqueNames(FlowsWrapper answer) {
        List<String> usedIds = new LinkedList<>();
        var name = "name";
        for (var flow : answer.flows()) {
            //Make sure we have a metadata set
            if (flow.getMetadata() == null) {
                flow.setMetadata(new LinkedHashMap<>());
            }
            //Make sure there is an id/name assigned to all flows
            if (!flow.getMetadata().containsKey(name)) {
                Random random = new Random();
                flow.getMetadata().put(name,
                        flow.getDsl().toLowerCase().replaceAll(" ", "") + random.nextInt(99));
            }
            //Make sure it is unique
            if (usedIds.contains(flow.getMetadata().get(name))) {
                Random random = new Random();
                flow.getMetadata().put(name, String.valueOf(flow.getMetadata().get(name)) + random.nextInt(99));
            }
            usedIds.add(String.valueOf(flow.getMetadata().get(name)));
        }
    }

    private void decorateIntegration(String dsl, FlowsWrapper flowsWrapper,
                                     List<StepParserService.ParseResult<Step>> parsed) {
        for (var result : parsed) {
            if (result.getSteps() == null) {
                if (result.getMetadata() != null) {
                    flowsWrapper.metadata().putAll(result.getMetadata());
                }
                continue;
            }
            Integration integration = new Integration();
            integration.setSteps(result.getSteps());
            integration.setMetadata(result.getMetadata());
            integration.setParameters(result.getParameters());
            integration.setDsl(dsl);

            flowsWrapper.flows().add(integration);
        }
    }

    @ServerExceptionMapper
    public Response mapException(final Exception x) {
        LOG.error("Error processing deployment.", x);

        return Response.status(Response.Status.BAD_REQUEST)
                .entity("Error processing deployment: " + x.getMessage())
                .type(MediaType.TEXT_PLAIN_TYPE)
                .build();
    }

}
