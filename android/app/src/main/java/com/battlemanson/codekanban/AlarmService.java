package com.battlemanson.codekanban;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import org.json.JSONObject;
import java.util.*;

/** Keeps audible alarms active independently of the editor or alarm screen. */
public final class AlarmService extends Service {
    static final String START="com.battlemanson.codekanban.ALARM_START", DISMISS="com.battlemanson.codekanban.ALARM_DISMISS", SNOOZE="com.battlemanson.codekanban.ALARM_SNOOZE";
    static final String CHANNEL="ringing_alarms";private static final int NOTIFICATION=7301;
    private MediaPlayer player;private Ringtone fallback;private PowerManager.WakeLock wake;private Uri current;private boolean foreground;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable repeatFallback=new Runnable(){@Override public void run(){if(fallback!=null){if(!fallback.isPlaying())fallback.play();handler.postDelayed(this,1000);}}};
    private final Runnable watchdog=new Runnable(){@Override public void run(){if(current!=null){try{if(ReminderScheduler.lookup(AlarmService.this,current)==null){respond(current,false);current=nextActive();if(current==null){stopForeground(true);stopSelf();return;}startRinging(current);}}catch(Exception e){android.util.Log.e("CodeKanbanAlarm","Could not verify active alarm",e);}}handler.postDelayed(this,10000);}};
    @Override public void onCreate(){super.onCreate();NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);NotificationChannel channel=new NotificationChannel(CHANNEL,"Ringing alarms",NotificationManager.IMPORTANCE_HIGH);channel.setSound(null,null);channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);manager.createNotificationChannel(channel);PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);wake=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"CodeKanban:Alarm");wake.acquire();handler.postDelayed(watchdog,10000);}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        Uri uri=intent==null?null:intent.getData();String action=intent==null?START:intent.getAction();
        Uri display=current==null?nextActive():current;String title="Code Kanban alarm";
        if(display!=null)try{JSONObject[] entry=ReminderScheduler.lookup(this,display);if(entry!=null)title=entry[1].optString("title",title);}catch(Exception ignored){}
        if(!foreground){startForeground(NOTIFICATION,notification(display,title));foreground=true;}
        if((DISMISS.equals(action)||SNOOZE.equals(action))&&uri!=null)respond(uri,SNOOZE.equals(action));
        if(current!=null&&!isActive(current)){stopSound();current=null;}
        if(current==null){current=nextActive();if(current!=null)startRinging(current);}
        if(current==null){stopForeground(true);stopSelf();return START_NOT_STICKY;}
        return START_STICKY;
    }
    private String identity(Uri uri){List<String> p=uri.getPathSegments();if(p.size()!=4)return "";return ReminderScheduler.key(p.get(1),p.get(2),Long.parseLong(p.get(3)));}
    private boolean isActive(Uri uri){try{return getSharedPreferences(ReminderScheduler.PREFS,MODE_PRIVATE).getBoolean("active/"+identity(uri),false);}catch(Exception e){return false;}}
    private Uri nextActive(){android.content.SharedPreferences prefs=getSharedPreferences(ReminderScheduler.PREFS,MODE_PRIVATE);ArrayList<String> keys=new ArrayList<>(prefs.getAll().keySet());Collections.sort(keys);for(String k:keys)if(k.startsWith("activeUri/")){Uri uri=Uri.parse(prefs.getString(k,""));if(!isActive(uri)){prefs.edit().remove(k).remove("active/"+k.substring("activeUri/".length())).commit();continue;}try{if(ReminderScheduler.lookup(this,uri)!=null)return uri;prefs.edit().remove(k).remove("active/"+k.substring("activeUri/".length())).commit();}catch(Exception e){android.util.Log.e("CodeKanbanAlarm","Could not read active alarm; will retry",e);return uri;}}return null;}
    private void respond(Uri uri,boolean snooze){
        String key;try{key=identity(uri);}catch(Exception e){return;}if(key.isEmpty()||!isActive(uri))return;
        android.content.SharedPreferences.Editor edit=getSharedPreferences(ReminderScheduler.PREFS,MODE_PRIVATE).edit().remove("active/"+key).remove("activeUri/"+key);
        long snoozedUntil=snooze?AlarmTiming.snoozeUntil(System.currentTimeMillis()):0;
        if(snooze)edit.putLong("snooze/"+key,snoozedUntil);else edit.remove("snooze/"+key).putBoolean("delivered/"+key,true);
        if(!edit.commit()){android.util.Log.e("CodeKanbanAlarm","Could not persist alarm response");return;}
        if(uri.equals(current)){stopSound();current=null;}if(snooze)ReminderScheduler.scheduleSnoozed(this,uri,snoozedUntil);ReminderScheduler.syncSaved(this);
    }
    private void startRinging(Uri uri){
        try{JSONObject[] entry;try{entry=ReminderScheduler.lookup(this,uri);}catch(Exception readFailure){android.util.Log.e("CodeKanbanAlarm","Could not read alarm details; using default sound",readFailure);entry=null;}
            String title=entry==null?"Work item alarm":entry[1].optString("title","Work item alarm");startForeground(NOTIFICATION,notification(uri,title));
            String sound=entry==null?"":entry[2].optString("soundUri","");Uri chosen=sound.isEmpty()?android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI:Uri.parse(sound);
            try{play(chosen);}catch(Exception first){android.util.Log.w("CodeKanbanAlarm","Selected sound unavailable; using default",first);try{play(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI);}catch(Exception second){fallback=RingtoneManager.getRingtone(this,android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI);if(fallback==null)throw second;fallback.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());if(Build.VERSION.SDK_INT>=28)fallback.setLooping(true);fallback.play();if(Build.VERSION.SDK_INT<28)handler.postDelayed(repeatFallback,1000);}}
        }catch(Exception e){android.util.Log.e("CodeKanbanAlarm","Could not start alarm sound",e);}
    }
    private void play(Uri sound)throws Exception{stopSound();MediaPlayer p=new MediaPlayer();try{p.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());p.setDataSource(this,sound);p.setLooping(true);p.prepare();p.start();player=p;}catch(Exception e){p.release();throw e;}}
    private void stopSound(){handler.removeCallbacks(repeatFallback);if(fallback!=null){fallback.stop();fallback=null;}if(player!=null){try{player.stop();}catch(Exception ignored){}player.release();player=null;}}
    private PendingIntent serviceAction(String action,Uri uri){return PendingIntent.getService(this,0,new Intent(this,AlarmService.class).setAction(action).setData(uri),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    private Notification notification(Uri uri,String title){
        Notification.Builder b=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(title).setContentText("Alarm ringing").setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(true).setPriority(Notification.PRIORITY_MAX);
        if(uri!=null){Intent screen=new Intent(this,AlarmActivity.class).setData(uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);PendingIntent full=PendingIntent.getActivity(this,0,screen,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);b.setContentIntent(full).setFullScreenIntent(full,true).addAction(new Notification.Action.Builder(null,"Snooze 10 min",serviceAction(SNOOZE,uri)).build()).addAction(new Notification.Action.Builder(null,"Dismiss",serviceAction(DISMISS,uri)).build());}
        return b.build();
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){handler.removeCallbacks(watchdog);stopSound();if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();}
}
