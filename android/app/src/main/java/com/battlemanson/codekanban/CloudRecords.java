package com.battlemanson.codekanban;

import org.json.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Version-one wire format shared by mobile and future VS Code clients. */
final class CloudRecords {
    static final String DELETED="{\"deleted\":true}";
    private CloudRecords(){}
    static String canonical(Object value)throws JSONException{
        if(value instanceof JSONObject){JSONObject object=(JSONObject)value;List<String> keys=new ArrayList<>();Iterator<String> iterator=object.keys();while(iterator.hasNext())keys.add(iterator.next());Collections.sort(keys);StringBuilder out=new StringBuilder("{");for(String key:keys){if(out.length()>1)out.append(',');out.append(JSONObject.quote(key)).append(':').append(canonical(object.get(key)));}return out.append('}').toString();}
        if(value instanceof JSONArray){JSONArray array=(JSONArray)value;StringBuilder out=new StringBuilder("[");for(int i=0;i<array.length();i++){if(i>0)out.append(',');out.append(canonical(array.get(i)));}return out.append(']').toString();}
        return value==null||value==JSONObject.NULL?"null":value instanceof String?JSONObject.quote((String)value):value.toString();
    }
    static String id(String kind,String board,String entity){try{byte[] hash=MessageDigest.getInstance("SHA-256").digest((kind+"\n"+board+"\n"+entity).getBytes(StandardCharsets.UTF_8));StringBuilder id=new StringBuilder();for(byte b:hash)id.append(String.format(Locale.ROOT,"%02x",b&255));return id.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    static Map<String,String> encode(JSONObject root)throws JSONException{
        Map<String,String> records=new TreeMap<>();JSONArray boards=root.getJSONArray("boards");
        for(int b=0;b<boards.length();b++){JSONObject board=boards.getJSONObject(b);if(board.has("_shared"))continue;JSONObject meta=BoardTemplate.copy(board);String bid=board.getString("id");meta.remove("columns");
            add(records,id("board",bid,bid),new JSONObject().put("kind","board").put("boardId",bid).put("order",b).put("data",meta));JSONArray columns=board.getJSONArray("columns");
            for(int c=0;c<columns.length();c++){JSONObject column=columns.getJSONObject(c),cm=BoardTemplate.copy(column);String cid=column.getString("id");cm.remove("cards");add(records,id("column",bid,cid),new JSONObject().put("kind","column").put("boardId",bid).put("order",c).put("data",cm));JSONArray cards=column.getJSONArray("cards");
                for(int i=0;i<cards.length();i++){JSONObject card=cards.getJSONObject(i);add(records,id("item",bid,card.getString("id")),new JSONObject().put("kind","item").put("boardId",bid).put("columnId",cid).put("order",i).put("data",card));}
            }
        }return records;
    }
    private static void add(Map<String,String> records,String id,JSONObject value)throws JSONException{String json=canonical(value);if(json.getBytes(StandardCharsets.UTF_8).length>700000)throw new JSONException("A board, column or work item is too large to sync (700 KB limit). Export a backup before reducing its content.");if(records.put(id,json)!=null)throw new JSONException("Duplicate board, column or work-item IDs prevent safe synchronization.");}
    static JSONArray decode(Map<String,String> records)throws JSONException{
        List<JSONObject> values=new ArrayList<>();for(String json:records.values()){JSONObject value=new JSONObject(json);if(value.optBoolean("deleted"))continue;String kind=value.getString("kind");if(!Arrays.asList("board","column","item").contains(kind))throw new JSONException("Unsupported cloud record kind.");value.getJSONObject("data").getString("id");values.add(value);}
        values.sort((a,b)->{int order=Integer.compare(a.optInt("order"),b.optInt("order"));return order!=0?order:a.optJSONObject("data").optString("id").compareTo(b.optJSONObject("data").optString("id"));});
        JSONArray result=new JSONArray();Map<String,JSONObject> boards=new HashMap<>(),columns=new HashMap<>();
        for(JSONObject record:values)if(record.getString("kind").equals("board")){JSONObject board=BoardTemplate.copy(record.getJSONObject("data"));board.put("columns",new JSONArray());result.put(board);boards.put(record.getString("boardId"),board);}
        for(JSONObject record:values)if(record.getString("kind").equals("column")){String bid=record.getString("boardId");JSONObject board=boards.get(bid);if(board==null)continue;JSONObject column=BoardTemplate.copy(record.getJSONObject("data"));column.put("cards",new JSONArray());board.getJSONArray("columns").put(column);columns.put(bid+"/"+column.getString("id"),column);}
        for(JSONObject record:values)if(record.getString("kind").equals("item")){JSONObject column=columns.get(record.getString("boardId")+"/"+record.getString("columnId"));if(column!=null)column.getJSONArray("cards").put(BoardTemplate.copy(record.getJSONObject("data")));}
        JSONObject root=new JSONObject().put("boards",result);BoardButtons.validateData(root);return result;
    }
    static Map<String,String> changes(Map<String,String> before,Map<String,String> after){Map<String,String> changes=new TreeMap<>();for(String id:after.keySet())if(!Objects.equals(before.get(id),after.get(id)))changes.put(id,after.get(id));for(String id:before.keySet())if(!after.containsKey(id))changes.put(id,DELETED);return changes;}
}
