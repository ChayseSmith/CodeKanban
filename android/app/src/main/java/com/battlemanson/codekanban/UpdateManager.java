package com.battlemanson.codekanban;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Downloads an explicit public release and checks its digest, package, version and signer. */
final class UpdateManager {
    private static final String PREFS="app_updates";
    private static final long MAX_APK=200L*1024*1024;
    private static final AtomicBoolean CHECKING=new AtomicBoolean(),DOWNLOADING=new AtomicBoolean();
    private static final AtomicBoolean MANUAL_REQUESTED=new AtomicBoolean();
    private static volatile WeakReference<Activity> checkTarget=new WeakReference<>(null);
    private final Activity activity;
    UpdateManager(Activity activity){this.activity=activity;}
    private void ui(Runnable action){activity.runOnUiThread(()->{if(!activity.isFinishing()&&!activity.isDestroyed())action.run();});}
    private static void checkUi(java.util.function.Consumer<Activity> action){Activity target=checkTarget.get();if(target==null)return;target.runOnUiThread(()->{Activity latest=checkTarget.get();if(latest!=null&&!latest.isFinishing()&&!latest.isDestroyed())action.accept(latest);});}
    private HttpURLConnection connect(String address,String accept)throws IOException{
        URL url=new URL(address);for(int i=0;i<7;i++){if(!"https".equalsIgnoreCase(url.getProtocol()))throw new IOException("Updates require HTTPS.");HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(45000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Accept",accept);int status=c.getResponseCode();
            if(status>=300&&status<400){String location=c.getHeaderField("Location");c.disconnect();if(location==null)throw new IOException("Invalid update redirect.");url=new URL(url,location);continue;}
            if(status!=200){c.disconnect();throw new IOException("GitHub returned HTTP "+status+". If this is an update check, the "+selectedChannel()+" feed may not have been published yet. Check the release channel under Defaults.");}String type=c.getContentType();if(type!=null&&type.toLowerCase(Locale.ROOT).contains("text/html")){c.disconnect();throw new IOException("The update source returned a web page instead of a release.");}return c;}
        throw new IOException("Too many update redirects.");
    }
    private String selectedChannel(){String selected=activity.getSharedPreferences(PREFS,Activity.MODE_PRIVATE).getString("channel",BuildConfig.DEFAULT_RELEASE_CHANNEL);return "beta".equals(selected)?"beta":"stable";}
    private String channelUrl(){
        String channel=activity.getSharedPreferences("app_updates",Activity.MODE_PRIVATE).getString("channel",BuildConfig.DEFAULT_RELEASE_CHANNEL);
        if(!"beta".equals(channel))channel="stable";
        return BuildConfig.UPDATE_MANIFEST_URL.replace("/releases/latest/download/", "/releases/download/android-"+channel+"/");
    }
    void check(boolean manual){
        if(BuildConfig.UPDATE_MANIFEST_URL.isEmpty()){if(manual)message("Updates unavailable","Install an APK built by the GitHub release workflow to enable update checks.");else Toast.makeText(activity,"Updates unavailable in this build",Toast.LENGTH_LONG).show();return;}
        checkTarget=new WeakReference<>(activity);if(manual)MANUAL_REQUESTED.set(true);
        if(!CHECKING.compareAndSet(false,true)){if(manual)Toast.makeText(activity,"Already checking for updates",Toast.LENGTH_SHORT).show();return;}
        if(manual)Toast.makeText(activity,"Checking for updates…",Toast.LENGTH_SHORT).show();
        final String requestedUrl=channelUrl();
        new Thread(()->{try{
            HttpURLConnection c=connect(requestedUrl,"application/json");ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>65536)throw new IOException("Update metadata is too large.");bytes.write(buffer,0,n);}}finally{c.disconnect();}
            UpdateManifest manifest=UpdateManifest.parse(bytes.toString("UTF-8"));long version=manifest.version;String name=manifest.name,url=manifest.url,sha=manifest.sha256;
            PackageInfo current=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0);long installed=Build.VERSION.SDK_INT>=28?current.getLongVersionCode():current.versionCode;
            if(!requestedUrl.equals(channelUrl()))return;
            boolean report=MANUAL_REQUESTED.getAndSet(false);
            if(version>installed)checkUi(target->new AlertDialog.Builder(target).setTitle("Update available").setMessage("Version "+name+" is available. Download it?").setNegativeButton("Later",null).setPositiveButton("Download",(d,w)->new UpdateManager(target).download(version,url,sha)).show());
            else if(report)checkUi(target->new UpdateManager(target).message("You're up to date","Installed build: "+installed+". Latest "+selectedChannel()+" build: "+version+". A higher version code is required to update."));
        }catch(Exception e){boolean report=MANUAL_REQUESTED.getAndSet(false);android.util.Log.w("CodeKanbanUpdate","Update check failed",e);if(report)checkUi(target->new UpdateManager(target).message("Could not check for updates",e.getMessage()));else checkUi(target->Toast.makeText(target,"Could not check for updates. Tap Updates for details.",Toast.LENGTH_LONG).show());}finally{CHECKING.set(false);if(!requestedUrl.equals(channelUrl()))checkUi(target->new UpdateManager(target).check(false));}},"update-check").start();
    }
    private void download(long version,String url,String expectedHash){
        if(!DOWNLOADING.compareAndSet(false,true)){Toast.makeText(activity,"An update is already downloading",Toast.LENGTH_SHORT).show();return;}Toast.makeText(activity,"Downloading update…",Toast.LENGTH_LONG).show();
        new Thread(()->{File temp=new File(activity.getCacheDir(),"update-download.apk"),apk=new File(activity.getCacheDir(),"update.apk");try{
            HttpURLConnection c=connect(url,"application/octet-stream");MessageDigest digest=MessageDigest.getInstance("SHA-256");long size=0;
            try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(temp)){byte[] buffer=new byte[16384];int n;while((n=in.read(buffer))!=-1){size+=n;if(size>MAX_APK)throw new IOException("The APK is too large.");digest.update(buffer,0,n);out.write(buffer,0,n);}}finally{c.disconnect();}
            StringBuilder actual=new StringBuilder();for(byte b:digest.digest())actual.append(String.format(Locale.ROOT,"%02x",b&255));if(!actual.toString().equalsIgnoreCase(expectedHash))throw new IOException("The downloaded APK failed its SHA-256 check.");
            verify(temp,version);if(apk.exists()&&!apk.delete())throw new IOException("Could not replace the previous download.");if(!temp.renameTo(apk))throw new IOException("Could not save the update.");
            activity.getSharedPreferences(PREFS,Activity.MODE_PRIVATE).edit().putLong("pending",version).apply();ui(()->install(version));
        }catch(Exception e){temp.delete();ui(()->message("Update download failed",e.getMessage()));}finally{DOWNLOADING.set(false);}},"update-download").start();
    }
    private void verify(File apk,long expected)throws Exception{
        PackageManager pm=activity.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
        PackageInfo candidate=pm.getPackageArchiveInfo(apk.getAbsolutePath(),flags),installed=pm.getPackageInfo(activity.getPackageName(),flags);
        if(candidate==null||!activity.getPackageName().equals(candidate.packageName))throw new IOException("The APK has the wrong application ID.");
        long version=Build.VERSION.SDK_INT>=28?candidate.getLongVersionCode():candidate.versionCode,current=Build.VERSION.SDK_INT>=28?installed.getLongVersionCode():installed.versionCode;
        if(version!=expected||version<=current)throw new IOException("The APK version is not the expected newer version.");
        Signature[] a=Build.VERSION.SDK_INT>=28?(candidate.signingInfo==null?null:candidate.signingInfo.getApkContentsSigners()):candidate.signatures;
        Signature[] b=Build.VERSION.SDK_INT>=28?(installed.signingInfo==null?null:installed.signingInfo.getApkContentsSigners()):installed.signatures;
        if(a==null||b==null||a.length==0||!new HashSet<>(Arrays.asList(a)).equals(new HashSet<>(Arrays.asList(b))))throw new IOException("The APK is not signed with the installed app's key.");
    }
    boolean resumePending(){long version=activity.getSharedPreferences(PREFS,Activity.MODE_PRIVATE).getLong("pending",0);if(version>0&&activity.getPackageManager().canRequestPackageInstalls())install(version);return version>0;}
    private void install(long version){try{
        File apk=new File(activity.getCacheDir(),"update.apk");verify(apk,version);
        if(!activity.getPackageManager().canRequestPackageInstalls()){new AlertDialog.Builder(activity).setTitle("Allow app updates").setMessage("Allow Code Kanban to install this update, then return to the app.").setNegativeButton("Later",(d,w)->activity.getSharedPreferences(PREFS,Activity.MODE_PRIVATE).edit().remove("pending").apply()).setPositiveButton("Settings",(d,w)->activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())))).show();return;}
        Uri uri=Uri.parse("content://"+activity.getPackageName()+".updates/update.apk");Intent installer=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);activity.startActivity(installer);activity.getSharedPreferences(PREFS,Activity.MODE_PRIVATE).edit().remove("pending").apply();
    }catch(Exception e){activity.getSharedPreferences(PREFS,Activity.MODE_PRIVATE).edit().remove("pending").apply();message("Could not install update",e.getMessage());}}
    private void message(String title,String text){new AlertDialog.Builder(activity).setTitle(title).setMessage(text==null?"Please try again.":text).setPositiveButton("OK",null).show();}
}
