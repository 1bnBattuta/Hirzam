package com.hirzam;

import com.hirzam.service.MessageService;
import com.hirzam.service.impl.MessageServiceImpl;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

public class DatabaseVerticle extends AbstractVerticle {
    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    // pool and messageService must be package visible
    Pool pool;
    MessageService messageService;

    public DatabaseVerticle(String dbUrl, String dbUser, String dbPassword) {
        this.dbUrl      = dbUrl;
        this.dbUser     = dbUser;
        this.dbPassword = dbPassword;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        JDBCConnectOptions connectOptions = new JDBCConnectOptions()
            .setJdbcUrl(dbUrl)
            .setUser(dbUser)
            .setPassword(dbPassword);
        
        // Max size is to limit the number of connections with db
        PoolOptions poolOptions = new PoolOptions().setMaxSize(10);

        pool = JDBCPool.pool(vertx, connectOptions, poolOptions);
        messageService = new MessageServiceImpl(pool);

        createSchema()
            .onSuccess(x -> {
                registerConsumers();
                System.out.println("DatabaseVerticle started - db ready");
                startPromise.complete();
            })
            .onFailure(err -> {
                System.err.println("Error DB : " + err.getMessage());
                startPromise.fail(err);
            });
    }

    private io.vertx.core.Future<Void> createSchema() {
        return pool.query(
            "CREATE TABLE IF NOT EXISTS messages (" +
                "id         SERIAL PRIMARY KEY, "              +
                "username   VARCHAR(100) NOT NULL, "           +
                "content    TEXT NOT NULL, "                   +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updated    BOOLEAN NOT NULL DEFAULT FALSE, "  +
                "deleted    BOOLEAN NOT NULL DEFAULT FALSE"    +
            ")"
        )
        .execute()
        .mapEmpty();
    }

    private void registerConsumers() {

        // Consumer 1 — GET last messages
        vertx.eventBus().<String>consumer(HttpVerticle.EB_GET_MESSAGES, msg -> {
        messageService.getLastMessages()
            .onSuccess(arr -> msg.reply(arr.encode()))
            .onFailure(err -> msg.fail(500, err.getMessage()));
        });

        // Consumer 2 — ADD a new message
        vertx.eventBus().<String>consumer(HttpVerticle.EB_ADD_MESSAGE, msg -> {
            JsonObject body = new JsonObject(msg.body());
            String username  = body.getString("username");
            String content   = body.getString("content");

            messageService.addMessage(username, content)
                .onSuccess(json -> msg.reply(json.encode()))
                .onFailure(err  -> {
                    err.printStackTrace();
                    msg.fail(500, err.getMessage());
                    
                }
                );
                    
        });
    }
}
