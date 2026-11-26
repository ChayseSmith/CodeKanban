package com.battlemanson.codekanban;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.util.*;

/** Alarms are hints: delivery rechecks the current board before posting a notification. */
final class ReminderScheduler {
    static final String CHANNEL="work_item_reminders", PREFS="reminder_state", ACTION="com.battlemanson.codekanban.REMINDER";
    private ReminderScheduler() {}
    static long time(JSONObject reminder){return ReminderModel.time(reminder);}
    static String key(String card,String reminder,long when){return ReminderModel.key(card,reminder,when);}
    static Uri uri(String board,String card,String reminder,long when){return new Uri.Builder().scheme("codekanban").authority("reminder").appendPath(board).appendPath(card).appendPath(reminder).appendPath(Long.toString(when)).build();}
    static PendingIntent alarmIntent(Context context,Uri uri){Intent i=new Intent(context,ReminderReceiver.class).setAction(ACTION).setData(uri);return PendingIntent.getBroadcast(context,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static JSONObject read(Context context)throws Exception{
        android.util.AtomicFile file=new android.util.AtomicFile(new File(context.getFilesDir(),"boards.json"));
        try(InputStream in=file.openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);return new JSONObject(out.toString("UTF-8"));}
    }
    static void sync(Context context,JSONObject data){
        AlarmManager alarms=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);if(alarms==null||data==null)return;
        SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        Set<String> old=new HashSet<>(prefs.getStringSet("scheduled",Collections.emptySet())),next=new HashSet<>();JSONArray boards=data.optJSONArray("boards");
        if(boards==null)return;
        for(int b=0;b<boards.length();b++){JSONObject board=boards.optJSONObject(b);if(board==null)continue;JSONArray columns=board.optJSONArray("columns");if(columns==null)continue;
            for(int c=0;c<columns.length();c++){JSONObject col=columns.optJSONObject(c);JSONArray cards=col==null?null:col.optJSONArray("cards");if(cards==null)continue;
                for(int i=0;i<cards.length();i++){JSONObject card=cards.optJSONObject(i);JSONArray reminders=card==null?null:card.optJSONArray("reminders");if(reminders==null)continue;
                    for(int j=0;j<reminders.length();j++){JSONObject reminder=reminders.optJSONObject(j);if(reminder==null)continue;long at=time(reminder);String bid=board.optString("id"),cid=card.optString("id"),rid=reminder.optString("id");
                        if(!ReminderModel.pending(reminder)||bid.isEmpty()||cid.isEmpty()||rid.isEmpty()||prefs.getBoolean("delivered/"+key(cid,rid,at),false))continue;
                        Uri uri=uri(bid,cid,rid,at);String identity=key(cid,rid,at);if(prefs.getBoolean("active/"+identity,false)){next.add(uri.toString());continue;}
                        next.add(uri.toString());PendingIntent pi=alarmIntent(context,uri);long trigger=Math.max(System.currentTimeMillis()+500,AlarmTiming.nextTrigger(at,prefs.getLong("snooze/"+identity,0)));
                        try{if(Build.VERSION.SDK_INT<31||alarms.canScheduleExactAlarms()){
                            if(reminder.optBoolean("fullScreen")){Intent show=new Intent(context,MainActivity.class);PendingIntent showIntent=PendingIntent.getActivity(context,1,show,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);alarms.setAlarmClock(new AlarmManager.AlarmClockInfo(trigger,showIntent),pi);}
                            else alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pi);
                        }else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pi);}catch(SecurityException e){alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pi);}
                    }
                }
            }
        }
        for(String previous:old)if(!next.contains(previous))alarms.cancel(alarmIntent(context,Uri.parse(previous)));
        prefs.edit().putStringSet("scheduled",next).apply();
    }
    static void syncSaved(Context context){try{sync(context,read(context));}catch(Exception ignored){}}
    static boolean notificationsAllowed(Context context){return Build.VERSION.SDK_INT<33||context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;}
    private static void retryActive(Context context,Uri uri){
        AlarmManager manager=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);if(manager==null)return;long retry=System.currentTimeMillis()+120000;PendingIntent alarm=alarmIntent(context,uri);
        try{if(Build.VERSION.SDK_INT<31||manager.canScheduleExactAlarms()){PendingIntent show=PendingIntent.getActivity(context,1,new Intent(context,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);manager.setAlarmClock(new AlarmManager.AlarmClockInfo(retry,show),alarm);}else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,retry,alarm);}catch(Exception e){android.util.Log.e("CodeKanbanReminder","Could not schedule alarm retry",e);}
    }
    static void scheduleSnoozed(Context context,Uri uri,long when){
        AlarmManager manager=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);if(manager==null)return;PendingIntent alarm=alarmIntent(context,uri);
        try{if(Build.VERSION.SDK_INT<31||manager.canScheduleExactAlarms()){PendingIntent show=PendingIntent.getActivity(context,1,new Intent(context,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);manager.setAlarmClock(new AlarmManager.AlarmClockInfo(when,show),alarm);}else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,alarm);}catch(Exception e){android.util.Log.e("CodeKanbanReminder","Could not schedule snooze",e);}
    }
    static JSONObject[] lookup(Context context,Uri uri)throws Exception{
        if(uri==null||!"codekanban".equals(uri.getScheme())||!"reminder".equals(uri.getAuthority())||uri.getPathSegments().size()!=4)return null;
        List<String> p=uri.getPathSegments();long when=Long.parseLong(p.get(3));JSONArray boards=read(context).getJSONArray("boards");
        for(int i=0;i<boards.length();i++){JSONObject board=boards.getJSONObject(i);if(!p.get(0).equals(board.optString("id")))continue;WorkItemRelations.Entry entry=WorkItemRelations.find(board,p.get(1));if(entry==null)return null;JSONArray reminders=entry.card.optJSONArray("reminders");if(reminders==null)return null;
            for(int j=0;j<reminders.length();j++){JSONObject reminder=reminders.optJSONObject(j);if(reminder!=null&&p.get(2).equals(reminder.optString("id"))&&when==time(reminder)&&ReminderModel.pending(reminder))return new JSONObject[]{board,entry.card,reminder};}return null;}
        return null;
    }
    static void deliver(Context context,Uri uri){
        try{if(uri==null||!"codekanban".equals(uri.getScheme())||!"reminder".equals(uri.getAuthority())||uri.getPathSegments().size()!=4)return;
            List<String> p=uri.getPathSegments();String bid=p.get(0),cid=p.get(1),rid=p.get(2);long when=Long.parseLong(p.get(3));if(when>System.currentTimeMillis()+1000)return;
            JSONObject data=read(context),board=null,card=null,reminder=null;JSONArray boards=data.getJSONArray("boards");
            for(int i=0;i<boards.length();i++)if(bid.equals(boards.getJSONObject(i).optString("id"))){board=boards.getJSONObject(i);break;}if(board==null)return;
            WorkItemRelations.Entry entry=WorkItemRelations.find(board,cid);if(entry==null)return;card=entry.card;JSONArray reminders=card.optJSONArray("reminders");if(reminders==null)return;
            for(int i=0;i<reminders.length();i++){JSONObject candidate=reminders.optJSONObject(i);if(candidate!=null&&rid.equals(candidate.optString("id"))&&when==time(candidate)){reminder=candidate;break;}}
            if(!ReminderModel.pending(reminder))return;
            SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);String delivery="delivered/"+key(cid,rid,when);if(prefs.getBoolean(delivery,false))return;
            String identity=key(cid,rid,when);if(prefs.getLong("snooze/"+identity,when)>System.currentTimeMillis()+1000)return;
            if(reminder.optBoolean("fullScreen")){
                prefs.edit().putBoolean("active/"+identity,true).putString("activeUri/"+identity,uri.toString()).commit();
                Intent ring=new Intent(context,AlarmService.class).setAction(AlarmService.START).setData(uri);
                try{context.startForegroundService(ring);retryActive(context,uri);}catch(Exception e){prefs.edit().remove("active/"+identity).remove("activeUri/"+identity).putLong("snooze/"+identity,System.currentTimeMillis()+30000).commit();android.util.Log.e("CodeKanbanReminder","Could not start alarm; retrying",e);sync(context,data);}return;
            }
            if(!notificationsAllowed(context))return;
            NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);if(manager==null||!manager.areNotificationsEnabled())return;
            String sound=reminder.optString("soundUri","");String channelId=sound.isEmpty()?CHANNEL:CHANNEL+"_"+Integer.toHexString(sound.hashCode());
            NotificationChannel configured=new NotificationChannel(channelId,"Work item reminders",NotificationManager.IMPORTANCE_HIGH);
            if(!sound.isEmpty())configured.setSound(Uri.parse(sound),new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build());manager.createNotificationChannel(configured);
            NotificationChannel channel=manager.getNotificationChannel(channelId);if(channel==null||channel.getImportance()==NotificationManager.IMPORTANCE_NONE)return;
            Intent open=new Intent(context,MainActivity.class).setAction("com.battlemanson.codekanban.OPEN_REMINDER").setData(uri).putExtra("boardId",bid).putExtra("cardId",cid).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent content=PendingIntent.getActivity(context,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification notification=new Notification.Builder(context,channelId).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(card.optString("title","Work item reminder")).setContentText(board.optString("name","Code Kanban")).setAutoCancel(true).setContentIntent(content).setPriority(Notification.PRIORITY_HIGH).build();
            manager.notify(uri.toString(),0,notification);prefs.edit().putBoolean(delivery,true).apply();
        }catch(Exception e){android.util.Log.e("CodeKanbanReminder","Could not deliver reminder; retrying",e);if(uri!=null)retryActive(context,uri);}
    }
}
