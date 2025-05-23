package org.acme.extra;

import java.net.URI;

import jakarta.enterprise.event.Observes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import jakarta.ws.rs.core.Response.Status;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Pool;

@Path("fruits")
public class FruitResource {

    private final Pool client;
    private final boolean schemaCreate;

    public FruitResource(Pool client, @ConfigProperty(name = "myapp.schema.create", defaultValue = "true") boolean schemaCreate) {
        this.client = client;
        this.schemaCreate = schemaCreate;
    }

    void initdb(@Observes StartupEvent ev) {
        if (schemaCreate) {
            client.query("DROP TABLE IF EXISTS fruits").execute()
                    .flatMap(r -> client.query("CREATE TABLE fruits (id SERIAL PRIMARY KEY, name TEXT NOT NULL)")
                            .execute())
                    .flatMap(r -> client.query("INSERT INTO fruits (name) VALUES ('Orange')").execute())
                    .flatMap(r -> client.query("INSERT INTO fruits (name) VALUES ('Pear')").execute())
                    .flatMap(r -> client.query("INSERT INTO fruits (name) VALUES ('Apple')").execute())
                    .await().indefinitely();
        }
    }

    @GET
    public Uni<Response> get() {
        return Fruit.findAll(client)
                .onItem().transform(Response::ok)
                .onItem().transform(ResponseBuilder::build);
    }

    @GET
    @Path("{id}")
    public Uni<Response> getSingle(Long id) {
        return Fruit.findById(client, id)
                .onItem().transform(fruit -> fruit != null ? Response.ok(fruit) : Response.status(Status.NOT_FOUND))
                .onItem().transform(ResponseBuilder::build);
    }

    @POST
    public Uni<Response> create(Fruit fruit) {
        return fruit.save(client)
                .onItem().transform(id -> URI.create("/fruits/" + id))
                .onItem().transform(uri -> Response.created(uri).build());
    }

    @PUT
    @Path("{id}")
    public Uni<Response> update(Long id, Fruit fruit) {
        return fruit.update(client)
                .onItem().transform(updated -> updated ? Status.OK : Status.NOT_FOUND)
                .onItem().transform(status -> Response.status(status).build());
    }

    @DELETE
    @Path("{id}")
    public Uni<Response> delete(Long id) {
        return Fruit.delete(client, id)
                .onItem().transform(deleted -> deleted ? Status.NO_CONTENT : Status.NOT_FOUND)
                .onItem().transform(status -> Response.status(status).build());
    }
}
