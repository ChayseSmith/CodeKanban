package com.battlemanson.codekanban;

import org.json.JSONObject;
import java.time.Instant;

final class ReminderModel {
    private ReminderModel() {}
    static long time(JSONObject reminder){Object value=reminder.opt("at");if(value instanceof Number)return ((Number)value).longValue();if(value instanceof String){try{return Instant.parse((String)value).toEpochMilli();}catch(Exception ignored){try{return Long.parseLong((String)value);}catch(Exception ignoredAgain){}}}return 0;}
    static boolean pending(JSONObject reminder){return reminder!=null&&time(reminder)>0&&(!reminder.has("notifiedAt")||reminder.isNull("notifiedAt"));}
    static String key(String card,String reminder,long when){return card+"/"+reminder+"/"+when;}
}
