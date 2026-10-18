package org.meh.dnd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;

@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
public class ApiServer {
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    RestServer server;

    @GET
    @Path("/state")
    public Response state() {
        return server.currentGame()
                .map(game -> Response.ok(ApiMapper.state(game)).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Path("/commands")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response command(String body) {
        try {
            JsonNode command = mapper.readTree(body);
            ApiCommand.execute(command, server);
            server.broadcast("command.accepted");
            return Response.ok(new ApiModels.CommandResult(true, "", server.apiState())).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiModels.CommandResult(false, e.getMessage(), server.apiState()))
                    .build();
        }
    }

    @POST
    @Path("/character")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response character(String body) {
        try {
            server.createApiCharacter(mapper.readTree(body));
            server.broadcast("state.created");
            return Response.ok(server.apiState()).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/quest")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response quest(String body) {
        try {
            server.createApiQuest(mapper.readTree(body));
            server.broadcast("state.created");
            return Response.ok(server.apiState()).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/level-up")
    public Response levelUp() {
        try {
            server.apiLevelUp();
            server.broadcast("level-up");
            return Response.ok(server.apiState()).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/save")
    public Response save() throws IOException {
        return Response.ok(server.saveGame())
                .header("Content-Disposition", "attachment; filename=game.json")
                .build();
    }

    @POST
    @Path("/load")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response load(String body) {
        try {
            server.loadGame(body);
            server.broadcast("state.loaded");
            return Response.ok(server.apiState()).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
}
