package com.battlemanson.codekanban;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

/** The editor format matches the extension's default-template JSON. */
final class BoardTemplate {
    private BoardTemplate() {}
    static String id() { return UUID.randomUUID().toString(); }
    static JSONObject copy(JSONObject object) throws JSONException { return new JSONObject(object.toString()); }
    static JSONObject empty(String title, String color) throws JSONException {
        JSONObject col = new JSONObject().put("id", id()).put("title", title).put("cards", new JSONArray());
        if (color != null && !color.isEmpty()) col.put("color", color);
        return col;
    }
    static JSONObject fromLegacy(JSONArray names, String[] colors) throws JSONException {
        JSONArray lists = new JSONArray();
        for (int i = 0; i < names.length(); i++) lists.put(empty(names.getString(i), colors[i % colors.length]));
        if (lists.length() == 0) lists.put(empty("To Do", colors[0]));
        return new JSONObject().put("lists", lists).put("archive", new JSONObject().put("lists", new JSONArray()).put("cards", new JSONArray())).put("settings", new JSONObject().put("labels", new JSONArray()));
    }
    static void validate(JSONObject template) throws JSONException {
        JSONArray lists = template.getJSONArray("lists");
        if (lists.length() == 0) throw new JSONException("Add at least one column.");
        for (int i = 0; i < lists.length(); i++) {
            JSONObject column = lists.getJSONObject(i);
            if (column.getString("title").trim().isEmpty()) throw new JSONException("Column names cannot be empty.");
            if (column.optString("id").isEmpty()) throw new JSONException("Each column needs an id.");
            if (column.has("color") && !column.getString("color").matches("#[0-9a-fA-F]{6}")) throw new JSONException("Colors must be #RRGGBB.");
            JSONArray cards = column.getJSONArray("cards");
            for (int j = 0; j < cards.length(); j++) {
                JSONObject card = cards.getJSONObject(j);
                if (card.optString("id").isEmpty() || card.optString("title").trim().isEmpty()) throw new JSONException("Starter work items need an id and title.");
                if (card.has("labels")) card.getJSONArray("labels");
                if (card.has("checkboxes")) card.getJSONArray("checkboxes");
                if (card.has("comments")) card.getJSONArray("comments");
            }
        }
        template.getJSONObject("archive").getJSONArray("lists");
        template.getJSONObject("archive").getJSONArray("cards");
        template.getJSONObject("settings").getJSONArray("labels");
    }
    static JSONObject createBoard(JSONObject template, String name, String parent) throws JSONException {
        validate(template);
        JSONObject source = copy(template), board = new JSONObject().put("id", id()).put("name", name).put("parent", parent);
        JSONArray columns = new JSONArray(), lists = source.getJSONArray("lists");
        for (int i = 0; i < lists.length(); i++) {
            JSONObject list = lists.getJSONObject(i);
            JSONObject column = new JSONObject().put("id", list.getString("id")).put("name", list.getString("title")).put("color", list.optString("color", ""));
            JSONArray cards = new JSONArray(), original = list.getJSONArray("cards");
            for (int j = 0; j < original.length(); j++) cards.put(convertCard(original.getJSONObject(j)));
            column.put("cards", cards).put("templateData", list); columns.put(column);
        }
        return board.put("columns", columns).put("templateData", source);
    }
    static JSONObject convertCard(JSONObject source) throws JSONException {
        JSONObject card = new JSONObject().put("id", source.optString("id", id())).put("title", source.optString("title"));
        if(source.has("parentId")&&!source.isNull("parentId"))card.put("parentId",source.getString("parentId"));
        card.put("description", source.optString("description", ""));
        card.put("due", source.optString("deadline", source.optString("dueDate", "")));
        for (String[] field : new String[][]{{"storyPoints","points"},{"priority","priority"},{"difficultyEstimation","difficulty"},{"adoWorkItem","ado"},{"smItem","sm"}})
            if (source.has(field[0]) && !source.isNull(field[0])) card.put(field[1], source.get(field[0]).toString());
        JSONArray labels = source.optJSONArray("labels"), checks = source.optJSONArray("checkboxes"), comments = source.optJSONArray("comments"), tasks = new JSONArray();
        StringBuilder labelText = new StringBuilder(), commentText = new StringBuilder();
        if (labels != null) for (int i=0; i<labels.length(); i++) { if (i>0) labelText.append(", "); labelText.append(labels.getJSONObject(i).optString("title")); }
        if (comments != null) for (int i=0; i<comments.length(); i++) { if (i>0) commentText.append('\n'); commentText.append(comments.getJSONObject(i).optString("comment")); }
        if (checks != null) for (int i=0; i<checks.length(); i++) { JSONObject task=checks.getJSONObject(i); tasks.put(new JSONObject().put("id",task.optString("id",id())).put("title",task.optString("title")).put("done",task.optBoolean("checked"))); }
        return card.put("labels",labelText.toString()).put("commentsText",commentText.toString()).put("tasks",tasks).put("templateData",source);
    }
    static boolean matches(JSONObject board, JSONObject template) {
        JSONArray columns=board.optJSONArray("columns"), lists=template.optJSONArray("lists");
        if (columns==null || lists==null || columns.length()!=lists.length()) return false;
        for(int i=0;i<columns.length();i++) { JSONObject a=columns.optJSONObject(i), b=lists.optJSONObject(i); if(a==null||b==null||!a.optString("name").equals(b.optString("title"))||!a.optString("color").equalsIgnoreCase(b.optString("color"))) return false; }
        return true;
    }
    static JSONArray migrate(JSONObject board, JSONObject template) throws JSONException {
        validate(template); JSONArray old=board.getJSONArray("columns"), lists=template.getJSONArray("lists"), result=new JSONArray(); boolean[] used=new boolean[old.length()];
        for(int i=0;i<lists.length();i++) { JSONObject target=lists.getJSONObject(i); JSONObject match=null;
            for(int j=0;j<old.length();j++) if(!used[j] && old.getJSONObject(j).optString("name").equals(target.optString("title"))) { match=old.getJSONObject(j);used[j]=true;break; }
            JSONObject column=new JSONObject().put("id",match==null?id():match.getString("id")).put("name",target.getString("title")).put("color",target.optString("color","")).put("cards",new JSONArray());
            if(match!=null&&match.has("collapsed"))column.put("collapsed",match.optBoolean("collapsed"));
            result.put(column);
        }
        for(int i=0;i<old.length();i++) { JSONObject source=old.getJSONObject(i), target=null;
            for(int j=0;j<result.length();j++) if(result.getJSONObject(j).getString("name").equals(source.optString("name"))) {target=result.getJSONObject(j);break;}
            if(target==null)target=result.getJSONObject(0); JSONArray cards=source.getJSONArray("cards");
            for(int j=0;j<cards.length();j++)target.getJSONArray("cards").put(cards.getJSONObject(j));
        }
        return result;
    }
}
