package com.hirzam;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

public class DatabaseVerticle extends AbstractVerticle {
    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    // pool must be package visible
    Pool pool;

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
        
        // TODO: Remove magic number 10 maybe ??
        // Max size is to limit the number of connections with db
        PoolOptions poolOptions = new PoolOptions().setMaxSize(10);

        pool = JDBCPool.pool(vertx, connectOptions, poolOptions);

        createSchema()
            .onSuccess(x -> {
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
                "id         SERIAL PRIMARY KEY, "     +
                "username   VARCHAR(100) NOT NULL, "  +
                "content    TEXT NOT NULL, "          +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
            ")"
        )
        .execute()
        .mapEmpty();
    }
}
