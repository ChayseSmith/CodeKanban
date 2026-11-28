package com.battlemanson.codekanban;
import org.json.*;
import java.util.*;

/** Durable local outbox. A server echo must never overwrite a newer unsent local edit. */
final class CloudReplica {
    final Map<String,String> records=new TreeMap<>(),lastLocal=new TreeMap<>();
    final Map<String,Write> pending=new TreeMap<>();
    static final class Write {final String json,token;Write(String json,String token){this.json=json;this.token=token;}}
    void local(Map<String,String> input)throws JSONException{
        Map<String,String> next=new TreeMap<>(input);
        // Removing/moving one card shifts array indexes of its neighbors. Keep their
        // wire order stable so that move cannot overwrite unrelated card content.
        for(String id:input.keySet())if(lastLocal.containsKey(id)){JSONObject before=new JSONObject(lastLocal.get(id)),after=new JSONObject(input.get(id));if(after.optString("kind").equals("item")&&before.optString("columnId").equals(after.optString("columnId"))){after.put("order",before.optInt("order"));next.put(id,CloudRecords.canonical(after));}}
        for(Map.Entry<String,String> change:CloudRecords.changes(lastLocal,next).entrySet()){records.put(change.getKey(),change.getValue());pending.put(change.getKey(),new Write(change.getValue(),UUID.randomUUID().toString()));}lastLocal.clear();lastLocal.putAll(next);}
    void remote(Map<String,String> incoming,boolean authoritative){for(Map.Entry<String,String> e:incoming.entrySet())if(!pending.containsKey(e.getKey()))records.put(e.getKey(),e.getValue());if(authoritative)for(String id:new ArrayList<>(records.keySet()))if(!incoming.containsKey(id)&&!pending.containsKey(id))records.put(id,CloudRecords.DELETED);}
    void acknowledged(String id,String token){Write write=pending.get(id);if(write!=null&&write.token.equals(token))pending.remove(id);}
    JSONObject json()throws JSONException{JSONObject writes=new JSONObject();for(Map.Entry<String,Write> e:pending.entrySet())writes.put(e.getKey(),new JSONObject().put("json",e.getValue().json).put("token",e.getValue().token));return new JSONObject().put("version",1).put("records",new JSONObject(records)).put("lastLocal",new JSONObject(lastLocal)).put("pending",writes);}
    static CloudReplica read(JSONObject state)throws JSONException{if(state.getInt("version")!=1)throw new JSONException("Unsupported cloud cache version.");CloudReplica replica=new CloudReplica();copy(state.getJSONObject("records"),replica.records);copy(state.getJSONObject("lastLocal"),replica.lastLocal);JSONObject writes=state.getJSONObject("pending");Iterator<String> keys=writes.keys();while(keys.hasNext()){String key=keys.next();JSONObject write=writes.getJSONObject(key);replica.pending.put(key,new Write(write.getString("json"),write.getString("token")));}return replica;}
    private static void copy(JSONObject object,Map<String,String> into)throws JSONException{Iterator<String> keys=object.keys();while(keys.hasNext()){String key=keys.next();into.put(key,object.getString(key));}}
}
