package com.battlemanson.codekanban;

import org.json.JSONObject;
import java.io.IOException;
import java.net.URI;

final class UpdateManifest {
    final long version;final String name,url,sha256;
    private UpdateManifest(long version,String name,String url,String sha256){this.version=version;this.name=name;this.url=url;this.sha256=sha256;}
    static UpdateManifest parse(String json)throws Exception{
        JSONObject manifest=new JSONObject(json);long version=manifest.getLong("versionCode");String name=manifest.getString("versionName"),url=manifest.getString("apkUrl"),sha=manifest.getString("sha256");URI uri=new URI(url);
        if(version<=0||version>2100000000L||name.trim().isEmpty()||name.length()>200||!"https".equalsIgnoreCase(uri.getScheme())||!"github.com".equalsIgnoreCase(uri.getHost())||!sha.matches("(?i)[0-9a-f]{64}"))throw new IOException("Invalid release metadata.");
        return new UpdateManifest(version,name,url,sha);
    }
}
