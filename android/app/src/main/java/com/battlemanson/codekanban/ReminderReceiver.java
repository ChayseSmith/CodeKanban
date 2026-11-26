package com.battlemanson.codekanban;
import android.content.*;
public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){if(intent!=null&&ReminderScheduler.ACTION.equals(intent.getAction()))ReminderScheduler.deliver(context,intent.getData());}
}
