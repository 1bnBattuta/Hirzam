package com.hirzam.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public interface MessageService {
    Future<JsonArray> getLastMessages();
    Future<JsonObject> addMessage(String uname, String content);
}
