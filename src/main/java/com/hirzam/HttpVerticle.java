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

    // Event Bus addresses
    static final String EB_GET_MESSAGES = "db.getLastMessages";
    static final String EB_ADD_MESSAGE  = "db.addMessage";
    static final String EB_NEW_MESSAGE  = "ws.newMessage"; // broadcast address

    public HttpVerticle(int port) {
        this.port = port;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        Router router = Router.router(vertx);

        // --- Body handler (must be before any POST/PUT route) ---
        router.route().handler(BodyHandler.create());

        // --- SockJS bridge ---
        SockJSHandler sockJSHandler = SockJSHandler.create(vertx);
        SockJSBridgeOptions bridgeOptions = new SockJSBridgeOptions()
            // allow clients to receive messages on ws.newMessage
            .addOutboundPermitted(new PermittedOptions().setAddress(EB_NEW_MESSAGE));

        router.route("/eventbus/*").subRouter(sockJSHandler.bridge(bridgeOptions));

        // --- REST API ---
        router.get("/api/messages").handler(ctx -> {
            vertx.eventBus().<String>request(EB_GET_MESSAGES, "")
              .onSuccess(reply -> {
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(reply.body());
            })
            .onFailure(err -> {
                ctx.response()
                    .setStatusCode(500)
                    .end(new JsonObject().put("error", err.getMessage()).encode());
            });
        });

        router.post("/api/messages").handler(ctx -> {
            JsonObject body = ctx.body().asJsonObject();

            if (body == null
                    || !body.containsKey("username")
                    || !body.containsKey("content")
                    || body.getString("username").isBlank()
                    || body.getString("content").isBlank()) {
                ctx.response()
                    .setStatusCode(400)
                    .end(new JsonObject()
                        .put("error", "Fields 'username' and 'content' are required")
                        .encode());
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
              .onFailure(err -> {
                  ctx.response()
                      .setStatusCode(500)
                      .end(new JsonObject().put("error", err.getMessage()).encode());
              });
        });

        // --- Static files (must be last) ---
        router.route("/*").handler(
            StaticHandler.create("webroot").setIndexPage("index.html")
        );

        // --- Start server ---
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
}