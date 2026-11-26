package com.battlemanson.codekanban;

import android.content.Context;
import android.graphics.Typeface;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Local Material Design Icons catalog; no network is used by the picker. */
final class MdiIcons {
    static final String DEFAULT="view-dashboard-outline";
    static final class Icon {
        final String name;final int codepoint;
        Icon(String name,int codepoint){this.name=name;this.codepoint=codepoint;}
        String glyph(){return new String(Character.toChars(codepoint));}
    }
    final Typeface font;final List<Icon> all;private final Map<String,Icon> byName;
    private static MdiIcons cached;
    static synchronized MdiIcons load(Context context)throws IOException {
        if(cached==null)cached=new MdiIcons(context.getApplicationContext());return cached;
    }
    private MdiIcons(Context context)throws IOException {
        font=Typeface.createFromAsset(context.getAssets(),"mdi/materialdesignicons-webfont.ttf");
        ArrayList<Icon> icons=new ArrayList<>();HashMap<String,Icon> index=new HashMap<>();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(context.getAssets().open("mdi/icons.tsv"),StandardCharsets.UTF_8))){
            String line;while((line=reader.readLine())!=null){String[] parts=line.split("\\t",2);if(parts.length!=2)throw new IOException("Invalid icon catalog");Icon icon=new Icon(parts[0],Integer.parseInt(parts[1],16));icons.add(icon);index.put(icon.name,icon);}
        }catch(NumberFormatException e){throw new IOException("Invalid icon catalog",e);}
        all=Collections.unmodifiableList(icons);byName=Collections.unmodifiableMap(index);
    }
    Icon get(String name){Icon icon=byName.get(name);return icon!=null?icon:byName.get(DEFAULT);}
    List<Icon> search(String query){String[] words=query.toLowerCase(Locale.ROOT).trim().split("\\s+");ArrayList<Icon> matches=new ArrayList<>();for(Icon icon:all){boolean ok=true;for(String word:words)if(!icon.name.contains(word)){ok=false;break;}if(ok)matches.add(icon);}return matches;}
}
