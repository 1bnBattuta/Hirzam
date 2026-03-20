package com.hirzam;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

public class MainVerticle extends AbstractVerticle {
    
    @Override
    public void start(Promise<Void> startPromise) {
        // TODO: Hard coded values are to be evaded : Maybe we should
        //      use local env file ? For now it is probably fine
        DatabaseVerticle dbVerticle = new DatabaseVerticle(
            "jdbc:postgresql://localhost:5432/hirzamdb",
            "superuser",
            "The world revolving"
        );

        vertx.deployVerticle(dbVerticle)
        .compose(id -> {
            System.out.println("DatabaseVerticle started : " + id);
            return vertx.deployVerticle(new HttpVerticle(8080));
        })
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
