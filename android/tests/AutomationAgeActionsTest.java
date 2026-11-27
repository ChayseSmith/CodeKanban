package com.battlemanson.codekanban;
import org.json.*;

public final class AutomationAgeActionsTest {
    static final long HOUR=3600000L,NOW=2_000_000_000_000L;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static JSONObject action(String type,String value)throws Exception{return new JSONObject().put("type",type).put("value",value);}
    static JSONObject rule(String id,String trigger,JSONArray actions)throws Exception{return new JSONObject().put("id",id).put("name",id).put("trigger",trigger).put("conditions",new JSONArray()).put("actions",actions);}
    static JSONObject fixture()throws Exception{
        JSONObject card=new JSONObject().put("id","item").put("title","Story").put("createdAt",NOW-100*HOUR).put("columnEnteredAt",NOW-48*HOUR);
        JSONObject board=new JSONObject().put("id","board").put("buttons",new JSONArray().put(new JSONObject().put("id","reset").put("label","Reset"))).put("columns",new JSONArray().put(new JSONObject().put("id","column").put("name","Doing").put("cards",new JSONArray().put(card))));
        JSONObject stale=rule("stale","conditions",new JSONArray().put(action("color","#EF4444")));stale.getJSONArray("conditions").put(new JSONObject().put("field","columnAgeHours").put("op","gte").put("value","24"));
        JSONObject reset=rule("reset","buttonTap",new JSONArray().put(action("clearColor","")).put(action("resetColumnAge",""))).put("buttonId","reset");
        return new JSONObject().put("boards",new JSONArray().put(board)).put("automations",new JSONArray().put(stale).put(reset));
    }
    static JSONObject card(JSONObject data)throws Exception{return AutomationEngine.items(data).get(0).card;}
    public static void main(String[] args)throws Exception{
        for(boolean resetFirst:new boolean[]{false,true}){
            JSONObject data=fixture();if(resetFirst){JSONArray rules=data.getJSONArray("automations");Object first=rules.get(0);rules.put(0,rules.get(1));rules.put(1,first);}
            AutomationEngine.run(data,BoardTemplate.copy(data),NOW,"device");check(card(data).has("automationColor"),"Stale rule initially colors item");
            JSONObject before=BoardTemplate.copy(data);AutomationEngine.run(data,before,NOW+HOUR,"device",new BoardButtons.Event("board","board","board","reset","tap"));
            check(!card(data).has("automationColor"),"Button clears color");check(card(data).getLong("columnEnteredAt")==NOW+HOUR,"Button resets column age");check(card(data).getLong("createdAt")==NOW-100*HOUR,"Column age reset preserves total age");
            check(!data.getJSONObject("automationState").getJSONObject("stale").optBoolean("board/item"),"Stale rule re-armed in the same save");
            // Simulate a restart and no intermediate check while the item ages again.
            data=new JSONObject(data.toString());before=BoardTemplate.copy(data);AutomationEngine.run(data,before,NOW+26*HOUR,"device");check(card(data).has("automationColor"),"Stale color returns after fresh interval even across restart");
        }
        JSONObject data=fixture(),before=BoardTemplate.copy(data);JSONArray rules=data.getJSONArray("automations");rules.put(0,rule("set","buttonTap",new JSONArray().put(action("setColumnAgeHours","0.5")).put(action("setItemAgeHours","12.25"))).put("buttonId","reset"));rules.remove(1);
        AutomationEngine.run(data,before,NOW,"device",new BoardButtons.Event("board","board","board","reset","tap"));check(card(data).getLong("columnEnteredAt")==NOW-HOUR/2,"Fractional column age supported");check(card(data).getLong("createdAt")==NOW-12*HOUR-HOUR/4,"Fractional total age supported");
        before=BoardTemplate.copy(data);rules.put(0,rule("item-reset","buttonTap",new JSONArray().put(action("resetItemAge",""))).put("buttonId","reset"));AutomationEngine.run(data,before,NOW+HOUR,"device",new BoardButtons.Event("board","board","board","reset","tap"));check(card(data).getLong("createdAt")==NOW+HOUR,"Total age can reset independently");check(card(data).getLong("columnEnteredAt")==NOW-HOUR/2,"Item age reset preserves column age");
        before=BoardTemplate.copy(data);rules.put(0,rule("fields","buttonTap",new JSONArray().put(action("title","New name")).put(action("description","New description")).put(action("due","2027-01-31"))).put("buttonId","reset"));AutomationEngine.run(data,before,NOW+2*HOUR,"device",new BoardButtons.Event("board","board","board","reset","tap"));JSONObject live=BoardTemplate.copy(before);AutomationEngine.adopt(live,data);check(card(live).optString("title").equals("New name")&&card(live).optString("description").equals("New description")&&card(live).optString("due").equals("2027-01-31"),"Other field actions persist to live items");
        for(String value:new String[]{"-1","NaN","Infinity","876001","bad"}){boolean invalid=false;try{AutomationEngine.validate(rule("bad","conditions",new JSONArray().put(action("setColumnAgeHours",value))));}catch(JSONException e){invalid=true;}check(invalid,"Invalid age rejected: "+value);}
        System.out.println("Age reset/set, stale-rule re-arming, restart, fractional age, field persistence and validation checks passed.");
    }
}
