package com.hirzam;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

public class MainVerticle extends AbstractVerticle {
    
    @Override
    public void start(Promise<Void> startPromise) {
        vertx.deployVerticle(new HttpVerticle(8080))
        .onSuccess(id -> {
            System.out.println("HttpVerticle started : " + id);
            startPromise.complete();
        })
        .onFailure(startPromise::fail);
    }

    public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new MainVerticle())
        .onSuccess(id -> System.out.println("MainVerticle started : " + id))
        .onFailure(err -> System.err.println("Failed : " + err.getMessage()));
    }
}
