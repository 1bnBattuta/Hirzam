package com.hirzam.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public interface MessageService {

    Future<JsonArray>  getLastMessages();
    Future<JsonObject> addMessage(String username, String content);

    Future<JsonObject> getMessage(int id);
    Future<JsonObject> updateMessage(int id, String content);
    Future<Void>       deleteMessage(int id);
}