package com.hirzam;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.SockJSBridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;

public class HttpVerticle extends AbstractVerticle {

    private final int port;

    // ── Event Bus addresses ───────────────────────────────────────────────────
    static final String EB_GET_MESSAGES  = "db.getLastMessages";
    static final String EB_ADD_MESSAGE   = "db.addMessage";
    static final String EB_GET_MESSAGE   = "db.getMessage";
    static final String EB_UPDATE_MESSAGE = "db.updateMessage";
    static final String EB_DELETE_MESSAGE = "db.deleteMessage";

    // ── SockJS broadcast addresses ────────────────────────────────────────────
    static final String EB_NEW_MESSAGE     = "ws.newMessage";
    static final String EB_UPDATED_MESSAGE = "ws.updatedMessage";
    static final String EB_DELETED_MESSAGE = "ws.deletedMessage";

    public HttpVerticle(int port) {
        this.port = port;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        Router router = Router.router(vertx);

        // ── Body handler ──────────────────────────────────────────────────────
        router.route().handler(BodyHandler.create());

        // ── SockJS bridge ─────────────────────────────────────────────────────
        SockJSHandler sockJSHandler = SockJSHandler.create(vertx);
        SockJSBridgeOptions bridgeOptions = new SockJSBridgeOptions()
            .addOutboundPermitted(new PermittedOptions().setAddress(EB_NEW_MESSAGE))
            .addOutboundPermitted(new PermittedOptions().setAddress(EB_UPDATED_MESSAGE))
            .addOutboundPermitted(new PermittedOptions().setAddress(EB_DELETED_MESSAGE));

        router.route("/eventbus/*").subRouter(sockJSHandler.bridge(bridgeOptions));

        // ── REST routes ───────────────────────────────────────────────────────
        router.get("/api/messages").handler(ctx -> {
            vertx.eventBus().<String>request(EB_GET_MESSAGES, "")
                .onSuccess(reply -> ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(reply.body()))
                .onFailure(err -> sendError(ctx, 500, err.getMessage()));
        });

        router.post("/api/messages").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();

            if (body == null
                    || !body.containsKey("username")
                    || !body.containsKey("content")
                    || body.getString("username").isBlank()
                    || body.getString("content").isBlank()) {
                sendError(ctx, 400, "Fields 'username' and 'content' are required");
                return;
            }

            vertx.eventBus().<String>request(EB_ADD_MESSAGE, body.encode())
                .onSuccess(reply -> {
                    String messageJson = reply.body();
                    vertx.eventBus().publish(EB_NEW_MESSAGE, messageJson);
                    ctx.response()
                        .setStatusCode(201)
                        .putHeader("Content-Type", "application/json")
                        .end(messageJson);
                })
                .onFailure(err -> sendError(ctx, 500, err.getMessage()));
        });

        // ── Bonus routes ──────────────────────────────────────────────────────

        router.get("/api/message/:id").handler(ctx -> {
            int id = parseId(ctx);
            if (id < 0) return;

            vertx.eventBus().<String>request(EB_GET_MESSAGE,
                new JsonObject().put("id", id).encode())
                .onSuccess(reply -> ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(reply.body()))
                .onFailure(err -> sendError(ctx, 404, "Message not found"));
        });

        router.put("/api/message/:id").handler(ctx -> {
            int id = parseId(ctx);
            if (id < 0) return;

            JsonObject body = ctx.body().asJsonObject();
            if (body == null || body.getString("content", "").isBlank()) {
                sendError(ctx, 400, "Field 'content' is required");
                return;
            }

            JsonObject payload = new JsonObject()
                .put("id",      id)
                .put("content", body.getString("content"));

            vertx.eventBus().<String>request(EB_UPDATE_MESSAGE, payload.encode())
                .onSuccess(reply -> {
                    String messageJson = reply.body();
                    vertx.eventBus().publish(EB_UPDATED_MESSAGE, messageJson);
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(messageJson);
                })
                .onFailure(err -> sendError(ctx, 404, "Message not found"));
        });

        router.delete("/api/message/:id").handler(ctx -> {
            int id = parseId(ctx);
            if (id < 0) return;

            vertx.eventBus().<String>request(EB_DELETE_MESSAGE,
                new JsonObject().put("id", id).encode())
                .onSuccess(reply -> {
                    vertx.eventBus().publish(EB_DELETED_MESSAGE,
                        new JsonObject().put("id", id).encode());
                    ctx.response().setStatusCode(204).end();
                })
                .onFailure(err -> sendError(ctx, 404, "Message not found"));
        });

        // ── Static files (must be last) ───────────────────────────────────────
        router.route("/*").handler(
            StaticHandler.create("webroot").setIndexPage("index.html")
        );

        // ── Start server ──────────────────────────────────────────────────────
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(port)
            .onSuccess(server -> {
                System.out.println("HTTP Server started on port : " + port);
                startPromise.complete();
            })
            .onFailure(err -> {
                System.err.println("Failed to start HTTP server : " + err.getMessage());
                startPromise.fail(err);
            });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int parseId(io.vertx.ext.web.RoutingContext ctx) {
        try {
            return Integer.parseInt(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            sendError(ctx, 400, "Invalid id");
            return -1;
        }
    }

    private void sendError(io.vertx.ext.web.RoutingContext ctx,
                           int status, String message) {
        ctx.response()
            .setStatusCode(status)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("error", message).encode());
    }
}