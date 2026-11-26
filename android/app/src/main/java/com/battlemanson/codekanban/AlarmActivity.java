package com.battlemanson.codekanban;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.*;
import java.util.List;

/** Alarm UI may appear over the lock screen; it never unlocks the device. */
public final class AlarmActivity extends Activity {
    private Uri alarm;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable checkActive=new Runnable(){@Override public void run(){if(alarm==null){finish();return;}try{List<String> p=alarm.getPathSegments();String key=ReminderScheduler.key(p.get(1),p.get(2),Long.parseLong(p.get(3)));if(!getSharedPreferences(ReminderScheduler.PREFS,MODE_PRIVATE).getBoolean("active/"+key,false)){finish();return;}}catch(Exception e){finish();return;}handler.postDelayed(this,1000);}};
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override protected void onCreate(Bundle state){super.onCreate(state);if(android.os.Build.VERSION.SDK_INT>=27){setShowWhenLocked(true);setTurnScreenOn(true);}else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);alarm=getIntent().getData();showAlarm();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);alarm=intent.getData();showAlarm();}
    private void showAlarm(){
        if(alarm==null){finish();return;}String title="Work item alarm";try{org.json.JSONObject[] entry=ReminderScheduler.lookup(this,alarm);if(entry==null){finish();return;}title=entry[1].optString("title",title);}catch(Exception e){finish();return;}
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setGravity(Gravity.CENTER);layout.setPadding(dp(24),dp(48),dp(24),dp(48));layout.setBackgroundColor(Color.rgb(16,21,34));setContentView(layout);
        TextView label=new TextView(this);label.setText("ALARM");label.setTextColor(Color.rgb(179,168,255));label.setTextSize(16);label.setGravity(Gravity.CENTER);layout.addView(label);
        TextView name=new TextView(this);name.setText(title);name.setTextColor(Color.WHITE);name.setTextSize(28);name.setGravity(Gravity.CENTER);name.setPadding(0,dp(20),0,dp(40));layout.addView(name);
        Button snooze=new Button(this);snooze.setText("Snooze 10 minutes");snooze.setOnClickListener(v->respond(AlarmService.SNOOZE));layout.addView(snooze,new LinearLayout.LayoutParams(-1,-2));
        TextView swipeLabel=new TextView(this);swipeLabel.setText("Swipe right to dismiss");swipeLabel.setTextColor(Color.WHITE);swipeLabel.setGravity(Gravity.CENTER);swipeLabel.setPadding(0,dp(32),0,dp(6));layout.addView(swipeLabel);
        SeekBar swipe=new SeekBar(this);swipe.setMax(100);swipe.setContentDescription("Swipe right to dismiss alarm");layout.addView(swipe,new LinearLayout.LayoutParams(-1,-2));swipe.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean user){}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){if(b.getProgress()>=85)respond(AlarmService.DISMISS);else b.setProgress(0);}});
        Button accessibleDismiss=new Button(this);accessibleDismiss.setText("Dismiss alarm");accessibleDismiss.setOnClickListener(v->respond(AlarmService.DISMISS));layout.addView(accessibleDismiss,new LinearLayout.LayoutParams(-1,-2));
    }
    private void respond(String action){startService(new Intent(this,AlarmService.class).setAction(action).setData(alarm));finish();}
    @Override protected void onResume(){super.onResume();handler.post(checkActive);}
    @Override protected void onPause(){handler.removeCallbacks(checkActive);super.onPause();}
    @Override public void onBackPressed(){/* An active alarm is stopped only by an explicit response. */}
}
