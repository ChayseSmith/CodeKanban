package com.battlemanson.codekanban;

import android.content.*;
import android.util.AtomicFile;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.lang.ref.WeakReference;
import java.util.*;

/** Main-thread coordinator. Firebase owns transport; our outbox also survives rejected writes. */
final class CloudSync {
    interface Host {JSONObject cloudData();boolean cloudApply(JSONArray boards,boolean initial);void cloudStatus();}
    private static CloudSync instance;
    static synchronized CloudSync get(Context context){if(instance==null)instance=new CloudSync(context.getApplicationContext());return instance;}
    final FirebaseAuth auth=FirebaseAuth.getInstance();
    private final FirebaseFirestore db=FirebaseFirestore.getInstance();private final Context context;private final SharedPreferences prefs;
    private WeakReference<Host> host=new WeakReference<>(null);private ListenerRegistration listener;private CloudReplica replica;private String uid="",status="Not connected";private boolean writing,needsApply,linking,blocked;
    private CloudSync(Context context){this.context=context;prefs=context.getSharedPreferences("cloud_sync",Context.MODE_PRIVATE);auth.addAuthStateListener(a->{stop();if(a.getCurrentUser()!=null&&a.getCurrentUser().getUid().equals(prefs.getString("linkedUid",""))&&host.get()!=null)start();else update("Sign in, then choose how to connect your boards.");});}
    String status(){return status+(replica==null?"":" · "+replica.pending.size()+" pending changes");}
    boolean linked(){return auth.getCurrentUser()!=null&&auth.getCurrentUser().getUid().equals(prefs.getString("linkedUid",""));}
    boolean busy(){return linking;}
    void attach(Host next){host=new WeakReference<>(next);if(linked())start();}
    void detach(Host previous){if(host.get()==previous){host=new WeakReference<>(null);if(listener!=null){listener.remove();listener=null;}}}
    private void stop(){if(listener!=null)listener.remove();listener=null;replica=null;uid="";writing=false;needsApply=false;blocked=false;}
    private void update(String message){status=message;Host current=host.get();if(current!=null)current.cloudStatus();}
    private CollectionReference collection(String user){return db.collection("users").document(user).collection("records");}
    private AtomicFile file(String user){return new AtomicFile(new File(context.getFilesDir(),"cloud-"+CloudRecords.id("user",user,"")+".json"));}
    private void persist(CloudReplica state,String user)throws Exception{AtomicFile file=file(user);FileOutputStream out=file.startWrite();try{out.write(state.json().toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}catch(Exception e){file.failWrite(out);throw e;}}
    private void start(){
        FirebaseUser user=auth.getCurrentUser();if(user==null||!linked())return;
        try{if(replica==null||!uid.equals(user.getUid())){uid=user.getUid();try(InputStream in=file(uid).openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);replica=CloudReplica.read(new JSONObject(out.toString("UTF-8")));}}
            if(listener!=null)return;final String account=uid;final CloudReplica session=replica;
            // Capture edits made locally while signed out or while this screen was stopped.
            Host current=host.get();if(current!=null)localSaved(current.cloudData());if(blocked)return;
            listener=collection(account).addSnapshotListener(MetadataChanges.INCLUDE,(snapshot,error)->{
                if(replica!=session||!account.equals(uid))return;
                if(error!=null){if(listener!=null)listener.remove();listener=null;update("Sync error: "+error.getMessage()+". Check the published Firestore rules.");return;}
                if(snapshot==null)return;
                try{session.remote(read(snapshot),!snapshot.getMetadata().isFromCache());persist(session,account);needsApply=true;apply();update(snapshot.getMetadata().isFromCache()?"Offline/cache: changes will retry when connected":"Connected to Firestore");}catch(Exception e){update("Could not apply cloud changes: "+e.getMessage());}
            });flush();
        }catch(Exception e){update("Cloud cache unavailable: "+e.getMessage()+". Reconnect explicitly to recover; local boards are unchanged.");}
    }
    private Map<String,String> read(QuerySnapshot snapshot)throws JSONException{Map<String,String> records=new TreeMap<>();for(DocumentSnapshot doc:snapshot.getDocuments()){Long schema=doc.getLong("schema");String json=doc.getString("json");if(schema==null||schema!=1||json==null)throw new JSONException("Unsupported cloud data format.");new JSONObject(json);records.put(doc.getId(),json);}return records;}
    void link(boolean upload){
        FirebaseUser user=auth.getCurrentUser();Host current=host.get();if(user==null||current==null||linking)return;if(writing||replica!=null&&!replica.pending.isEmpty()){update("Finish syncing pending changes before reconnecting. Publish the rules and use Retry sync if uploads are blocked.");return;}final String account=user.getUid();linking=true;update("Connecting to cloud boards…");
        collection(account).get(Source.SERVER).addOnCompleteListener(task->{linking=false;if(auth.getCurrentUser()==null||!account.equals(auth.getCurrentUser().getUid())||host.get()==null)return;
            if(!task.isSuccessful()){update("Could not connect: "+(task.getException()==null?"Unknown error":task.getException().getMessage())+". Publish the Firestore rules and try again.");return;}
            try{CloudReplica next=new CloudReplica();next.records.putAll(read(task.getResult()));Host target=host.get();Map<String,String> local=CloudRecords.encode(target.cloudData());
                if(upload){Map<String,String> before=new TreeMap<>();for(String id:local.keySet())if(next.records.containsKey(id)&&!next.records.get(id).equals(CloudRecords.DELETED))before.put(id,next.records.get(id));next.lastLocal.putAll(before);next.local(local);}
                JSONArray boards=CloudRecords.decode(next.records);if(!target.cloudApply(boards,true)){update("Could not save cloud boards locally; connection was not enabled.");return;}
                next.lastLocal.clear();next.lastLocal.putAll(CloudRecords.encode(target.cloudData()));persist(next,account);if(!prefs.edit().putString("linkedUid",account).commit())throw new IOException("Could not save cloud account selection.");
                stop();uid=account;replica=next;start();update("Connected to Firestore");
            }catch(Exception e){update("Could not connect boards: "+e.getMessage());}
        });
    }
    void localSaved(JSONObject data){if(replica==null||!linked())return;try{CloudReplica next=CloudReplica.read(replica.json());next.local(CloudRecords.encode(data));persist(next,uid);replica.records.clear();replica.records.putAll(next.records);replica.lastLocal.clear();replica.lastLocal.putAll(next.lastLocal);replica.pending.clear();replica.pending.putAll(next.pending);blocked=false;flush();}catch(Exception e){blocked=true;if(listener!=null){listener.remove();listener=null;}needsApply=false;update("Local save succeeded; cloud queue failed: "+e.getMessage()+". Retry sync before uninstalling.");}}
    private void flush(){
        if(writing||replica==null||!linked()||replica.pending.isEmpty())return;writing=true;final CloudReplica session=replica;final String account=uid;Map<String,CloudReplica.Write> sent=new LinkedHashMap<>();
        WriteBatch batch=db.batch();for(Map.Entry<String,CloudReplica.Write> entry:session.pending.entrySet()){if(sent.size()==400)break;sent.put(entry.getKey(),entry.getValue());Map<String,Object> doc=new HashMap<>();doc.put("schema",1);doc.put("json",entry.getValue().json);doc.put("changeId",entry.getValue().token);doc.put("updatedAt",FieldValue.serverTimestamp());batch.set(collection(account).document(entry.getKey()),doc);}
        batch.commit().addOnCompleteListener(task->{try{if(task.isSuccessful()){for(Map.Entry<String,CloudReplica.Write> e:sent.entrySet())session.acknowledged(e.getKey(),e.getValue().token);persist(session,account);}
                if(replica==session){writing=false;if(task.isSuccessful()){update("Changes saved to Firestore");flush();}else update("Cloud upload failed; changes remain queued: "+(task.getException()==null?"Unknown error":task.getException().getMessage()));}
            }catch(Exception e){if(replica==session){writing=false;update("Could not save cloud queue state: "+e.getMessage());}}});
    }
    void apply(){if(blocked||!needsApply||replica==null)return;Host target=host.get();if(target==null)return;try{JSONArray boards=CloudRecords.decode(replica.records);if(target.cloudApply(boards,false)){replica.lastLocal.clear();replica.lastLocal.putAll(CloudRecords.encode(target.cloudData()));persist(replica,uid);needsApply=false;}}catch(Exception e){update("Could not save incoming boards: "+e.getMessage());}}
    void retry(){if(replica==null)start();Host current=host.get();if(current!=null)localSaved(current.cloudData());if(!blocked)start();flush();apply();}
    void signOut(){stop();auth.signOut();update("Signed out. Boards remain available locally.");}
}
