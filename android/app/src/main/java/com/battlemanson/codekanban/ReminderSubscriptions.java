package com.battlemanson.codekanban;

import android.app.*;
import android.content.*;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.util.*;

/** Origin travels with a reminder; subscription decisions belong only to this installation. */
final class ReminderSubscriptions {
    static final String OPEN="com.battlemanson.codekanban.OPEN_SUBSCRIPTIONS";
    private static final String CHANNEL="reminder_subscriptions";
    private ReminderSubscriptions() {}
    static synchronized String device(Context context){
        File file=new File(context.getNoBackupFilesDir(),"reminder-device-id");
        try{if(file.exists())try(DataInputStream in=new DataInputStream(new FileInputStream(file))){return in.readUTF();}
            String id=UUID.randomUUID().toString();android.util.AtomicFile atomic=new android.util.AtomicFile(file);FileOutputStream stream=atomic.startWrite();try{DataOutputStream out=new DataOutputStream(stream);out.writeUTF(id);out.flush();atomic.finishWrite(stream);}catch(Exception e){atomic.failWrite(stream);throw e;}return id;
        }catch(Exception e){throw new IllegalStateException("Could not read reminder device identity",e);}
    }
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("reminder_subscriptions",Context.MODE_PRIVATE);}
    private static String key(Context c,String card,String reminder){return device(c)+"/"+card+"/"+reminder;}
    static boolean subscribed(Context c,String card,JSONObject reminder){
        String key=key(c,card,reminder.optString("id"));SharedPreferences p=prefs(c);
        return p.contains(key)?p.getBoolean(key,false):(reminder.optString("originDeviceId").isEmpty()||device(c).equals(reminder.optString("originDeviceId")));
    }
    /** Run before persisting imports; legacy local reminders retain delivery on upgrade. */
    static boolean prepare(Context c,JSONObject data,boolean imported)throws JSONException{
        boolean changed=false;JSONArray boards=data.optJSONArray("boards");if(boards==null)return false;
        for(int b=0;b<boards.length();b++)for(WorkItemRelations.Entry entry:WorkItemRelations.entries(boards.getJSONObject(b))){JSONArray reminders=entry.card.optJSONArray("reminders");if(reminders==null)continue;
            for(int r=0;r<reminders.length();r++){JSONObject reminder=reminders.optJSONObject(r);if(reminder==null)continue;
                if(reminder.optString("id").isEmpty()){reminder.put("id",UUID.randomUUID().toString());changed=true;}
                if(reminder.optString("originDeviceId").isEmpty()){reminder.put("originDeviceId",imported?"unknown-import":device(c));changed=true;}
            }}return changed;
    }
    static void set(Context c,String card,JSONObject reminder,boolean enabled){
        if(!prefs(c).edit().putBoolean(key(c,card,reminder.optString("id")),enabled).commit())throw new IllegalStateException("Could not save reminder subscription");
        NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(manager!=null)manager.cancel("subscription/"+key(c,card,reminder.optString("id")),0);
        if(!enabled){
            if(manager!=null)for(android.service.notification.StatusBarNotification notification:manager.getActiveNotifications()){
                String tag=notification.getTag();if(tag==null)continue;Uri address=Uri.parse(tag);List<String> parts=address.getPathSegments();
                if("reminder".equals(address.getAuthority())&&parts.size()==4&&card.equals(parts.get(1))&&reminder.optString("id").equals(parts.get(2)))manager.cancel(tag,notification.getId());
            }
            SharedPreferences state=c.getSharedPreferences(ReminderScheduler.PREFS,Context.MODE_PRIVATE);SharedPreferences.Editor edit=state.edit();boolean active=false;
            String prefix=card+"/"+reminder.optString("id")+"/";
            for(String k:state.getAll().keySet()){
                if(k.startsWith("activeUri/"+prefix)){String address=state.getString(k,"");if(manager!=null)manager.cancel(address,0);}
                if(k.startsWith("active/"+prefix)){active|=state.getBoolean(k,false);edit.remove(k);}
                if(k.startsWith("activeUri/"+prefix)||k.startsWith("snooze/"+prefix))edit.remove(k);
            }edit.commit();
            if(active)try{c.startService(new Intent(c,AlarmService.class).setAction("subscriptions-changed"));}catch(Exception e){android.util.Log.w("CodeKanbanReminder","Alarm will stop on its next subscription check",e);}
        }
        ReminderScheduler.syncSaved(c);
    }
    static void discover(Context c,JSONObject data){
        if(!ReminderScheduler.notificationsAllowed(c))return;NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);if(manager==null||!manager.areNotificationsEnabled())return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Imported reminder subscriptions",NotificationManager.IMPORTANCE_DEFAULT));
        if(manager.getNotificationChannel(CHANNEL).getImportance()==NotificationManager.IMPORTANCE_NONE)return;
        try{JSONArray boards=data.optJSONArray("boards");if(boards==null)return;
            for(int b=0;b<boards.length();b++)for(WorkItemRelations.Entry entry:WorkItemRelations.entries(boards.getJSONObject(b))){JSONArray reminders=entry.card.optJSONArray("reminders");if(reminders==null)continue;
                for(int r=0;r<reminders.length();r++){JSONObject reminder=reminders.optJSONObject(r);if(reminder==null||!ReminderModel.pending(reminder)||device(c).equals(reminder.optString("originDeviceId")))continue;
                    String card=entry.card.optString("id"),rid=reminder.optString("id"),key=key(c,card,rid);if(prefs(c).contains(key)||prefs(c).getBoolean("prompted/"+key,false))continue;
                    Intent open=new Intent(c,MainActivity.class).setAction(OPEN).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    PendingIntent view=PendingIntent.getActivity(c,4100,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                    Notification notification=new Notification.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(entry.card.optString("title","Imported reminder")).setContentText("Enable this reminder's alerts on this device?").setContentIntent(view).setAutoCancel(true)
                        .addAction(new Notification.Action.Builder(null,"Apply to this device",choice(c,card,rid,true)).build()).addAction(new Notification.Action.Builder(null,"No",choice(c,card,rid,false)).build()).build();
                    manager.notify("subscription/"+key,0,notification);prefs(c).edit().putBoolean("prompted/"+key,true).apply();
                }}
        }catch(Exception e){android.util.Log.e("CodeKanbanReminder","Could not show imported reminders",e);}
    }
    private static PendingIntent choice(Context c,String card,String reminder,boolean enabled){
        Uri uri=new Uri.Builder().scheme("codekanban").authority("subscription").appendPath(card).appendPath(reminder).appendPath(Boolean.toString(enabled)).build();
        return PendingIntent.getBroadcast(c,0,new Intent(c,ReminderSubscriptionReceiver.class).setData(uri),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
}
