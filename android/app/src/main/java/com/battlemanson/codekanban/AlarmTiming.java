package com.battlemanson.codekanban;

final class AlarmTiming {
    private static final long SNOOZE_MILLIS=10*60*1000L;
    private AlarmTiming() {}
    static long snoozeUntil(long now){return Math.addExact(now,SNOOZE_MILLIS);}
    static long nextTrigger(long scheduled,long snoozed){return snoozed>0?snoozed:scheduled;}
}
