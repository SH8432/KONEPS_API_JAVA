package com.example.nara.parser;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * 나라장터 API JSON 응답 파싱 전담.
 * 응답 구조(response/header/body/items) 변경 시 이 클래스만 수정하면 됨.
 */
public final class NaraResponseParser {

    public static JSONObject getResponse(String jsonData) throws Exception {
        JSONParser parser = new JSONParser();
        JSONObject root = (JSONObject) parser.parse(jsonData);
        JSONObject response = (JSONObject) root.get("response");
        if (response == null) throw new IllegalArgumentException("응답에 'response'가 없습니다.");
        return response;
    }

    public static JSONObject getResponseHeader(JSONObject response) {
        JSONObject header = (JSONObject) response.get("header");
        if (header == null) throw new IllegalArgumentException("응답에 'header'가 없습니다.");
        return header;
    }

    public static JSONObject getResponseBody(JSONObject response) {
        JSONObject body = (JSONObject) response.get("body");
        if (body == null) throw new IllegalArgumentException("응답에 'body'가 없습니다.");
        return body;
    }

    @SuppressWarnings("unchecked")
    public static JSONArray getItemArray(JSONObject body) {
        Object itemsObj = body.get("items");
        if (itemsObj == null) return new JSONArray();

        if (itemsObj instanceof JSONObject) {
            Object itemObj = ((JSONObject) itemsObj).get("item");
            if (itemObj instanceof JSONArray) return (JSONArray) itemObj;
            if (itemObj instanceof JSONObject) {
                JSONArray arr = new JSONArray();
                arr.add(itemObj);
                return arr;
            }
            return new JSONArray();
        }

        if (itemsObj instanceof JSONArray) return (JSONArray) itemsObj;
        return new JSONArray();
    }

    public static String getAsString(JSONObject obj, String key) {
        Object v = obj.get(key);
        return (v == null) ? "" : String.valueOf(v);
    }

    private NaraResponseParser() {}
}
