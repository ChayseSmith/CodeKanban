package com.battlemanson.codekanban;

import org.json.*;
import java.util.*;

/** Board-scoped parent links and atomic mutations on a caller-owned draft. */
final class WorkItemRelations {
    private WorkItemRelations() {}
    static final class Entry { final JSONObject column,card; Entry(JSONObject column,JSONObject card){this.column=column;this.card=card;} }
    static ArrayList<Entry> entries(JSONObject board) throws JSONException {
        ArrayList<Entry> result=new ArrayList<>();JSONArray cols=board.getJSONArray("columns");
        for(int i=0;i<cols.length();i++){JSONObject col=cols.getJSONObject(i);JSONArray cards=col.getJSONArray("cards");for(int j=0;j<cards.length();j++)result.add(new Entry(col,cards.getJSONObject(j)));}return result;
    }
    static Entry find(JSONObject board,String id) throws JSONException {
        Entry found=null;for(Entry e:entries(board))if(id.equals(e.card.optString("id"))){if(found!=null)throw new JSONException("Duplicate work item IDs on this board.");found=e;}return found;
    }
    static boolean canParent(JSONObject board,String cardId,String parentId) throws JSONException {
        if(find(board,cardId)==null)return false;Set<String> seen=new HashSet<>();seen.add(cardId);String current=parentId;
        while(current!=null&&!current.isEmpty()){if(!seen.add(current))return false;Entry parent=find(board,current);if(parent==null)return false;current=parent.card.optString("parentId","");}return true;
    }
    static boolean hasArchivedChild(JSONObject board,String id){
        JSONObject template=board.optJSONObject("templateData"),archive=template==null?null:template.optJSONObject("archive");
        JSONArray cards=archive==null?null:archive.optJSONArray("cards");if(cards==null)return false;
        for(int i=0;i<cards.length();i++){JSONObject child=cards.optJSONObject(i);if(child!=null&&id.equals(child.optString("parentId")))return true;}return false;
    }
    static void setParent(JSONObject board,String cardId,String parentId) throws JSONException {
        Entry child=find(board,cardId);if(child==null||!canParent(board,cardId,parentId))throw new JSONException("This parent would create a cycle or is unavailable.");
        if(parentId==null||parentId.isEmpty())child.card.remove("parentId");else child.card.put("parentId",parentId);
    }
    static void taskToCard(JSONObject board,String cardId,String taskId) throws JSONException {
        Entry owner=find(board,cardId);if(owner==null)throw new JSONException("Work item not found.");JSONArray tasks=owner.card.getJSONArray("tasks");int index=-1;JSONObject task=null;
        for(int i=0;i<tasks.length();i++)if(taskId.equals(tasks.getJSONObject(i).optString("id"))){if(index!=-1)throw new JSONException("Duplicate task IDs.");index=i;task=tasks.getJSONObject(i);}
        if(task==null)throw new JSONException("Task not found.");JSONArray cols=board.getJSONArray("columns");if(cols.length()==0)throw new JSONException("The board has no columns.");
        JSONObject target=cols.getJSONObject(task.optBoolean("done")?cols.length()-1:0);
        JSONObject child=new JSONObject().put("id",UUID.randomUUID().toString()).put("title",task.getString("title")).put("parentId",cardId).put("tasks",new JSONArray()).put("comments",new JSONArray()).put("points","2").put("priority","2");
        tasks.remove(index);target.getJSONArray("cards").put(child);
    }
    static void cardToTask(JSONObject board,String cardId) throws JSONException {
        Entry entry=find(board,cardId);if(entry==null)throw new JSONException("Work item not found.");String parentId=entry.card.optString("parentId");Entry parent=find(board,parentId);
        if(parent==null||parent.card==entry.card)throw new JSONException("Choose a valid parent first.");
        JSONArray tasks=entry.card.optJSONArray("tasks");if(tasks!=null&&tasks.length()>0)throw new JSONException("A work item with tasks cannot be converted.");
        for(Entry e:entries(board))if(cardId.equals(e.card.optString("parentId")))throw new JSONException("A work item with children cannot be converted.");
        if(hasArchivedChild(board,cardId))throw new JSONException("A work item with archived children cannot be converted.");
        JSONArray cols=board.getJSONArray("columns");boolean done=entry.column==cols.getJSONObject(cols.length()-1);
        JSONArray parentTasks=parent.card.optJSONArray("tasks");if(parentTasks==null){parentTasks=new JSONArray();parent.card.put("tasks",parentTasks);}
        parentTasks.put(new JSONObject().put("id",UUID.randomUUID().toString()).put("title",entry.card.getString("title")).put("done",done));remove(entry.column,entry.card);
    }
    static void remove(JSONObject column,JSONObject card) throws JSONException {JSONArray cards=column.getJSONArray("cards");for(int i=0;i<cards.length();i++)if(cards.getJSONObject(i)==card){cards.remove(i);return;}throw new JSONException("Work item no longer exists.");}
    static void moveFamily(JSONObject source,JSONObject destination,String cardId,boolean root) throws JSONException {
        if(source==destination)throw new JSONException("Choose a different board.");Entry selected=find(source,cardId);if(selected==null)throw new JSONException("Work item not found.");
        if(root){Set<String> seen=new HashSet<>();while(!selected.card.optString("parentId").isEmpty()){if(!seen.add(selected.card.getString("id")))throw new JSONException("Parent links contain a cycle.");selected=find(source,selected.card.optString("parentId"));if(selected==null)throw new JSONException("Parent not found.");}}
        ArrayList<Entry> all=entries(source),family=new ArrayList<>();Set<String> ids=new HashSet<>();family.add(selected);ids.add(selected.card.getString("id"));
        for(int i=0;i<family.size();i++)for(Entry e:all)if(family.get(i).card.getString("id").equals(e.card.optString("parentId"))){String id=e.card.getString("id");if(!ids.add(id))throw new JSONException("Duplicate ID or parent cycle.");family.add(e);}
        for(Entry e:family)if(hasArchivedChild(source,e.card.getString("id")))throw new JSONException("This family includes archived children; moving archived items is not supported yet.");
        for(Entry e:entries(destination))if(ids.contains(e.card.optString("id")))throw new JSONException("Destination already has a work item with the same ID.");
        JSONArray cols=destination.getJSONArray("columns");if(cols.length()==0)throw new JSONException("Destination needs a column.");
        for(Entry e:family){JSONObject target=cols.getJSONObject(0);for(int j=0;j<cols.length();j++)if(cols.getJSONObject(j).optString("name").equals(e.column.optString("name"))){target=cols.getJSONObject(j);break;}
            remove(e.column,e.card);if(!ids.contains(e.card.optString("parentId")))e.card.remove("parentId");target.getJSONArray("cards").put(e.card);}
    }
}
