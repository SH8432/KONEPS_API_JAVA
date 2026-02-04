package com.softbase.nara.parser;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * 나라장터 API JSON 응답 파싱 전담.
 * 응답 구조(response/header/body/items) 변경 시 이 클래스만 수정하면 됨.
 */
public final class NaraResponseParser {

    /*
    response 객체 반환
    */
    public static JSONObject getResponse(String jsonData) throws Exception {
        JSONParser parser = new JSONParser();
        JSONObject root = (JSONObject) parser.parse(jsonData);
        JSONObject response = (JSONObject) root.get("response");
        if (response == null) throw new IllegalArgumentException("응답에 'response'가 없습니다.");
        return response;
    }

    /*
    header 객체 반환
    */
    public static JSONObject getResponseHeader(JSONObject response) {
        JSONObject header = (JSONObject) response.get("header");
        if (header == null) throw new IllegalArgumentException("응답에 'header'가 없습니다.");
        return header;
    }

    /*
    body 객체 반환
    */
    public static JSONObject getResponseBody(JSONObject response) {
        JSONObject body = (JSONObject) response.get("body");
        if (body == null) throw new IllegalArgumentException("응답에 'body'가 없습니다.");
        return body;
    }

    /*
    items 배열 반환
    */
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

    /*
    JSON 객체에서 지정된 키의 문자열 값 반환
    */
    public static String getAsString(JSONObject obj, String key) {
        Object v = obj.get(key);
        return (v == null) ? "" : String.valueOf(v);
    }

    private NaraResponseParser() {}
}
