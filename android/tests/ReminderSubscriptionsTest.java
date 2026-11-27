package com.battlemanson.codekanban;

import android.content.*;
import org.json.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Standalone checks against android-all; no device, notification service or SDK installation needed. */
public final class ReminderSubscriptionsTest {
    static final class MemoryPreferences implements SharedPreferences {
        final Map<String,Object> values=new HashMap<>();
        public Map<String,?> getAll(){return new HashMap<>(values);}
        public String getString(String k,String d){return (String)values.getOrDefault(k,d);}
        @SuppressWarnings("unchecked") public Set<String> getStringSet(String k,Set<String> d){return (Set<String>)values.getOrDefault(k,d);}
        public int getInt(String k,int d){return (Integer)values.getOrDefault(k,d);}
        public long getLong(String k,long d){return (Long)values.getOrDefault(k,d);}
        public float getFloat(String k,float d){return (Float)values.getOrDefault(k,d);}
        public boolean getBoolean(String k,boolean d){return (Boolean)values.getOrDefault(k,d);}
        public boolean contains(String k){return values.containsKey(k);}
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}
        public Editor edit(){return new Editor(){
            final Map<String,Object> updates=new HashMap<>();boolean clear;
            public Editor putString(String k,String v){updates.put(k,v);return this;}
            public Editor putStringSet(String k,Set<String> v){updates.put(k,v);return this;}
            public Editor putInt(String k,int v){updates.put(k,v);return this;}
            public Editor putLong(String k,long v){updates.put(k,v);return this;}
            public Editor putFloat(String k,float v){updates.put(k,v);return this;}
            public Editor putBoolean(String k,boolean v){updates.put(k,v);return this;}
            public Editor remove(String k){updates.put(k,null);return this;}
            public Editor clear(){clear=true;return this;}
            public boolean commit(){if(clear)values.clear();for(Map.Entry<String,Object> e:updates.entrySet())if(e.getValue()==null)values.remove(e.getKey());else values.put(e.getKey(),e.getValue());return true;}
            public void apply(){commit();}
        };}
    }
    static final class Device extends ContextWrapper {
        final File folder;final Map<String,MemoryPreferences> preferences;
        Device(String id,Map<String,MemoryPreferences> preferences)throws Exception{super(null);this.preferences=preferences;folder=Files.createTempDirectory("kanban-reminders-test").toFile();try(DataOutputStream out=new DataOutputStream(new FileOutputStream(new File(folder,"reminder-device-id")))){out.writeUTF(id);}}
        @Override public File getNoBackupFilesDir(){return folder;}
        @Override public File getFilesDir(){return folder;}
        @Override public Object getSystemService(String name){return null;}
        @Override public String getPackageName(){return "com.battlemanson.codekanban";}
        @Override public ComponentName startService(Intent intent){return null;}
        @Override public SharedPreferences getSharedPreferences(String name,int mode){return preferences.computeIfAbsent(name,k->new MemoryPreferences());}
        void cleanup(){for(File f:folder.listFiles())f.delete();folder.delete();}
    }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        Map<String,MemoryPreferences> prefs=new HashMap<>();Device original=new Device("original",prefs),other=new Device("other",prefs);
        try{
            JSONObject reminder=new JSONObject().put("id","reminder").put("at",123456);
            JSONObject card=new JSONObject().put("id","card").put("reminders",new JSONArray().put(reminder));
            JSONObject data=new JSONObject().put("boards",new JSONArray().put(new JSONObject().put("id","board").put("columns",new JSONArray().put(new JSONObject().put("cards",new JSONArray().put(card))))));
            check(ReminderSubscriptions.subscribed(original,"card",reminder),"Legacy local reminder survives upgrade before migration");
            check(ReminderSubscriptions.prepare(original,data,false),"Local reminder adopted");
            check("original".equals(reminder.getString("originDeviceId")),"Origin assigned");
            check(ReminderSubscriptions.subscribed(original,"card",reminder),"Original device enabled");
            check(!ReminderSubscriptions.subscribed(other,"card",reminder),"Other device defaults silent");
            ReminderSubscriptions.set(other,"card",reminder,false);
            check(ReminderSubscriptions.subscribed(original,"card",reminder),"Decline does not change original device");
            ReminderSubscriptions.set(other,"card",reminder,true);
            check(ReminderSubscriptions.subscribed(other,"card",reminder),"Explicit subscribe enabled");
            String occurrence=ReminderScheduler.key("card","reminder",123456);
            original.getSharedPreferences(ReminderScheduler.PREFS,0).edit().putBoolean("active/"+occurrence,true).putString("activeUri/"+occurrence,"codekanban://reminder/board/card/reminder/123456").putLong("snooze/"+occurrence,999999).commit();
            ReminderSubscriptions.set(original,"card",reminder,false);
            check(!original.getSharedPreferences(ReminderScheduler.PREFS,0).contains("active/"+occurrence),"Unsubscribe clears ringing state");
            check(!original.getSharedPreferences(ReminderScheduler.PREFS,0).contains("snooze/"+occurrence),"Unsubscribe clears snooze state");
            check(!ReminderSubscriptions.subscribed(original,"card",reminder),"Origin can unsubscribe");
            check(ReminderSubscriptions.subscribed(other,"card",reminder),"Other device remains enabled");
            reminder.put("at",999999);
            check(!ReminderSubscriptions.subscribed(original,"card",reminder),"Date edits do not reset opt-out");
            check(!ReminderSubscriptions.prepare(other,data,true),"Imports preserve origin");
            reminder.remove("originDeviceId");ReminderSubscriptions.prepare(other,data,true);
            check("unknown-import".equals(reminder.getString("originDeviceId")),"Legacy imports are not adopted");
            JSONObject newReminder=new JSONObject().put("id","new");card.getJSONArray("reminders").put(newReminder);ReminderSubscriptions.prepare(other,data,true);
            check(!ReminderSubscriptions.subscribed(other,"card",newReminder),"Legacy imported reminder stays silent");
            System.out.println("Reminder subscription isolation, migration, opt-in/out and import checks passed.");
        }finally{original.cleanup();other.cleanup();}
    }
}
