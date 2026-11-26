package com.battlemanson.codekanban;
import android.content.*;
public final class ReminderBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){if(intent==null)return;String action=intent.getAction();if(Intent.ACTION_BOOT_COMPLETED.equals(action)){android.content.SharedPreferences p=context.getSharedPreferences(ReminderScheduler.PREFS,Context.MODE_PRIVATE);android.content.SharedPreferences.Editor e=p.edit();for(String key:p.getAll().keySet())if(key.startsWith("active/")||key.startsWith("activeUri/"))e.remove(key);e.commit();}if(Intent.ACTION_BOOT_COMPLETED.equals(action)||"android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED".equals(action))ReminderScheduler.syncSaved(context);}
}
