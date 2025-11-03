package com.test.video.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Json {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ObjectNode parse(String s) throws Exception {
        return (ObjectNode) MAPPER.readTree(s);
    }

    public static ObjectNode message(String type) {
        ObjectNode o = MAPPER.createObjectNode();
        o.put("type", type);
        return o;
    }

    public Json() {}

    public static String stringify(Object obj) {
        try { return MAPPER.writeValueAsString(obj); } catch (Exception e) { throw new RuntimeException(e); }
    }
}












