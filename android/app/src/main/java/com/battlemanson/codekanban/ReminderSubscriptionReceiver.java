package com.battlemanson.codekanban;
import android.content.*;
import android.net.Uri;
import org.json.*;
import java.util.List;
public final class ReminderSubscriptionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        Uri uri=intent==null?null:intent.getData();if(uri==null||!"subscription".equals(uri.getAuthority())||uri.getPathSegments().size()!=3)return;
        List<String> p=uri.getPathSegments();try{JSONArray boards=ReminderScheduler.read(context).getJSONArray("boards");
            for(int b=0;b<boards.length();b++){WorkItemRelations.Entry entry=WorkItemRelations.find(boards.getJSONObject(b),p.get(0));if(entry==null)continue;JSONArray reminders=entry.card.optJSONArray("reminders");if(reminders==null)return;
                for(int r=0;r<reminders.length();r++){JSONObject reminder=reminders.getJSONObject(r);if(p.get(1).equals(reminder.optString("id"))){ReminderSubscriptions.set(context,p.get(0),reminder,Boolean.parseBoolean(p.get(2)));return;}}}
        }catch(Exception e){android.util.Log.e("CodeKanbanReminder","Could not change subscription",e);}
    }
}
