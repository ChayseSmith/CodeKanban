package com.battlemanson.codekanban;

import org.json.*;
import java.util.*;

/** Deterministic rule evaluation on a caller-owned draft. No Android or network dependency. */
final class AutomationEngine {
    static final String[] TRIGGERS={"created","updated","moved","conditions","buttonTap","buttonDoubleTap","buttonLongPress"};
    static final String[] FIELDS={"column","previousColumn","ageHours","columnAgeHours","title","description","labels","priority","points","difficulty","overdue","tasksComplete"};
    static final String[] OPS={"eq","ne","contains","gte","lte"};
    static final String[] ACTIONS={"color","clearColor","completeTasks","reopenTasks","reminder","label","priority","points","difficulty","resetColumnAge","setColumnAgeHours","resetItemAge","setItemAgeHours","title","description","due","clearDue"};
    static final class Item {
        final JSONObject board,column,card;
        Item(JSONObject b,JSONObject c,JSONObject w){board=b;column=c;card=w;}
        String key(){return board.optString("id")+"/"+card.optString("id");}
    }
    private AutomationEngine(){}
    static List<Item> items(JSONObject data)throws JSONException{
        List<Item> items=new ArrayList<>();JSONArray boards=data.optJSONArray("boards");if(boards==null)return items;
        for(int b=0;b<boards.length();b++){JSONObject board=boards.getJSONObject(b);for(WorkItemRelations.Entry e:WorkItemRelations.entries(board))items.add(new Item(board,e.column,e.card));}return items;
    }
    static void validate(JSONObject rule)throws JSONException{
        if(rule.optString("id").isEmpty()||rule.optString("name").trim().isEmpty())throw new JSONException("Give the automation a name.");
        if(!Arrays.asList(TRIGGERS).contains(rule.optString("trigger")))throw new JSONException("Choose a supported trigger.");
        if(!Arrays.asList("all","any").contains(rule.optString("match","all")))throw new JSONException("Choose All or Any conditions.");
        if(rule.optString("trigger").startsWith("button")){if(!BoardButtons.validId(rule.optString("buttonId")))throw new JSONException("Enter a valid button ID to match.");if(!Arrays.asList(BoardButtons.SOURCES).contains(rule.optString("buttonSource","any")))throw new JSONException("Choose a button location.");}
        JSONArray conditions=rule.getJSONArray("conditions"),actions=rule.getJSONArray("actions");
        if(actions.length()==0)throw new JSONException("Add at least one action.");
        for(int i=0;i<conditions.length();i++){JSONObject c=conditions.getJSONObject(i);String field=c.getString("field"),op=c.getString("op");
            if(!Arrays.asList(FIELDS).contains(field)||!Arrays.asList(OPS).contains(op))throw new JSONException("Unsupported condition.");
            if(numeric(field)){double v=number(c.getString("value"));if(!Double.isFinite(v)||v<0)throw new JSONException("Numeric conditions require a non-negative number.");if(!Arrays.asList("eq","ne","gte","lte").contains(op))throw new JSONException("Choose a numeric comparison.");}
            else if(!Arrays.asList("eq","ne","contains").contains(op))throw new JSONException("Choose a text comparison.");
        }
        for(int i=0;i<actions.length();i++){JSONObject a=actions.getJSONObject(i);String type=a.getString("type"),v=a.optString("value");if(!Arrays.asList(ACTIONS).contains(type))throw new JSONException("Unsupported action.");
            if(type.equals("color")&&!v.matches("#[0-9a-fA-F]{6}"))throw new JSONException("Use a color such as #EF4444.");
            if(Arrays.asList("reminder","priority","points","difficulty").contains(type)){double n=number(v);if(!Double.isFinite(n)||n<0||(type.equals("reminder")&&(n<1||n>525600)))throw new JSONException("Use a non-negative number; reminder delay must be 1–525600 minutes.");}
            if(type.equals("setColumnAgeHours")||type.equals("setItemAgeHours")){double hours=number(v);if(!Double.isFinite(hours)||hours<0||hours>876000)throw new JSONException("Age must be between 0 and 876000 hours (100 years).");}
            if(type.equals("title")&&v.trim().isEmpty())throw new JSONException("Work item titles cannot be empty.");
            if(type.equals("due"))try{java.time.LocalDate.parse(v);}catch(Exception e){throw new JSONException("Due dates must use YYYY-MM-DD.");}
            if(type.equals("label")&&(v.trim().isEmpty()||v.contains(",")))throw new JSONException("Enter one non-empty label without commas.");
        }
    }
    private static boolean numeric(String field){return Arrays.asList("ageHours","columnAgeHours","priority","points","difficulty").contains(field);}
    private static double number(String value){try{return Double.parseDouble(value);}catch(Exception e){return Double.NaN;}}
    private static Item previous(Item item,List<Item> before,List<Item> current){
        for(Item old:before)if(old.key().equals(item.key()))return old;
        Item found=null;for(Item old:before)if(old.card.optString("id").equals(item.card.optString("id"))){boolean retained=false;for(Item live:current)if(live.key().equals(old.key())){retained=true;break;}if(retained)continue;if(found!=null)return null;found=old;}return found;
    }
    static boolean initialize(JSONObject data,long now)throws JSONException{
        boolean changed=false;for(Item i:items(data)){
            if(!i.card.has("createdAt")){i.card.put("createdAt",now);changed=true;}
            if(!i.card.has("columnEnteredAt")){i.card.put("columnEnteredAt",now);changed=true;}
        }return changed;
    }
    /** Event rules use only user changes, not actions produced by earlier rules. */
    static boolean run(JSONObject draft,JSONObject baseline,long now,String device)throws JSONException{
        return run(draft,baseline,now,device,null);
    }
    static boolean run(JSONObject draft,JSONObject baseline,long now,String device,BoardButtons.Event button)throws JSONException{
        return run(draft,baseline,now,device,button,id->true);
    }
    static boolean run(JSONObject draft,JSONObject baseline,long now,String device,BoardButtons.Event button,java.util.function.Predicate<String> canWrite)throws JSONException{
        if(button!=null&&!BoardButtons.exists(draft,button))throw new JSONException("The pressed button no longer exists.");
        String original=draft.toString();List<Item> old=items(baseline),current=items(draft);
        for(Item item:current){if(!canWrite.test(item.board.optString("id")))continue;Item before=previous(item,old,current);if(!item.card.has("createdAt"))item.card.put("createdAt",now);
            if(before==null||!before.column.optString("id").equals(item.column.optString("id"))||!before.board.optString("id").equals(item.board.optString("id")))item.card.put("columnEnteredAt",now);
            else if(!item.card.has("columnEnteredAt"))item.card.put("columnEnteredAt",now);
        }
        JSONObject eventSnapshot=BoardTemplate.copy(draft);List<Item> events=items(eventSnapshot);JSONArray rules=draft.optJSONArray("automations");if(rules==null)return !original.equals(draft.toString());
        JSONObject state=draft.optJSONObject("automationState");if(state==null){state=new JSONObject();draft.put("automationState",state);}
        Set<String> activeRules=new HashSet<>();
        for(int r=0;r<rules.length();r++){JSONObject rule=rules.getJSONObject(r);String rid=rule.optString("id");activeRules.add(rid);
            if(!rule.optBoolean("enabled",true)){state.remove(rid);continue;}
            try{validate(rule);}catch(JSONException invalid){rule.put("error",invalid.getMessage());continue;}
            boolean buttonRule=rule.optString("trigger").startsWith("button");
            if(buttonRule&&(button==null||!rule.optString("buttonId").equals(button.button)||!rule.optString("buttonSource","any").equals("any")&&!rule.optString("buttonSource").equals(button.source)))continue;
            rule.remove("error");JSONObject matches=state.optJSONObject(rid);if(matches==null){matches=new JSONObject();state.put(rid,matches);}
            Set<String> live=new HashSet<>();
            for(int i=0;i<events.size();i++){Item event=events.get(i),target=current.get(i),before=previous(event,old,events);String key=event.key();live.add(key);if(!canWrite.test(event.board.optString("id")))continue;
                String scope=rule.optString("boardId");if(!scope.isEmpty()&&!scope.equals(event.board.optString("id"))){matches.remove(key);continue;}
                if(buttonRule&&!button.includes(event))continue;
                boolean match=matches(rule,event,before,now),was=matches.optBoolean(key);if(match)matches.put(key,true);else matches.remove(key);
                boolean moved=before!=null&&(!before.column.optString("id").equals(event.column.optString("id"))||!before.board.optString("id").equals(event.board.optString("id")));
                String trigger=rule.optString("trigger");boolean fire=buttonRule?trigger.equals(button.gesture.equals("tap")?"buttonTap":button.gesture.equals("doubleTap")?"buttonDoubleTap":"buttonLongPress"):trigger.equals("created")?before==null:trigger.equals("moved")?moved:trigger.equals("updated")?before!=null&&!content(before.card).equals(content(event.card)):!was;
                if(!match||!fire)continue;
                JSONArray actions=rule.getJSONArray("actions");for(int a=0;a<actions.length();a++)act(target.card,actions.getJSONObject(a),rid,now,device);
                rule.put("lastRunAt",now).put("runCount",rule.optLong("runCount")+1);
            }
            for(String key:new ArrayList<>(keys(matches)))if(!live.contains(key))matches.remove(key);
        }
        // Re-arm immediately when actions make a previously matched condition false.
        // This survives closing the app until after the next stale threshold.
        for(int r=0;r<rules.length();r++){JSONObject rule=rules.getJSONObject(r);if(!rule.optBoolean("enabled",true)||!rule.optString("trigger").equals("conditions")||rule.has("error"))continue;
            JSONObject remembered=state.optJSONObject(rule.optString("id"));if(remembered==null)continue;
            for(Item item:current)if(remembered.optBoolean(item.key())&&!matches(rule,item,previous(item,old,current),now))remembered.remove(item.key());
        }
        for(String key:new ArrayList<>(keys(state)))if(!activeRules.contains(key))state.remove(key);
        return !original.equals(draft.toString());
    }
    private static Set<String> keys(JSONObject o){Set<String> keys=new HashSet<>();Iterator<String> it=o.keys();while(it.hasNext())keys.add(it.next());return keys;}
    private static String content(JSONObject card)throws JSONException{JSONObject copy=BoardTemplate.copy(card);copy.remove("createdAt");copy.remove("columnEnteredAt");return copy.toString();}
    private static boolean matches(JSONObject rule,Item item,Item before,long now)throws JSONException{
        JSONArray conditions=rule.getJSONArray("conditions");boolean any=rule.optString("match","all").equals("any");if(conditions.length()==0)return true;
        for(int i=0;i<conditions.length();i++){boolean matched=condition(conditions.getJSONObject(i),item,before,now);if(any&&matched)return true;if(!any&&!matched)return false;}return !any;
    }
    private static boolean condition(JSONObject condition,Item item,Item before,long now)throws JSONException{
        String field=condition.getString("field"),op=condition.getString("op"),wanted=condition.getString("value"),value;
        switch(field){
            case "column":value=item.column.optString("name");break;
            case "previousColumn":value=before==null?"":before.column.optString("name");break;
            case "ageHours":value=Double.toString(Math.max(0,now-item.card.optLong("createdAt",now))/3600000d);break;
            case "columnAgeHours":value=Double.toString(Math.max(0,now-item.card.optLong("columnEnteredAt",now))/3600000d);break;
            case "overdue":value=Boolean.toString(overdue(item.card.optString("due"),now));break;
            case "tasksComplete":JSONArray tasks=item.card.optJSONArray("tasks");boolean all=tasks!=null&&tasks.length()>0;if(tasks!=null)for(int t=0;t<tasks.length();t++)all&=tasks.getJSONObject(t).optBoolean("done");value=Boolean.toString(all);break;
            default:value=item.card.optString(field);
        }
        if(numeric(field)){double actual=number(value),expected=number(wanted);if(!Double.isFinite(actual))return false;switch(op){case "gte":return actual>=expected;case "lte":return actual<=expected;case "ne":return actual!=expected;default:return actual==expected;}}
        if(op.equals("contains"))return value.toLowerCase(Locale.ROOT).contains(wanted.toLowerCase(Locale.ROOT));boolean equal=value.equalsIgnoreCase(wanted);return op.equals("ne")?!equal:equal;
    }
    private static boolean overdue(String due,long now){try{return java.time.LocalDate.parse(due).isBefore(java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate());}catch(Exception e){return false;}}
    private static void act(JSONObject card,JSONObject action,String rule,long now,String device)throws JSONException{
        String type=action.getString("type"),value=action.optString("value");
        switch(type){
            case "resetColumnAge":card.put("columnEnteredAt",now);break;
            case "setColumnAgeHours":card.put("columnEnteredAt",now-(long)(number(value)*3600000d));break;
            case "resetItemAge":card.put("createdAt",now);break;
            case "setItemAgeHours":card.put("createdAt",now-(long)(number(value)*3600000d));break;
            case "clearDue":card.remove("due");break;
            case "color":card.put("automationColor",value);break;
            case "clearColor":card.remove("automationColor");break;
            case "completeTasks":case "reopenTasks":JSONArray tasks=card.optJSONArray("tasks");if(tasks!=null)for(int t=0;t<tasks.length();t++)tasks.getJSONObject(t).put("done",type.equals("completeTasks"));break;
            case "reminder":JSONArray reminders=card.optJSONArray("reminders");if(reminders==null){reminders=new JSONArray();card.put("reminders",reminders);}reminders.put(new JSONObject().put("id",UUID.randomUUID().toString()).put("kind","scheduled").put("at",now+(long)(number(value)*60000)).put("originDeviceId",device).put("automationId",rule));break;
            case "label":String labels=card.optString("labels"),label=value.trim();boolean exists=false;for(String old:labels.split(","))exists|=old.trim().equalsIgnoreCase(label);if(!exists)card.put("labels",labels.trim().isEmpty()?label:labels+", "+label);break;
            default:card.put(type,value);
        }
    }
    /** Adopt evaluated fields after the atomic write, preserving live card/task references. */
    static void adopt(JSONObject live,JSONObject saved)throws JSONException{
        Map<String,Item> byKey=new HashMap<>();for(Item item:items(saved))byKey.put(item.key(),item);
        String[] fields={"createdAt","columnEnteredAt","automationColor","priority","points","difficulty","labels","reminders","title","description","due"};
        for(Item item:items(live)){Item result=byKey.get(item.key());if(result==null)continue;for(String field:fields)if(result.card.has(field))item.card.put(field,result.card.get(field));else item.card.remove(field);
            JSONArray tasks=item.card.optJSONArray("tasks"),savedTasks=result.card.optJSONArray("tasks");if(tasks!=null&&savedTasks!=null)for(int i=0;i<tasks.length()&&i<savedTasks.length();i++)tasks.getJSONObject(i).put("done",savedTasks.getJSONObject(i).optBoolean("done"));
        }
        if(saved.has("automationState"))live.put("automationState",saved.get("automationState"));
        if(saved.has("automations"))live.put("automations",saved.get("automations"));
    }
}
