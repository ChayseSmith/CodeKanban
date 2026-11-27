package com.battlemanson.codekanban;

/** Monotonic-time scheduling, shared by automatic checks across activity recreations. */
final class UpdatePolling {
    static final long INTERVAL=60_000, MAX_BACKOFF=15*60_000;
    private long next, rateLimitedUntil;private int failures;
    synchronized boolean start(long now,boolean manual){if(now<rateLimitedUntil||(!manual&&now<next))return false;next=now+INTERVAL;return true;}
    synchronized void success(){failures=0;}
    synchronized void failed(long now){failures=Math.min(failures+1,5);next=Math.max(next,now+Math.min(MAX_BACKOFF,INTERVAL*(1L<<(failures-1))));}
    synchronized void rateLimited(long now,long delay){rateLimitedUntil=Math.max(rateLimitedUntil,now+Math.max(INTERVAL,delay));next=Math.max(next,rateLimitedUntil);}
}
