package com.hirzam;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;

public class MainVerticle extends AbstractVerticle {
    
    @Override
    public void start(Promise<Void> startPromise) {
        System.out.println("Hirzam Chat - Started.");
        startPromise.complete();
    }
}
