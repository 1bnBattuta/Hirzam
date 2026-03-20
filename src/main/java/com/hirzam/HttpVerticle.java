package com.hirzam;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;

public class HttpVerticle extends AbstractVerticle {

  private final int port;

  public HttpVerticle(int port) {
    this.port = port;
  }

  @Override
  public void start(Promise<Void> startPromise) {
    Router router = Router.router(vertx);

    router.route("/*").handler(
      StaticHandler.create("webroot").setIndexPage("index.html")
    );

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
