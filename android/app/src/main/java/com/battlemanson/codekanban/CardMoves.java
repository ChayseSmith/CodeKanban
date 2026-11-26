package com.battlemanson.codekanban;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Moves a card only after preparing both columns, and restores them if saving fails. */
final class CardMoves {
    interface Save { boolean run(); }
    private CardMoves() {}
    static boolean transfer(JSONObject from, JSONObject to, JSONObject card, Save save) {
        if (from == to) return false;
        JSONArray originalFrom=from.optJSONArray("cards"), originalTo=to.optJSONArray("cards");
        if(originalFrom==null||originalTo==null)return false;
        JSONArray updatedFrom=new JSONArray(), updatedTo=new JSONArray();boolean found=false;
        for(int i=0;i<originalFrom.length();i++){
            JSONObject item=originalFrom.optJSONObject(i);
            if(item==card&&!found)found=true;else updatedFrom.put(item);
        }
        if(!found)return false;
        for(int i=0;i<originalTo.length();i++)updatedTo.put(originalTo.optJSONObject(i));
        updatedTo.put(card);
        try {
            from.put("cards",updatedFrom);to.put("cards",updatedTo);
            if(save.run())return true;
            from.put("cards",originalFrom);to.put("cards",originalTo);
            return false;
        } catch(JSONException e) { throw new IllegalStateException(e); }
    }
}
