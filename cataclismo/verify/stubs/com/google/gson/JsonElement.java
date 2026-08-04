package com.google.gson;

public abstract class JsonElement {
    public JsonObject getAsJsonObject() { throw new UnsupportedOperationException(); }
    public String getAsString() { throw new UnsupportedOperationException(); }
}
