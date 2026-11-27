package com.battlemanson.codekanban;

public final class UpdatePollingTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        UpdatePolling p=new UpdatePolling();
        check(p.start(0,false),"First check runs immediately");
        check(!p.start(59_999,false),"No check before one minute");
        check(p.start(60_000,false),"Minute check is allowed");
        check(p.start(60_001,true),"Resume/manual check bypasses ordinary interval");
        p.rateLimited(60_001,120_000);
        check(!p.start(100_000,true),"Resume/manual respects server backoff");
        check(!p.start(180_000,false),"Timer respects server backoff");
        check(p.start(180_001,false),"Checks resume after backoff expires");
        p.failed(180_001);p.failed(180_001);
        check(!p.start(300_000,false),"Repeated failure increases retry delay");
        check(p.start(300_001,false),"Exponential backoff eventually permits retry");
        p.success();p.failed(300_001);
        check(p.start(360_001,false),"Successful request resets failure count");
        for(int i=0;i<20;i++)p.failed(360_001);
        check(!p.start(1_260_000,false),"Maximum failure backoff is enforced");
        check(p.start(1_260_001,false),"Failure backoff caps at fifteen minutes");
        System.out.println("Minute cadence, resume checks, rate-limit handling and failure backoff passed.");
    }
}
