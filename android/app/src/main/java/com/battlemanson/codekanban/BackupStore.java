package com.battlemanson.codekanban;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.DocumentsContract;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Snapshots are separate documents, so a failed write never overwrites an older backup. */
final class BackupStore {
    private static final int MAX_BYTES=20*1024*1024;
    private BackupStore() {}
    static String snapshot(JSONObject data,boolean dark,String themeMode) throws JSONException {
        String snapshot=new JSONObject().put("format","code-kanban-backup").put("version",1)
            .put("themeMode",themeMode).put("darkMode",dark).put("data",new JSONObject(data.toString())).toString();
        if(snapshot.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new JSONException("Backup is too large (limit 20 MB).");
        return snapshot;
    }
    static JSONObject validate(String json) throws JSONException {
        JSONObject backup=new JSONObject(json);
        if(!"code-kanban-backup".equals(backup.optString("format"))||backup.optInt("version")!=1)
            throw new JSONException("Not a supported Code Kanban backup.");
        if(!(backup.opt("darkMode") instanceof Boolean))throw new JSONException("Missing theme setting.");
        if(backup.has("themeMode")&&!java.util.Arrays.asList("light","dark","device").contains(backup.optString("themeMode")))throw new JSONException("Invalid theme setting.");
        JSONObject data=backup.getJSONObject("data");JSONArray boards=data.getJSONArray("boards");
        for(int i=0;i<boards.length();i++){
            JSONObject board=boards.getJSONObject(i);board.getString("id");board.getString("name");
            JSONArray columns=board.getJSONArray("columns");
            for(int j=0;j<columns.length();j++){
                JSONObject column=columns.getJSONObject(j);column.getString("id");column.getString("name");
                JSONArray cards=column.getJSONArray("cards");
                for(int k=0;k<cards.length();k++){JSONObject card=cards.getJSONObject(k);card.getString("id");card.getString("title");}
            }
        }
        if(data.has("defaultTemplate"))BoardTemplate.validate(data.getJSONObject("defaultTemplate"));
        if(data.has("defaults"))data.getJSONArray("defaults");
        return backup;
    }
    static void write(ContentResolver resolver,Uri tree,String json) throws IOException {
        Uri folder=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
        String name=String.format(java.util.Locale.ROOT,"code-kanban-%013d-%s.json",System.currentTimeMillis(),UUID.randomUUID().toString().substring(0,8));
        Uri file=DocumentsContract.createDocument(resolver,folder,"application/json",name);
        if(file==null)throw new IOException("The selected folder could not create a backup.");
        try(OutputStream out=resolver.openOutputStream(file,"w")){
            if(out==null)throw new IOException("The backup file could not be opened.");
            out.write(json.getBytes(StandardCharsets.UTF_8));out.flush();
        }catch(Exception e){try{DocumentsContract.deleteDocument(resolver,file);}catch(Exception ignored){}throw new IOException("Could not write backup: "+e.getMessage(),e);}
    }
    static String read(ContentResolver resolver,Uri file) throws IOException {
        try(InputStream in=resolver.openInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(in==null)throw new IOException("Could not open the selected file.");
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>MAX_BYTES)throw new IOException("Backup is too large.");out.write(buffer,0,n);}
            return out.toString("UTF-8");
        }
    }
}
