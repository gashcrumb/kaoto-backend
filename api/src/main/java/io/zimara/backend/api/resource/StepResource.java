package io.zimara.backend.api.resource;

import io.zimara.backend.api.Catalog;
import io.zimara.backend.model.Step;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Collection;

/**
 * 🐱class StepResource
 * This endpoint will return steps based on the parameters.
 */
@Path("/step")
@ApplicationScoped
public class StepResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/id/{id}")
    public Step stepById(@PathParam("id") String id) {
        return Catalog.getReadOnlyCatalog().searchStepByID(id);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/name/{name}")
    public Collection<Step> stepsByName(@PathParam("name") String name) {
        return Catalog.getReadOnlyCatalog().searchStepsByName(name);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Collection<Step> allSteps() {
        return Catalog.getReadOnlyCatalog().getAll();
    }
}