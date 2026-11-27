package com.battlemanson.codekanban;

import org.json.*;
import java.util.*;

/** Buttons belong to a board, column or work item; events are never persisted or replayed. */
final class BoardButtons {
    static final String[] SOURCES={"any","board","column","item"};
    static final class Event {
        final String board,source,owner,button,gesture;
        Event(String board,String source,String owner,String button,String gesture){this.board=board;this.source=source;this.owner=owner;this.button=button;this.gesture=gesture;}
        boolean includes(AutomationEngine.Item item){return board.equals(item.board.optString("id"))&&(source.equals("board")||source.equals("column")&&owner.equals(item.column.optString("id"))||source.equals("item")&&owner.equals(item.card.optString("id")));}
    }
    private BoardButtons(){}
    static boolean validId(String id){return id.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}");}
    static JSONObject owner(JSONObject data,String boardId,String source,String ownerId)throws JSONException{
        JSONArray boards=data.getJSONArray("boards");for(int b=0;b<boards.length();b++){JSONObject board=boards.getJSONObject(b);if(!boardId.equals(board.optString("id")))continue;
            if(source.equals("board"))return boardId.equals(ownerId)?board:null;
            if(source.equals("item")){WorkItemRelations.Entry e=WorkItemRelations.find(board,ownerId);return e==null?null:e.card;}
            if(source.equals("column")){JSONArray columns=board.getJSONArray("columns");for(int c=0;c<columns.length();c++)if(ownerId.equals(columns.getJSONObject(c).optString("id")))return columns.getJSONObject(c);}
        }return null;
    }
    static boolean exists(JSONObject data,Event event)throws JSONException{
        if(event==null||!Arrays.asList("tap","doubleTap","longPress").contains(event.gesture))return false;
        JSONObject owner=owner(data,event.board,event.source,event.owner);JSONArray buttons=owner==null?null:owner.optJSONArray("buttons");if(buttons==null)return false;
        for(int i=0;i<buttons.length();i++)if(event.button.equals(buttons.getJSONObject(i).optString("id")))return true;return false;
    }
    static void validate(JSONArray buttons)throws JSONException{
        Set<String> ids=new HashSet<>();for(int i=0;i<buttons.length();i++){JSONObject button=buttons.getJSONObject(i);String id=button.optString("id");if(!validId(id))throw new JSONException("Button IDs must be 1–64 letters, digits, dots, underscores or hyphens, starting with a letter or digit.");if(!ids.add(id))throw new JSONException("Button IDs must be unique within this board, column or work item.");if(button.optString("label").trim().isEmpty())throw new JSONException("Give each button a label.");}
    }
    static void validateData(JSONObject data)throws JSONException{
        JSONArray boards=data.getJSONArray("boards");for(int b=0;b<boards.length();b++){JSONObject board=boards.getJSONObject(b);if(board.has("buttons"))validate(board.getJSONArray("buttons"));JSONArray columns=board.getJSONArray("columns");for(int c=0;c<columns.length();c++)if(columns.getJSONObject(c).has("buttons"))validate(columns.getJSONObject(c).getJSONArray("buttons"));for(WorkItemRelations.Entry e:WorkItemRelations.entries(board))if(e.card.has("buttons"))validate(e.card.getJSONArray("buttons"));}
    }
}
