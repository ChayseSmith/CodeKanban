package com.battlemanson.codekanban;
import org.json.*;
import java.util.*;

/** Nearest explicit grant wins. Owner access is never downgraded. */
final class BoardPermissions {
    static final int MAX_DEPTH=8;
    private BoardPermissions(){}
    static String role(String user,String board,Map<String,JSONObject> metadata){
        JSONObject own=metadata.get(board);if(own==null||own.optBoolean("deleted"))return "none";
        if(user.equals(own.optString("ownerId")))return "owner";
        JSONObject grants=own.optJSONObject("grants");if(grants!=null&&grants.has(user))return explicit(grants.optString(user,"none"));
        JSONArray ancestors=own.optJSONArray("ancestors");if(ancestors!=null)for(int i=ancestors.length()-1;i>=0;i--){JSONObject parent=metadata.get(ancestors.optString(i));if(parent==null||parent.optBoolean("deleted"))return "none";grants=parent.optJSONObject("grants");if(grants!=null&&grants.has(user))return explicit(grants.optString(user,"none"));}
        return "none";
    }
    private static String explicit(String value){return Arrays.asList("read","write","none").contains(value)?value:"none";}
    static boolean readable(String role){return Arrays.asList("owner","read","write").contains(role);}
    static boolean writable(String role){return role.equals("owner")||role.equals("write");}
    static String localId(String id){return "shared:"+id;}
    static String sharedId(JSONObject board){JSONObject marker=board.optJSONObject("_shared");return marker==null?"":marker.optString("id");}
    static Map<String,String> records(JSONObject board)throws JSONException{
        JSONObject marker=board.getJSONObject("_shared"),copy=BoardTemplate.copy(board);String id=marker.getString("id");copy.remove("_shared");copy.put("id",id).put("parent",marker.optString("parentId"));copy.remove("boardButtonsCollapsed");copy.remove("skipDefaultPrompt");
        JSONArray columns=copy.getJSONArray("columns");for(int c=0;c<columns.length();c++)columns.getJSONObject(c).remove("collapsed");
        return CloudRecords.encode(new JSONObject().put("boards",new JSONArray().put(copy)));
    }
    static boolean checkWrites(JSONObject before,JSONObject after,String user,Map<String,JSONObject> policies)throws JSONException{
        Map<String,JSONObject> old=new HashMap<>(),next=new HashMap<>();for(int i=0;i<before.getJSONArray("boards").length();i++){JSONObject b=before.getJSONArray("boards").getJSONObject(i);old.put(b.getString("id"),b);}for(int i=0;i<after.getJSONArray("boards").length();i++){JSONObject b=after.getJSONArray("boards").getJSONObject(i);next.put(b.getString("id"),b);}
        for(JSONObject b:old.values())if(b.has("_shared")){JSONObject n=next.get(b.getString("id"));Map<String,String> prior=BoardPermissions.records(b),edited=n==null?Collections.emptyMap():BoardPermissions.records(n);if(prior.equals(edited))continue;
            if(!BoardPermissions.writable(role(user,sharedId(b),policies)))throw new JSONException("This board is read-only or no longer accessible: "+b.optString("name"));
            String boardKey=CloudRecords.id("board",BoardPermissions.sharedId(b),BoardPermissions.sharedId(b));if(!role(user,sharedId(b),policies).equals("owner")&&!Objects.equals(prior.get(boardKey),edited.get(boardKey)))throw new JSONException("Only the board owner can change board settings or delete the board.");
        }
        for(JSONObject b:next.values())if(!old.containsKey(b.optString("id"))){JSONObject parent=next.get(b.optString("parent"));if(parent!=null&&parent.has("_shared"))throw new JSONException("Use New shared nested board in the Board permissions section to create children of a shared board.");}
        return true;
    }
    static JSONObject localBoard(String id,JSONObject metadata,Map<String,String> records,Set<String> visible)throws JSONException{
        JSONArray boards=CloudRecords.decode(records);if(boards.length()==0)return null;JSONObject board=boards.getJSONObject(0);if(boards.length()!=1||!id.equals(board.optString("id")))throw new JSONException("Shared board identity mismatch.");
        String parent=metadata.optString("parentId");board.put("id",localId(id)).put("parent",visible.contains(parent)?localId(parent):"");board.put("_shared",new JSONObject().put("id",id).put("ownerId",metadata.getString("ownerId")).put("parentId",parent));return board;
    }
}
