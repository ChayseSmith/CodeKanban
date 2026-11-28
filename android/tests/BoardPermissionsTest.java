package com.battlemanson.codekanban;
import org.json.*;
import java.util.*;

public final class BoardPermissionsTest {
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    static JSONObject policy(String owner,String parent,String... ancestors)throws Exception{return new JSONObject().put("ownerId",owner).put("parentId",parent).put("ancestors",new JSONArray(Arrays.asList(ancestors))).put("grants",new JSONObject()).put("emails",new JSONObject()).put("deleted",false);}
    static JSONObject local()throws Exception{return new JSONObject().put("boards",new JSONArray().put(new JSONObject().put("id","shared:root").put("name","Root").put("parent","").put("_shared",new JSONObject().put("id","root").put("ownerId","alice").put("parentId","")).put("columns",new JSONArray().put(new JSONObject().put("id","col").put("name","Todo").put("cards",new JSONArray().put(new JSONObject().put("id","card").put("title","Story")))))));}
    static JSONObject first(JSONObject d)throws Exception{return d.getJSONArray("boards").getJSONObject(0);}
    public static void main(String[] args)throws Exception{
        Map<String,JSONObject> metadata=new HashMap<>();JSONObject root=policy("alice",""),child=policy("alice","root","root"),grand=policy("alice","child","root","child");metadata.put("root",root);metadata.put("child",child);metadata.put("grand",grand);root.getJSONObject("grants").put("bob","write");
        check(BoardPermissions.role("bob","grand",metadata).equals("write"),"Parent write inherited through nested boards");
        child.getJSONObject("grants").put("bob","read");check(BoardPermissions.role("bob","grand",metadata).equals("read"),"Nearer read overrides parent write");
        grand.getJSONObject("grants").put("bob","write");check(BoardPermissions.role("bob","grand",metadata).equals("write"),"Deeper explicit write overrides inherited read");
        child.getJSONObject("grants").put("bob","none");check(BoardPermissions.role("bob","child",metadata).equals("none"),"Explicit deny overrides inherited grant");check(BoardPermissions.role("bob","grand",metadata).equals("write"),"Explicit descendant permission takes precedence over ancestor deny");
        grand.getJSONObject("grants").remove("bob");check(BoardPermissions.role("bob","grand",metadata).equals("none"),"Removing override restores inherited deny");
        root.getJSONObject("grants").put("alice","none");check(BoardPermissions.role("alice","grand",metadata).equals("owner"),"Owner cannot be locked out");check(BoardPermissions.role("stranger","root",metadata).equals("none"),"No grant fails closed");
        metadata.remove("child");check(BoardPermissions.role("bob","grand",metadata).equals("none"),"Unknown ancestor fails closed");metadata.put("child",child);
        JSONObject before=local(),after=BoardTemplate.copy(before);root.getJSONObject("grants").put("bob","read");first(after).getJSONArray("columns").getJSONObject(0).getJSONArray("cards").getJSONObject(0).put("title","Unauthorized");boolean rejected=false;try{BoardPermissions.checkWrites(before,after,"bob",metadata);}catch(JSONException e){rejected=true;}check(rejected,"Read-only local edits blocked before save");
        after=BoardTemplate.copy(before);first(after).put("boardButtonsCollapsed",false).getJSONArray("columns").getJSONObject(0).put("collapsed",true);check(BoardPermissions.checkWrites(before,after,"bob",metadata),"Read-only viewers may collapse sections locally");
        root.getJSONObject("grants").put("bob","write");after=BoardTemplate.copy(before);first(after).getJSONArray("columns").getJSONObject(0).getJSONArray("cards").getJSONObject(0).put("title","Allowed");check(BoardPermissions.checkWrites(before,after,"bob",metadata),"Editor can change work items");
        after=BoardTemplate.copy(before);first(after).put("name","Changed settings");rejected=false;try{BoardPermissions.checkWrites(before,after,"bob",metadata);}catch(JSONException e){rejected=true;}check(rejected,"Editor cannot change owner-managed board settings");check(BoardPermissions.checkWrites(before,after,"alice",metadata),"Owner can change board settings");
        check(CloudRecords.encode(before).isEmpty(),"Shared records never leak into private account uploads");Map<String,String> shared=BoardPermissions.records(first(before));JSONObject remote=BoardPermissions.localBoard("root",root,shared,Collections.singleton("root"));check(remote.optString("id").equals("shared:root")&&remote.getJSONObject("_shared").getString("ownerId").equals("alice"),"Shared board identities are namespaced and authoritative");
        JSONObject draft=BoardTemplate.copy(before);JSONArray actions=new JSONArray().put(new JSONObject().put("type","color").put("value","#EF4444"));draft.put("automations",new JSONArray().put(new JSONObject().put("id","test").put("name","Test").put("trigger","conditions").put("conditions",new JSONArray()).put("actions",actions)));AutomationEngine.run(draft,before,System.currentTimeMillis(),"device",null,id->false);check(!first(draft).getJSONArray("columns").getJSONObject(0).getJSONArray("cards").getJSONObject(0).has("automationColor"),"Automations skip read-only boards");
        System.out.println("Board inheritance, nested overrides, owner guarantees, read-only saves, local view state and sync isolation passed.");
    }
}
