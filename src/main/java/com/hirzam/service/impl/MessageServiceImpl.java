package com.hirzam.service.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

import com.hirzam.service.MessageService;

public class MessageServiceImpl implements MessageService {

    private final Pool pool;

    public MessageServiceImpl(Pool pool) {
        this.pool = pool;
    }

    // ── getLastMessages ───────────────────────────────────────────────────────

    @Override
    public Future<JsonArray> getLastMessages() {
        return pool.query(
            "SELECT id, username, content, created_at, updated, deleted " +
            "FROM messages " +
            "WHERE deleted = FALSE " +
            "ORDER BY created_at DESC LIMIT 20"
        )
        .execute()
        .map(rows -> {
            JsonArray result = new JsonArray();
            for (Row row : rows) {
                result.add(rowToJson(row));
            }
            return result;
        });
    }

    // ── addMessage ────────────────────────────────────────────────────────────

    @Override
    public Future<JsonObject> addMessage(String username, String content) {
        return pool.withTransaction(conn ->
            conn.preparedQuery(
                "INSERT INTO messages (username, content) VALUES (?, ?)"
            )
            .execute(Tuple.of(username, content))
            .compose(ignored ->
                conn.query(
                    "SELECT id, username, content, created_at, updated, deleted " +
                    "FROM messages ORDER BY id DESC LIMIT 1"
                )
                .execute()
            )
            .map(rows -> rowToJson(rows.iterator().next()))
        );
    }

    // ── getMessage ────────────────────────────────────────────────────────────

    @Override
    public Future<JsonObject> getMessage(int id) {
        return pool.preparedQuery(
            "SELECT id, username, content, created_at, updated, deleted " +
            "FROM messages WHERE id = ? AND deleted = FALSE"
        )
        .execute(Tuple.of(id))
        .map(rows -> {
            if (!rows.iterator().hasNext()) {
                throw new RuntimeException("Message not found");
            }
            return rowToJson(rows.iterator().next());
        });
    }

    // ── updateMessage ─────────────────────────────────────────────────────────

    @Override
    public Future<JsonObject> updateMessage(int id, String content) {
        return pool.withTransaction(conn ->
            conn.preparedQuery(
                "UPDATE messages SET content = ?, updated = TRUE " +
                "WHERE id = ? AND deleted = FALSE"
            )
            .execute(Tuple.of(content, id))
            .compose(res -> {
                if (res.rowCount() == 0) {
                    throw new RuntimeException("Message not found or already deleted");
                }
                return conn.preparedQuery(
                    "SELECT id, username, content, created_at, updated, deleted " +
                    "FROM messages WHERE id = ?"
                )
                .execute(Tuple.of(id));
            })
            .map(rows -> rowToJson(rows.iterator().next()))
        );
    }

    // ── deleteMessage ─────────────────────────────────────────────────────────

    @Override
    public Future<Void> deleteMessage(int id) {
        return pool.preparedQuery(
            "UPDATE messages SET deleted = TRUE WHERE id = ? AND deleted = FALSE"
        )
        .execute(Tuple.of(id))
        .map(res -> {
            if (res.rowCount() == 0) {
                throw new RuntimeException("Message not found or already deleted");
            }
            return null;
        });
    }

    // ── rowToJson ─────────────────────────────────────────────────────────────

    private JsonObject rowToJson(Row row) {
        return new JsonObject()
            .put("id",         row.getInteger("id"))
            .put("username",   row.getString("username"))
            .put("content",    row.getString("content"))
            .put("created_at", row.getLocalDateTime("created_at").toString())
            .put("updated",    row.getBoolean("updated"))
            .put("deleted",    row.getBoolean("deleted"));
    }
}