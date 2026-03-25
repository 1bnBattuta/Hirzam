package com.hirzam.service.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import com.hirzam.service.MessageService;

public class MessageServiceImpl implements MessageService{

    private final Pool pool;

    public MessageServiceImpl(Pool pool) {
        this.pool = pool;
    }

    @Override
    public Future<JsonArray> getLastMessages() {
        return pool.query(
                "SELECT id, username, content, created_at " + 
                "FROM messages " + 
                "ORDER BY created_at DESC LIMIT 20"
            ).execute()
             .map(rows -> {
                JsonArray result = new JsonArray();
                for (Row row : rows) {
                    result.add(rowToJson(row));
                }
                return result;
            });
    }

    @Override
    public Future<JsonObject> addMessage(String username, String content) {
        return pool
            .preparedQuery(
                "INSERT INTO messages (username, content) " +
                "VALUES ($1, $2) " +
                "RETURNING id, username, content, created_at"
            )
            .execute(Tuple.of(username, content))
            .map(rows -> rowToJson(rows.iterator().next()));
   }

    private JsonObject rowToJson(Row row) {
        return new JsonObject()
        .put("id",         row.getInteger("id"))
        .put("username",   row.getString("username"))
        .put("content",    row.getString("content"))
        .put("created_at", row.getLocalDateTime("created_at").toString());
    }
}
