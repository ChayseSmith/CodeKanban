package com.battlemanson.codekanban;

import android.app.*;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private int INK, MUTED, ACCENT, BG, SURFACE, BORDER; private boolean dark;
    private JSONObject data; private String current="", tab="boards"; private LinearLayout root, content; private ScrollView boardScroll;
    private boolean skipNextUpdateCheck, defaultColumnsExpanded, updatesExpanded, backupExpanded;
    private final android.os.Handler updateHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable betaUpdateCheck=new Runnable(){@Override public void run(){new UpdateManager(MainActivity.this).checkPeriodically();updateHandler.postDelayed(this,UpdatePolling.INTERVAL);}};
    private String themeMode(){return getPreferences(MODE_PRIVATE).getString("themeMode","device");}
    private static final int PICK_BACKUP_FOLDER=1001,PICK_RESTORE_FILE=1002;
    private static final int PICK_ALARM_SOUND=1003;
    private JSONObject soundChoice;private TextView soundChoiceLabel;
    private static final ExecutorService BACKUPS=Executors.newSingleThreadExecutor();
    private static final java.util.concurrent.atomic.AtomicLong BACKUP_SEQUENCE=new java.util.concurrent.atomic.AtomicLong();
    private final Map<View,JSONObject> dropColumns=new LinkedHashMap<>();
    private final Map<String,View> cardViews=new HashMap<>();
    private final Set<String> expandedChildren=new HashSet<>(), expandedTasks=new HashSet<>();
    private final Map<View,JSONObject> dropBoards=new LinkedHashMap<>(); private LinearLayout dragBoardTargets;
    private final String[] palette={"#6366f1","#0d9488","#f59e0b","#e11d48","#0284c7","#9333ea"};
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private JSONObject obj(){return new JSONObject();} private JSONArray arr(){return new JSONArray();}
    private JSONObject put(JSONObject o,String k,Object v){try{o.put(k,v);}catch(JSONException e){throw new IllegalStateException(e);}return o;}
    private String id(){return UUID.randomUUID().toString();}
    private JSONArray array(JSONObject o,String k){JSONArray a=o.optJSONArray(k);if(a==null){a=arr();put(o,k,a);}return a;}
    private JSONObject at(JSONArray a,int i){return a.optJSONObject(i);}
    private JSONObject find(JSONArray a,String id){for(int i=0;i<a.length();i++){JSONObject o=at(a,i);if(o!=null&&id.equals(o.optString("id")))return o;}return null;}
    private JSONObject board(){return find(array(data,"boards"),current);}
    private JSONObject col(JSONObject b,String id){return find(array(b,"columns"),id);}
    private void remove(JSONArray a,String id){for(int i=0;i<a.length();i++)if(id.equals(at(a,i).optString("id"))){a.remove(i);return;}}
    @Override public void onCreate(Bundle state){
        dark="dark".equals(themeMode())||("device".equals(themeMode())&&(getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES);
        setTheme(dark?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(state);
        INK=Color.parseColor(dark?"#F1F5F9":"#1E293B");
        MUTED=Color.parseColor(dark?"#A8B5C8":"#64748B");
        ACCENT=Color.parseColor(dark?"#B3A8FF":"#4338CA");
        BG=Color.parseColor(dark?"#101522":"#F5F7FC");
        SURFACE=Color.parseColor(dark?"#1C2535":"#FFFFFF");
        BORDER=Color.parseColor(dark?"#354154":"#E1E6F0");
        getWindow().setStatusBarColor(dark?BG:Color.rgb(38,32,102));
        getWindow().setNavigationBarColor(dark?BG:Color.WHITE);
        int systemUi=dark?0:View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        if(!dark&&android.os.Build.VERSION.SDK_INT>=35)systemUi|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        getWindow().getDecorView().setSystemUiVisibility(systemUi);
        skipNextUpdateCheck=getIntent()!=null&&"com.battlemanson.codekanban.OPEN_REMINDER".equals(getIntent().getAction());
        load();migrateLegacyReminders();if(data!=null)try{if(ReminderSubscriptions.prepare(this,data,false))save();}catch(JSONException e){error("Could not initialize reminder subscriptions",e.getMessage());}if(getIntent().getBooleanExtra("settingsOpen",false)){tab="settings";getIntent().removeExtra("settingsOpen");}show();openReminder(getIntent());
    }
    @Override protected void onResume(){super.onResume();UpdateManager.attach(this);updateHandler.removeCallbacks(betaUpdateCheck);updateHandler.postDelayed(betaUpdateCheck,UpdatePolling.INTERVAL);syncReminders();UpdateManager updates=new UpdateManager(this);boolean pending=updates.resumePending();if(skipNextUpdateCheck)skipNextUpdateCheck=false;else if(!pending&&data!=null)updates.checkOnResume();}
    @Override protected void onStop(){updateHandler.removeCallbacks(betaUpdateCheck);UpdateManager.detach(this);super.onStop();}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==2001)syncReminders();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent!=null&&"com.battlemanson.codekanban.OPEN_REMINDER".equals(intent.getAction()))skipNextUpdateCheck=true;openReminder(intent);}
    private void openReminder(Intent intent){if(intent!=null&&ReminderSubscriptions.OPEN.equals(intent.getAction())){tab="reminders";show();intent.setAction(null);return;}if(intent==null||!"com.battlemanson.codekanban.OPEN_REMINDER".equals(intent.getAction())||data==null)return;String boardId=intent.getStringExtra("boardId"),id=intent.getStringExtra("cardId");if(boardId!=null&&id!=null){JSONObject b=find(array(data,"boards"),boardId);if(b!=null)revealCard(b,id);}intent.setAction(null);}
    private void syncReminders(){try{ReminderScheduler.sync(this,data);if(data!=null)ReminderSubscriptions.discover(this,data);}catch(Exception e){android.util.Log.e("CodeKanbanReminder","Could not schedule reminders",e);}}
    private void load(){
        android.util.AtomicFile file=new android.util.AtomicFile(new File(getFilesDir(),"boards.json"));
        try(FileInputStream in=file.openRead()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);data=new JSONObject(out.toString("UTF-8"));}
        catch(FileNotFoundException e){data=obj();array(data,"boards");put(data,"defaults",new JSONArray(Arrays.asList("Backlog","To Do","Doing","Done")));}
        catch(Exception e){new AlertDialog.Builder(this).setTitle("Unable to open boards").setMessage("Your saved data could not be read. It has not been replaced. Close the app and recover it before continuing.\n\n"+e.getMessage()).setPositiveButton("Close",(d,w)->finish()).setCancelable(false).show();data=null;}
    }
    private void migrateLegacyReminders(){
        if(data==null)return;boolean changed=false;
        try{JSONArray boards=data.optJSONArray("boards");if(boards==null)return;for(int b=0;b<boards.length();b++){JSONObject board=boards.optJSONObject(b);if(board==null)continue;JSONArray cols=board.optJSONArray("columns");if(cols==null)continue;for(int c=0;c<cols.length();c++){JSONObject col=cols.optJSONObject(c);JSONArray cards=col==null?null:col.optJSONArray("cards");if(cards==null)continue;for(int i=0;i<cards.length();i++){JSONObject card=cards.optJSONObject(i),template=card==null?null:card.optJSONObject("templateData");JSONArray old=template==null?null:template.optJSONArray("reminders");if(card==null||card.has("reminders")||old==null)continue;JSONArray copied=new JSONArray(old.toString());for(int j=0;j<copied.length();j++)copied.getJSONObject(j).put("id",id());card.put("reminders",copied);changed=true;}}}if(changed)save();}catch(JSONException e){android.util.Log.e("CodeKanbanReminder","Could not migrate template reminders",e);}
    }
    private boolean save(){try{ReminderSubscriptions.prepare(this,data,false);}catch(JSONException e){error("Could not prepare reminders",e.getMessage());return false;}android.util.AtomicFile file=new android.util.AtomicFile(new File(getFilesDir(),"boards.json"));FileOutputStream out=null;try{out=file.startWrite();out.write(data.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);Toast.makeText(this,"Could not save: "+e.getMessage(),Toast.LENGTH_LONG).show();return false;}queueBackup();syncReminders();return true;}
    private GradientDrawable shape(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(16));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private Drawable columnBackground(JSONObject column,boolean highlighted){return roundedAccentBackground(parseColor(column.optString("color")),highlighted);}
    private Drawable roundedAccentBackground(int color,boolean highlighted){
        return new Drawable(){final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(Canvas canvas){
                Rect bounds=getBounds();float radius=Math.min(dp(16),Math.min(bounds.width(),bounds.height())/2f);
                paint.setStyle(Paint.Style.FILL);paint.setColor(SURFACE);canvas.drawRoundRect(bounds.left,bounds.top,bounds.right,bounds.bottom,radius,radius,paint);
                float border=dp(highlighted?2:1),edge=border/2;
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(border);paint.setColor(highlighted?color:BORDER);
                canvas.drawRoundRect(bounds.left+edge,bounds.top+edge,bounds.right-edge,bounds.bottom-edge,Math.max(0,radius-edge),Math.max(0,radius-edge),paint);
                float width=Math.min(dp(4),Math.min(bounds.width(),bounds.height())),inset=width/2,curve=Math.max(0,radius-inset);
                float left=bounds.left+inset,top=bounds.top+inset,bottom=bounds.bottom-inset;
                android.graphics.Path accent=new android.graphics.Path();accent.moveTo(left+curve,top);
                if(curve>0)accent.arcTo(new android.graphics.RectF(left,top,left+2*curve,top+2*curve),270,-90);
                accent.lineTo(left,bottom-curve);
                if(curve>0)accent.arcTo(new android.graphics.RectF(left,bottom-2*curve,left+2*curve,bottom),180,-90);
                paint.setStrokeWidth(width);paint.setStrokeCap(Paint.Cap.BUTT);paint.setColor(color);canvas.drawPath(accent,paint);
            }
            @Override public void getOutline(android.graphics.Outline outline){outline.setRoundRect(getBounds(),dp(16));}
            @Override public void setAlpha(int alpha){} @Override public void setColorFilter(ColorFilter filter){} @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        };
    }
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private void pad(View v,int n){v.setPadding(dp(n),dp(n),dp(n),dp(n));}
    private TextView action(String title,Runnable r){TextView t=text(title,15,ACCENT,true);pad(t,12);t.setGravity(Gravity.CENTER);t.setOnClickListener(v->r.run());return t;}
    private void show(){if(data==null)return;root=vertical();root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            int left,top,right,bottom;
            if(android.os.Build.VERSION.SDK_INT>=30){
                int types=WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout();
                if(android.os.Build.VERSION.SDK_INT>=35)types|=WindowInsets.Type.ime();
                android.graphics.Insets safe=insets.getInsets(types);
                left=safe.left;top=safe.top;right=safe.right;bottom=safe.bottom;
            }else{left=insets.getSystemWindowInsetLeft();top=insets.getSystemWindowInsetTop();right=insets.getSystemWindowInsetRight();bottom=insets.getSystemWindowInsetBottom();}
            view.setPadding(left,top,right,bottom);return insets;
        });
        setContentView(root);root.requestApplyInsets();
        LinearLayout header=vertical();header.setBackgroundColor(SURFACE);header.setPadding(dp(20),dp(18),dp(20),dp(12));root.addView(header);
        LinearLayout brandRow=new LinearLayout(this);brandRow.setGravity(Gravity.CENTER_VERTICAL);header.addView(brandRow);
        TextView brand=text("CODE KANBAN",12,ACCENT,true);brandRow.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        if("beta".equals(getSharedPreferences("app_updates",MODE_PRIVATE).getString("channel","stable"))){TextView build=text("Build "+BuildConfig.VERSION_CODE,12,MUTED,false);build.setGravity(Gravity.RIGHT);brandRow.addView(build);}
        String title=tab.equals("boards")?(board()==null?"Your boards":board().optString("name")):tab.equals("all")?"Across boards":tab.equals("reminders")?"Reminders":"Settings";
        LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);header.addView(titleRow);if(tab.equals("boards")&&board()!=null){TextView icon=boardIcon(board(),22);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(36),dp(36));p.rightMargin=dp(10);titleRow.addView(icon,p);}TextView h=text(title,27,INK,true);titleRow.addView(h,new LinearLayout.LayoutParams(0,-2,1));TextView sub=text(tab.equals("boards")?(board()==null?"Organize work, your way":"Board · "+path(board())):tab.equals("all")?"Work items from every board":tab.equals("reminders")?"Manage alerts on this device":"Appearance, templates, updates and backups",13,MUTED,false);header.addView(sub);
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);LinearLayout nav=new LinearLayout(this);scroll.addView(nav);header.addView(scroll);
        nav.addView(action("Boards",()->{if(tab.equals("boards"))current="";tab="boards";show();}));nav.addView(action("All work",()->{tab="all";show();}));nav.addView(action("Reminders",()->{tab="reminders";show();}));nav.addView(action("Settings",()->{tab="settings";show();}));
        ScrollView sv=new ScrollView(this);boardScroll=sv;sv.setFillViewport(true);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));content=vertical();content.setPadding(dp(16),dp(16),dp(16),dp(28));sv.addView(content);
        if(tab.equals("settings"))settings();else if(tab.equals("all"))all();else if(tab.equals("reminders"))remindersView();else if(board()==null)boards();else boardView();
    }
    private String path(JSONObject b){ArrayList<String> parts=new ArrayList<>();HashSet<String> seen=new HashSet<>();while(b!=null&&seen.add(b.optString("id"))){parts.add(b.optString("name"));b=find(array(data,"boards"),b.optString("parent"));}Collections.reverse(parts);return android.text.TextUtils.join(" / ",parts);}
    private void section(String s){TextView t=text(s,18,INK,true);t.setPadding(dp(4),dp(18),dp(4),dp(10));content.addView(t);}
    private LinearLayout tile(){LinearLayout l=vertical();pad(l,16);l.setBackground(shape(SURFACE,BORDER));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);content.addView(l,p);return l;}
    private void button(String s,Runnable r){TextView b=action(s,r);b.setTextColor(dark?Color.rgb(26,20,69):Color.WHITE);b.setBackground(shape(ACCENT,0));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);content.addView(b,p);}
    private void empty(String s){TextView t=text(s,15,MUTED,false);pad(t,16);content.addView(t);}
    private void boards(){button("+ Create board",()->newBoard(""));JSONArray boards=array(data,"boards");int count=0;for(int i=0;i<boards.length();i++){JSONObject b=at(boards,i);if(!b.optString("parent").isEmpty())continue;boardTile(b);count++;}if(count==0)empty("Create your first board to start organizing work.");}
    private int boardIconColor(JSONObject board){try{return Color.parseColor(board.optString("boardColor","#8B80FF"));}catch(Exception e){return Color.parseColor("#8B80FF");}}
    private TextView boardIcon(JSONObject board,int size){int color=boardIconColor(board);TextView icon=text("▦",size,color,true);try{MdiIcons library=MdiIcons.load(this);MdiIcons.Icon selected=library.get(board.optString("boardIcon",MdiIcons.DEFAULT));if(selected!=null){icon.setTypeface(library.font);icon.setText(selected.glyph());}}catch(Exception e){android.util.Log.e("CodeKanban","Unable to load board icons",e);}icon.setGravity(Gravity.CENTER);icon.setIncludeFontPadding(false);icon.setBackground(shape(BoardIconPicker.iconBackdrop(color),0));icon.setContentDescription("Board icon");return icon;}
    private void boardTile(JSONObject b){LinearLayout tile=tile(),row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);tile.addView(row);TextView icon=boardIcon(b,26);LinearLayout.LayoutParams iconSize=new LinearLayout.LayoutParams(dp(44),dp(44));iconSize.rightMargin=dp(12);row.addView(icon,iconSize);LinearLayout labels=vertical();row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));labels.addView(text(b.optString("name"),18,INK,true));labels.addView(text(countCards(b)+" work items  ·  "+childCount(b.optString("id"))+" nested boards",13,MUTED,false));tile.setOnClickListener(v->openBoard(b.optString("id")));tile.setOnLongClickListener(v->{boardContext(b);return true;});}
    private void boardContext(JSONObject board){new AlertDialog.Builder(this).setTitle(board.optString("name")).setItems(new String[]{"Rename","Set Icon","Delete"},(d,choice)->{if(choice==0)renameBoard(board);else if(choice==1)boardContextIcon(board);else confirmDeleteBoard(board);}).show();}
    private void refreshKeepingScroll(){int y=boardScroll==null?0:boardScroll.getScrollY();show();boardScroll.post(()->boardScroll.scrollTo(0,y));}
    private void boardContextIcon(JSONObject board){BoardIconPicker.show(this,board.optString("boardIcon",MdiIcons.DEFAULT),boardIconColor(board),INK,MUTED,SURFACE,BORDER,ACCENT,(name,color)->{Object oldIcon=board.opt("boardIcon"),oldColor=board.opt("boardColor");put(board,"boardIcon",name);put(board,"boardColor",String.format(Locale.ROOT,"#%06X",color&0xffffff));if(!save()){if(oldIcon==null)board.remove("boardIcon");else put(board,"boardIcon",oldIcon);if(oldColor==null)board.remove("boardColor");else put(board,"boardColor",oldColor);}refreshKeepingScroll();});}
    private void renameBoard(JSONObject board){input("Rename board","Board name",board.optString("name"),name->{String before=board.optString("name");put(board,"name",name);if(!save())put(board,"name",before);refreshKeepingScroll();});}
    private int countCards(JSONObject b){int n=0;JSONArray a=array(b,"columns");for(int i=0;i<a.length();i++)n+=array(at(a,i),"cards").length();return n;}
    private int childCount(String parent){int n=0;JSONArray a=array(data,"boards");for(int i=0;i<a.length();i++)if(parent.equals(at(a,i).optString("parent")))n++;return n;}
    private JSONObject template() throws JSONException {
        JSONObject template=data.optJSONObject("defaultTemplate");
        if(template==null){template=BoardTemplate.fromLegacy(array(data,"defaults"),palette);put(data,"defaultTemplate",template);}
        BoardTemplate.validate(template);return template;
    }
    private void newBoard(String parent){input("Create board","Board name","",name->{try{JSONObject b=BoardTemplate.createBoard(template(),name,parent);array(data,"boards").put(b);if(save()){current=b.optString("id");show();}}catch(JSONException e){error("Invalid default template",e.getMessage());}});}
    private void openBoard(String id){current=id;show();JSONObject b=board();if(b!=null)checkDefault(b,false);}
    private void checkDefault(JSONObject b,boolean force){try{JSONObject t=template();if(!force&&(b.optBoolean("skipDefaultPrompt")||BoardTemplate.matches(b,t)))return;
        new AlertDialog.Builder(this).setTitle("Apply default board layout?").setMessage("This board's columns differ from your default. Matching columns keep their work items. Work items in other columns move to the first default column. Starter items are not added.").setPositiveButton("Apply",(d,w)->{try{JSONArray before=b.getJSONArray("columns");boolean skipped=b.optBoolean("skipDefaultPrompt");put(b,"columns",BoardTemplate.migrate(b,template()));put(b,"skipDefaultPrompt",false);if(!save()){put(b,"columns",before);put(b,"skipDefaultPrompt",skipped);}show();}catch(JSONException e){error("Could not apply default",e.getMessage());}}).setNegativeButton("Don't ask again",(d,w)->{put(b,"skipDefaultPrompt",true);save();}).setNeutralButton("Later",null).show();
        }catch(JSONException e){error("Invalid default template",e.getMessage());}}
    private void error(String title,String message){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK",null).show();}
    private JSONObject newColumn(String name,String color){return put(put(put(put(obj(),"id",id()),"name",name),"color",color),"cards",arr());}
    private void boardView(){dropColumns.clear();dropBoards.clear();cardViews.clear();JSONObject b=board();if(b==null){current="";show();return;}String bid=current;
        LinearLayout row=new LinearLayout(this);row.addView(action("‹ Back",()->{if(b.optString("parent").isEmpty()){current="";show();}else openBoard(b.optString("parent"));}));row.addView(action("+ Nested board",()->newBoard(bid)));row.addView(action("⋮",()->boardMenu(b)));content.addView(row);
        JSONArray boards=array(data,"boards");boolean children=false;for(int i=0;i<boards.length();i++){JSONObject child=at(boards,i);if(bid.equals(child.optString("parent"))){if(!children)section("Nested boards");children=true;boardTile(child);}}
        dragBoardTargets=vertical();dragBoardTargets.setVisibility(View.GONE);content.addView(dragBoardTargets);TextView hint=text("DROP ON A BOARD TO MOVE",13,MUTED,true);pad(hint,10);dragBoardTargets.addView(hint);for(int i=0;i<boards.length();i++){JSONObject target=at(boards,i);if(target==b)continue;LinearLayout destination=new LinearLayout(this);destination.setGravity(Gravity.CENTER_VERTICAL);pad(destination,10);destination.setBackground(shape(SURFACE,BORDER));TextView icon=boardIcon(target,20);LinearLayout.LayoutParams iconSize=new LinearLayout.LayoutParams(dp(34),dp(34));iconSize.rightMargin=dp(10);destination.addView(icon,iconSize);destination.addView(text(path(target),16,INK,true),new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);dragBoardTargets.addView(destination,p);dropBoards.put(destination,target);}
        LinearLayout columnHeader=new LinearLayout(this);columnHeader.setGravity(Gravity.CENTER_VERTICAL);TextView heading=text("Columns",18,INK,true);columnHeader.addView(heading,new LinearLayout.LayoutParams(0,-2,1));columnHeader.addView(action("Collapse all",()->collapseAllColumns(b)));content.addView(columnHeader);
        JSONArray cols=array(b,"columns");for(int i=0;i<cols.length();i++)columnView(b,at(cols,i));button("+ Add column",()->input("Add column","Column name","",name->{cols.put(newColumn(name,palette[cols.length()%palette.length]));save();show();}));
    }
    private void collapseAllColumns(JSONObject board){
        JSONArray columns=array(board,"columns");ArrayList<JSONObject> changed=new ArrayList<>();ArrayList<Object> previous=new ArrayList<>();
        for(int i=0;i<columns.length();i++){JSONObject column=at(columns,i);if(column!=null&&!column.optBoolean("collapsed")){changed.add(column);previous.add(column.has("collapsed")?column.opt("collapsed"):null);put(column,"collapsed",true);}}
        if(changed.isEmpty())return;
        if(!save()){for(int i=0;i<changed.size();i++){if(previous.get(i)==null)changed.get(i).remove("collapsed");else put(changed.get(i),"collapsed",previous.get(i));}return;}
        int scrollY=boardScroll.getScrollY();show();boardScroll.post(()->boardScroll.scrollTo(0,scrollY));
    }
    private void boardMenu(JSONObject b){new AlertDialog.Builder(this).setTitle(b.optString("name")).setItems(new String[]{"Rename board","Set Icon","Apply default layout","Use columns as default","Delete board"},(d,w)->{if(w==0)renameBoard(b);else if(w==1)boardContextIcon(b);else if(w==2)checkDefault(b,true);else if(w==3)useBoardAsDefault(b);else confirmDeleteBoard(b);}).show();}
    private void confirmDeleteBoard(JSONObject board){
        new AlertDialog.Builder(this).setTitle("Delete board?").setMessage("Delete “"+board.optString("name")+"”? This deletes the board, all nested boards, and their work items.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(dialog,which)->{
            JSONObject previous=data;String previousCurrent=current;
            try{data=BoardTemplate.copy(data);deleteTree(board.optString("id"));if(!current.isEmpty()&&board()==null)current=board.optString("parent");if(!save()){data=previous;current=previousCurrent;return;}show();}
            catch(JSONException e){data=previous;current=previousCurrent;error("Could not delete board",e.getMessage());}
        }).show();
    }
    private void deleteTree(String id){JSONArray bs=array(data,"boards");ArrayList<String> children=new ArrayList<>();for(int i=0;i<bs.length();i++)if(id.equals(at(bs,i).optString("parent")))children.add(at(bs,i).optString("id"));for(String child:children)deleteTree(child);remove(bs,id);}
    private void columnView(JSONObject b,JSONObject c){
        LinearLayout box=tile();box.setBackground(columnBackground(c,false));dropColumns.put(box,c);LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);box.addView(head);
        TextView toggle=text(c.optBoolean("collapsed")?"▸  ":"▾  ",20,INK,true);head.addView(toggle);
        TextView dot=text("●  ",20,parseColor(c.optString("color")),true);head.addView(dot);
        TextView title=text(c.optString("name")+"  "+array(c,"cards").length(),17,INK,true);head.addView(title,new LinearLayout.LayoutParams(0,-2,1));head.addView(action("⋮",()->columnMenu(b,c)));
        LinearLayout body=vertical();box.addView(body);body.setVisibility(c.optBoolean("collapsed")?View.GONE:View.VISIBLE);
        head.setContentDescription((c.optBoolean("collapsed")?"Expand ":"Collapse ")+c.optString("name"));
        head.setOnClickListener(v->{boolean was=c.optBoolean("collapsed"),next=!was;put(c,"collapsed",next);if(!save()){put(c,"collapsed",was);return;}body.setVisibility(next?View.GONE:View.VISIBLE);toggle.setText(next?"▸  ":"▾  ");head.setContentDescription((next?"Expand ":"Collapse ")+c.optString("name"));});
        JSONArray cards=array(c,"cards");for(int i=0;i<cards.length();i++){JSONObject card=at(cards,i);LinearLayout cardView=boardCard(b,c,card);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(7);body.addView(cardView,p);cardViews.put(card.optString("id"),cardView);}body.addView(action("+ Add work item",()->input("New work item","Title","",name->{JSONObject card=put(put(put(obj(),"id",id()),"title",name),"tasks",arr());put(card,"comments",arr());cards.put(card);save();show();})));
    }
    private LinearLayout boardCard(JSONObject board,JSONObject column,JSONObject card){
        LinearLayout view=vertical();view.setPadding(dp(12),dp(12),dp(12),dp(12));view.setBackground(shape(BG,0));
        TextView title=text(card.optString("title"),16,INK,true);view.addView(title);
        String description=card.optString("description","").split("\\n",-1)[0].trim();if(!description.isEmpty()){TextView snippet=text(description,14,MUTED,false);snippet.setMaxLines(2);snippet.setEllipsize(android.text.TextUtils.TruncateAt.END);snippet.setPadding(0,dp(5),0,0);view.addView(snippet);}
        String labels=card.optString("labels","").trim();if(!labels.isEmpty()){TextView tags=text("Labels  "+labels,13,ACCENT,true);tags.setPadding(0,dp(7),0,0);view.addView(tags);}
        StringBuilder detail=new StringBuilder();appendCardField(detail,"Due",card.optString("due"));appendCardField(detail,"Points",card.optString("points"));appendCardField(detail,"Priority",card.optString("priority"));appendCardField(detail,"Difficulty",card.optString("difficulty"));
        JSONObject metadata=card.optJSONObject("templateData");appendCardField(detail,"Preference",card.optString("userPreference",metadata==null?"":metadata.optString("userPreference","")));appendCardField(detail,"ADO",card.optString("ado"));appendCardField(detail,"SM",card.optString("sm"));
        if(detail.length()>0){TextView fields=text(detail.toString(),13,MUTED,false);fields.setPadding(0,dp(7),0,0);view.addView(fields);}
        JSONArray tasks=array(card,"tasks");if(tasks.length()>0){
            String key=board.optString("id")+"/"+card.optString("id");boolean open=expandedTasks.contains(key);
            TextView heading=text("",14,ACCENT,true);heading.setPadding(0,dp(8),0,dp(6));view.addView(heading);
            LinearLayout taskBody=vertical();taskBody.setVisibility(open?View.VISIBLE:View.GONE);view.addView(taskBody);
            Runnable updateHeading=()->{int done=0;for(int i=0;i<tasks.length();i++)if(at(tasks,i).optBoolean("done"))done++;heading.setText((taskBody.getVisibility()==View.VISIBLE?"▾  ":"▸  ")+"Tasks ("+done+"/"+tasks.length()+")");heading.setContentDescription((taskBody.getVisibility()==View.VISIBLE?"Collapse":"Expand")+" tasks for "+card.optString("title"));};
            updateHeading.run();heading.setOnClickListener(v->{boolean expand=taskBody.getVisibility()!=View.VISIBLE;taskBody.setVisibility(expand?View.VISIBLE:View.GONE);if(expand)expandedTasks.add(key);else expandedTasks.remove(key);updateHeading.run();});
            for(int pass=0;pass<2;pass++)for(int i=0;i<tasks.length();i++){JSONObject task=at(tasks,i);boolean done=task.optBoolean("done");if(done!=(pass==1))continue;CheckBox check=new CheckBox(this);check.setText(task.optString("title"));check.setTextColor(done?MUTED:INK);check.setTextSize(14);check.setChecked(done);if(done)check.setPaintFlags(check.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);taskBody.addView(check);check.setOnLongClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);taskMenu(task,()->mutateBoard(board,draft->WorkItemRelations.taskToCard(draft,card.optString("id"),task.optString("id"))),()->new AlertDialog.Builder(this).setTitle("Delete task?").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->mutateBoard(board,draft->{WorkItemRelations.Entry owner=WorkItemRelations.find(draft,card.optString("id"));if(owner==null)throw new JSONException("Work item not found.");remove(array(owner.card,"tasks"),task.optString("id"));})).show());return true;});check.setOnCheckedChangeListener((button,checked)->{boolean before=task.optBoolean("done");put(task,"done",checked);if(!save()){put(task,"done",before);button.setOnCheckedChangeListener(null);button.setChecked(before);show();}else{button.setTextColor(checked?MUTED:INK);button.setPaintFlags(checked?button.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG:button.getPaintFlags()&~android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);updateHeading.run();}});}
        }
        try{
            String parentId=card.optString("parentId","");if(!parentId.isEmpty()){WorkItemRelations.Entry parent=WorkItemRelations.find(board,parentId);if(parent!=null){TextView parentLink=text("↳ Parent: "+parent.card.optString("title"),14,ACCENT,true);parentLink.setPadding(0,dp(9),0,dp(4));parentLink.setOnClickListener(v->revealCard(board,parent.card.optString("id")));view.addView(parentLink);}else{TextView missing=text("Parent unavailable",13,MUTED,false);missing.setPadding(0,dp(8),0,0);view.addView(missing);}}
            ArrayList<WorkItemRelations.Entry> children=new ArrayList<>();for(WorkItemRelations.Entry entry:WorkItemRelations.entries(board))if(!card.optString("id").equals(entry.card.optString("id"))&&card.optString("id").equals(entry.card.optString("parentId")))children.add(entry);
            if(!children.isEmpty())addChildSection(view,board,card,children);
        }catch(JSONException e){TextView warning=text("Relationships unavailable",13,MUTED,false);view.addView(warning);}
        view.setOnClickListener(v->editCard(board,column,card));view.setOnLongClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);cardMenu(board,column,card);return true;});view.setOnTouchListener(new CardTouch(board,column,card));return view;
    }
    private void appendCardField(StringBuilder line,String label,String value){if(value==null||value.trim().isEmpty()||"null".equals(value))return;if(line.length()>0)line.append("  ·  ");line.append(label).append(": ").append(value.trim());}
    private void addChildSection(LinearLayout cardView,JSONObject board,JSONObject card,ArrayList<WorkItemRelations.Entry> children){
        String key=board.optString("id")+"/"+card.optString("id");boolean initiallyOpen=expandedChildren.contains(key);
        TextView toggle=text((initiallyOpen?"▾  ":"▸  ")+"Children ("+children.size()+")",14,ACCENT,true);toggle.setPadding(0,dp(10),0,dp(6));cardView.addView(toggle);
        LinearLayout content=vertical();content.setVisibility(initiallyOpen?View.VISIBLE:View.GONE);cardView.addView(content);
        JSONArray columns=array(board,"columns");for(WorkItemRelations.Entry child:children){
            TextView link=text(child.card.optString("title"),14,INK,true);link.setPadding(dp(4),dp(5),0,dp(3));link.setOnClickListener(v->revealCard(board,child.card.optString("id")));content.addView(link);
            int index=0;for(int i=0;i<columns.length();i++)if(at(columns,i)==child.column){index=i;break;}int progress=WorkItemRelations.columnProgress(index,columns.length());
            ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgress(progress);bar.setProgressTintList(ColorStateList.valueOf(parseColor(child.column.optString("color"))));bar.setContentDescription("Column "+(index+1)+" of "+columns.length()+": "+child.column.optString("name"));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(6));p.bottomMargin=dp(8);content.addView(bar,p);
        }
        toggle.setContentDescription((initiallyOpen?"Collapse":"Expand")+" children of "+card.optString("title"));toggle.setOnClickListener(v->{boolean open=content.getVisibility()!=View.VISIBLE;content.setVisibility(open?View.VISIBLE:View.GONE);if(open)expandedChildren.add(key);else expandedChildren.remove(key);toggle.setText((open?"▾  ":"▸  ")+"Children ("+children.size()+")");toggle.setContentDescription((open?"Collapse":"Expand")+" children of "+card.optString("title"));});
    }
    private void revealCard(JSONObject board,String id){
        try{WorkItemRelations.Entry entry=WorkItemRelations.find(board,id);if(entry==null){Toast.makeText(this,"Work item not found",Toast.LENGTH_SHORT).show();return;}if(entry.column.optBoolean("collapsed")){put(entry.column,"collapsed",false);if(!save()){put(entry.column,"collapsed",true);return;}}
            current=board.optString("id");tab="boards";show();View target=cardViews.get(id);if(target==null)return;ScrollView scroll=boardScroll;
            target.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener(){@Override public void onGlobalLayout(){target.getViewTreeObserver().removeOnGlobalLayoutListener(this);if(scroll!=boardScroll||!target.isAttachedToWindow())return;Rect bounds=new Rect();target.getDrawingRect(bounds);scroll.offsetDescendantRectToMyCoords(target,bounds);scroll.smoothScrollTo(0,Math.max(0,bounds.top-dp(12)));target.setBackground(shape(BG,ACCENT));target.announceForAccessibility("Work item "+entry.card.optString("title"));target.postDelayed(()->target.setBackground(shape(BG,0)),1200);}});target.requestLayout();
        }catch(JSONException e){error("Could not find work item",e.getMessage());}
    }
    private int parseColor(String s){try{return Color.parseColor(s);}catch(Exception e){return ACCENT;}}
    private void taskMenu(JSONObject task,Runnable convert,Runnable delete){
        new AlertDialog.Builder(this).setTitle(task.optString("title")).setItems(new String[]{"Convert to work item","Delete task"},(d,n)->{if(n==0)convert.run();else delete.run();}).show();
    }
    private void cardMenu(JSONObject b,JSONObject c,JSONObject card){
        new AlertDialog.Builder(this).setTitle(card.optString("title")).setItems(new String[]{"Edit", "Move to column", "Move to board", "Set or clear parent", "Convert task to work item", "Convert work item to task", "Delete"},(dialog,choice)->{
            if(choice==0)editCard(b,c,card);
            else if(choice==1)moveCard(b,c,card);
            else if(choice==2)chooseBoard(b,card);
            else if(choice==3)chooseParent(b,card);
            else if(choice==4)chooseTaskToConvert(b,card);
            else if(choice==5)convertToTask(b,card);
            else new AlertDialog.Builder(this).setTitle("Delete work item?").setMessage("This permanently deletes the item and removes its children's parent links.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->mutateBoard(b, draft->{WorkItemRelations.Entry entry=WorkItemRelations.find(draft,card.optString("id"));if(entry==null)throw new JSONException("Work item not found.");if(WorkItemRelations.hasArchivedChild(draft,card.optString("id")))throw new JSONException("This item has archived children; deleting it is not supported yet.");for(WorkItemRelations.Entry item:WorkItemRelations.entries(draft))if(card.optString("id").equals(item.card.optString("parentId")))item.card.remove("parentId");WorkItemRelations.remove(entry.column,entry.card);})).show();
        }).show();
    }
    interface BoardMutation{void apply(JSONObject board) throws JSONException;}
    private void mutateBoard(JSONObject original,BoardMutation action){JSONObject previous=data;try{JSONObject draft=new JSONObject(data.toString());JSONObject board=find(array(draft,"boards"),original.optString("id"));if(board==null)throw new JSONException("Board not found.");action.apply(board);data=draft;if(!save())data=previous;show();}catch(JSONException e){data=previous;error("Could not update work item",e.getMessage());}}
    private void chooseParent(JSONObject board,JSONObject card){try{ArrayList<WorkItemRelations.Entry> choices=new ArrayList<>();for(WorkItemRelations.Entry e:WorkItemRelations.entries(board))if(WorkItemRelations.canParent(board,card.optString("id"),e.card.optString("id")))choices.add(e);String[] names=new String[choices.size()+1];names[0]="No parent";for(int i=0;i<choices.size();i++)names[i+1]=choices.get(i).card.optString("title")+" · "+choices.get(i).column.optString("name");new AlertDialog.Builder(this).setTitle("Choose parent").setItems(names,(d,n)->mutateBoard(board,draft->WorkItemRelations.setParent(draft,card.optString("id"),n==0?"":choices.get(n-1).card.optString("id")))).show();}catch(JSONException e){error("Cannot choose parent",e.getMessage());}}
    private void chooseTaskToConvert(JSONObject board,JSONObject card){JSONArray tasks=array(card,"tasks");if(tasks.length()==0){error("No tasks","Add a checklist task first.");return;}String[] names=new String[tasks.length()];for(int i=0;i<names.length;i++)names[i]=at(tasks,i).optString("title");new AlertDialog.Builder(this).setTitle("Convert task to work item").setItems(names,(d,n)->{String id=at(tasks,n).optString("id");mutateBoard(board,draft->WorkItemRelations.taskToCard(draft,card.optString("id"),id));}).show();}
    private void convertToTask(JSONObject board,JSONObject card){new AlertDialog.Builder(this).setTitle("Convert to task?").setMessage("The item becomes a checklist task on its parent. Only its title and completion state are kept; description, labels, comments and other fields will be removed. The item must have a parent and no tasks or children.").setNegativeButton("Cancel",null).setPositiveButton("Convert",(d,w)->mutateBoard(board,draft->WorkItemRelations.cardToTask(draft,card.optString("id")))).show();}
    private void chooseBoard(JSONObject source,JSONObject card){JSONArray boards=array(data,"boards");ArrayList<JSONObject> targets=new ArrayList<>();for(int i=0;i<boards.length();i++)if(at(boards,i)!=source)targets.add(at(boards,i));if(targets.isEmpty()){error("No other boards","Create another board first.");return;}String[] names=new String[targets.size()];for(int i=0;i<names.length;i++)names[i]=path(targets.get(i));new AlertDialog.Builder(this).setTitle("Move to board").setItems(names,(d,n)->moveAcrossBoards(source,targets.get(n),card)).show();}
    private void moveAcrossBoards(JSONObject source,JSONObject destination,JSONObject card){
        if(!card.optString("parentId").isEmpty()){new AlertDialog.Builder(this).setTitle("Move parent family?").setMessage("Move the root parent and all descendants, or move this item and its descendants and detach it from its parent.").setNegativeButton("Cancel",null).setNeutralButton("This item",(d,w)->applyBoardMove(source,destination,card,false)).setPositiveButton("Root family",(d,w)->applyBoardMove(source,destination,card,true)).show();}
        else applyBoardMove(source,destination,card,false);
    }
    private void applyBoardMove(JSONObject source,JSONObject destination,JSONObject card,boolean root){JSONObject previous=data;try{JSONObject draft=new JSONObject(data.toString());JSONObject from=find(array(draft,"boards"),source.optString("id")),to=find(array(draft,"boards"),destination.optString("id"));if(from==null||to==null)throw new JSONException("Board no longer exists.");WorkItemRelations.moveFamily(from,to,card.optString("id"),root);data=draft;if(!save()){data=previous;return;}show();Toast.makeText(this,"Moved to "+path(find(array(data,"boards"),destination.optString("id"))),Toast.LENGTH_SHORT).show();}catch(JSONException e){data=previous;error("Could not move work item",e.getMessage());}}
    /** Touches scroll normally until a stationary long press picks up a card. */
    private final class CardTouch implements View.OnTouchListener {
        private static final long MENU_RELEASE_WINDOW_MS=500;
        private final JSONObject board,from,card;
        private final int slop=ViewConfiguration.get(MainActivity.this).getScaledTouchSlop();
        private View source,highlight; private TextView ghost;
        private boolean held,moved,cancelled,scrollScheduled; private float downX,downY,x,y; private long pickedUpAt;
        private final Runnable pickup=()->{
            if(cancelled||source==null)return;
            held=true;pickedUpAt=android.os.SystemClock.uptimeMillis();source.getParent().requestDisallowInterceptTouchEvent(true);
            source.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            source.setAlpha(.45f);
        };
        private final Runnable scrollTick=new Runnable(){@Override public void run(){
            scrollScheduled=false;if(!held||!moved||boardScroll==null)return;
            Rect visible=new Rect();if(!boardScroll.getGlobalVisibleRect(visible))return;
            int edge=dp(56), speed=dp(12), delta=y<visible.top+edge?-speed:y>visible.bottom-edge?speed:0;
            if(delta==0)return;int before=boardScroll.getScrollY();boardScroll.scrollBy(0,delta);updateTarget();
            if(boardScroll.getScrollY()!=before){scrollScheduled=true;boardScroll.postOnAnimation(this);}
        }};
        CardTouch(JSONObject board,JSONObject from,JSONObject card){this.board=board;this.from=from;this.card=card;}
        @Override public boolean onTouch(View view,android.view.MotionEvent event){
            switch(event.getActionMasked()){
                case android.view.MotionEvent.ACTION_DOWN:
                    source=view;held=false;moved=false;cancelled=false;downX=x=event.getRawX();downY=y=event.getRawY();
                    view.postDelayed(pickup,ViewConfiguration.getLongPressTimeout());return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    if(cancelled)return true;
                    x=event.getRawX();y=event.getRawY();
                    if(!moved && Math.hypot(x-downX,y-downY)>slop){moved=true;if(!held)view.removeCallbacks(pickup);else createGhost();}
                    if(held&&moved){positionGhost();updateTarget();if(!scrollScheduled){scrollScheduled=true;boardScroll.postOnAnimation(scrollTick);}}
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                    if(cancelled)return true;
                    x=event.getRawX();y=event.getRawY();view.removeCallbacks(pickup);
                    if(held&&moved){updateTarget();JSONObject destination=highlight==null?null:dropColumns.get(highlight),otherBoard=highlight==null?null:dropBoards.get(highlight);int scrollY=boardScroll.getScrollY();cleanup();if(otherBoard!=null){view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);moveAcrossBoards(board,otherBoard,card);}else if(destination!=null&&destination!=from){if(transferCard(from,destination,card)){view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);show();boardScroll.post(()->boardScroll.scrollTo(0,scrollY));}}else if(destination==null)Toast.makeText(MainActivity.this,"Drop on a column or board to move",Toast.LENGTH_SHORT).show();}
                    else if(held){boolean quickRelease=event.getEventTime()-pickedUpAt>=0 && event.getEventTime()-pickedUpAt<=MENU_RELEASE_WINDOW_MS;cleanup();if(quickRelease)cardMenu(board,from,card);}
                    else {cleanup();if(!moved)view.performClick();}
                    return true;
                case android.view.MotionEvent.ACTION_POINTER_DOWN:
                case android.view.MotionEvent.ACTION_CANCEL:
                    cancelled=true;view.removeCallbacks(pickup);cleanup();return true;
                default:return true;
            }
        }
        private void createGhost(){
            if(dragBoardTargets!=null&&!dropBoards.isEmpty())dragBoardTargets.setVisibility(View.VISIBLE);
            ghost=text(card.optString("title"),16,INK,true);pad(ghost,14);ghost.setBackground(shape(SURFACE,ACCENT));ghost.setElevation(dp(12));
            int width=Math.min(source.getWidth(),root.getWidth()-dp(32));
            ghost.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1,width),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(dp(100),View.MeasureSpec.AT_MOST));
            ghost.layout(0,0,ghost.getMeasuredWidth(),ghost.getMeasuredHeight());root.getOverlay().add(ghost);positionGhost();
        }
        private void positionGhost(){if(ghost==null)return;int[] loc=new int[2];root.getLocationOnScreen(loc);ghost.setTranslationX(x-loc[0]-ghost.getWidth()/2f);ghost.setTranslationY(y-loc[1]-ghost.getHeight()/2f);}
        private void updateTarget(){
            View target=null;Rect r=new Rect();for(View column:dropColumns.keySet())if(column.getGlobalVisibleRect(r)&&r.contains((int)x,(int)y)){target=column;break;}
            if(target==null)for(View board:dropBoards.keySet())if(board.getGlobalVisibleRect(r)&&r.contains((int)x,(int)y)){target=board;break;}
            if(target==highlight)return;if(highlight!=null)highlight.setBackground(dropColumns.containsKey(highlight)?columnBackground(dropColumns.get(highlight),false):shape(SURFACE,BORDER));highlight=target;
            if(highlight!=null){highlight.setBackground(dropColumns.containsKey(highlight)?columnBackground(dropColumns.get(highlight),true):shape(SURFACE,ACCENT));source.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);}
        }
        private void cleanup(){
            if(source!=null){source.removeCallbacks(pickup);source.setAlpha(1f);if(source.getParent()!=null)source.getParent().requestDisallowInterceptTouchEvent(false);}
            if(boardScroll!=null)boardScroll.removeCallbacks(scrollTick);scrollScheduled=false;
            if(highlight!=null)highlight.setBackground(dropColumns.containsKey(highlight)?columnBackground(dropColumns.get(highlight),false):shape(SURFACE,BORDER));highlight=null;
            if(dragBoardTargets!=null)dragBoardTargets.setVisibility(View.GONE);
            if(ghost!=null){root.getOverlay().remove(ghost);ghost=null;}held=false;
        }
    }
    private String summary(JSONObject c){JSONArray tasks=array(c,"tasks");int done=0;for(int i=0;i<tasks.length();i++)if(at(tasks,i).optBoolean("done"))done++;String due=c.optString("due");return (tasks.length()>0?"\n✓ "+done+"/"+tasks.length():"")+(due.isEmpty()?"":"   ◷ "+due);}
    private void columnMenu(JSONObject b,JSONObject c){new AlertDialog.Builder(this).setTitle(c.optString("name")).setItems(new String[]{"Rename","Change color","Delete column"},(d,w)->{if(w==0)input("Rename column","Name",c.optString("name"),s->{put(c,"name",s);save();show();});else if(w==1)new AlertDialog.Builder(this).setTitle("Column color").setItems(new String[]{"Indigo","Teal","Amber","Rose","Blue","Purple"},(x,n)->{put(c,"color",palette[n]);save();show();}).show();else if(array(b,"columns").length()==1)Toast.makeText(this,"Keep at least one column",Toast.LENGTH_SHORT).show();else new AlertDialog.Builder(this).setTitle("Delete column?").setMessage("Its work items will be deleted, and children in other columns will be detached.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(x,n)->mutateBoard(b,draft->{JSONObject removed=find(array(draft,"columns"),c.optString("id"));if(removed==null)throw new JSONException("Column not found.");Set<String> ids=new HashSet<>();JSONArray cards=array(removed,"cards");for(int i=0;i<cards.length();i++){String id=at(cards,i).optString("id");if(WorkItemRelations.hasArchivedChild(draft,id))throw new JSONException("A work item has archived children; deleting this column is not supported yet.");ids.add(id);}for(WorkItemRelations.Entry item:WorkItemRelations.entries(draft))if(ids.contains(item.card.optString("parentId")))item.card.remove("parentId");remove(array(draft,"columns"),c.optString("id"));})).show();}).show();}
    private void all(){EditText search=new EditText(this);search.setSingleLine();search.setHint("Search work items");content.addView(search);LinearLayout results=vertical();content.addView(results);Runnable render=()->{results.removeAllViews();String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);int count=0;JSONArray bs=array(data,"boards");for(int i=0;i<bs.length();i++){JSONObject b=at(bs,i);JSONArray cs=array(b,"columns");for(int j=0;j<cs.length();j++){JSONObject c=at(cs,j);JSONArray cards=array(c,"cards");for(int k=0;k<cards.length();k++){JSONObject card=at(cards,k);if(!card.toString().toLowerCase(Locale.ROOT).contains(q))continue;count++;TextView t=text(card.optString("title")+"\n"+path(b)+"  ·  "+c.optString("name")+summary(card),16,INK,false);pad(t,16);t.setBackground(shape(SURFACE,BORDER));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);results.addView(t,p);t.setOnClickListener(v->editCard(b,c,card));}}}if(count==0){TextView t=text("No matching work items",15,MUTED,false);pad(t,16);results.addView(t);}};search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){render.run();}public void afterTextChanged(android.text.Editable e){}});render.run();}
    private void backupView(){
        String tree=getPreferences(MODE_PRIVATE).getString("backupTree","");
        String last=getPreferences(MODE_PRIVATE).getString("backupStatus","");
        section("Automatic backups");
        empty(tree.isEmpty()?"No backup folder selected. Your boards are currently only in app storage.":"Backup folder connected. "+(last.isEmpty()?"Waiting for a backup.":last));
        empty("Each save writes a separate snapshot to the folder you choose. Snapshots contain your boards, work items, defaults, column visibility, and theme.");
        button(tree.isEmpty()?"Choose backup folder":"Change backup folder",()->new AlertDialog.Builder(this).setTitle("Choose a backup folder").setMessage("The app will immediately back up its current data and continue after each save. After reinstalling, restore an existing snapshot before connecting a folder if you want the old boards back.").setNegativeButton("Cancel",null).setPositiveButton("Choose folder",(d,w)->{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(intent,PICK_BACKUP_FOLDER);}).show());
        if(!tree.isEmpty())button("Back up now",()->{queueBackup();show();});
        section("Restore after reinstall");empty("Choose a previously saved code-kanban JSON snapshot. Restoring replaces the current app data. Afterwards, reconnect a backup folder for future saves.");
        button("Choose backup to restore",()->{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json");intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(intent,PICK_RESTORE_FILE);});
    }
    private void queueBackup(){
        String tree=getPreferences(MODE_PRIVATE).getString("backupTree","");if(tree.isEmpty()||data==null)return;
        long sequence=BACKUP_SEQUENCE.incrementAndGet();getPreferences(MODE_PRIVATE).edit().putString("backupStatus","Backup in progress…").apply();
        final String snapshot;try{snapshot=BackupStore.snapshot(data,dark,themeMode());}catch(JSONException e){backupResult(tree,sequence,"Backup failed: "+e.getMessage(),false);return;}
        BACKUPS.execute(()->{try{BackupStore.write(getContentResolver(),Uri.parse(tree),snapshot);backupResult(tree,sequence,"Last backup: "+java.text.DateFormat.getDateTimeInstance().format(new Date()),true);}catch(Exception e){backupResult(tree,sequence,"Backup failed: "+e.getMessage(),false);}});
    }
    private void backupResult(String tree,long sequence,String status,boolean success){
        runOnUiThread(()->{if(sequence!=BACKUP_SEQUENCE.get()||!tree.equals(getPreferences(MODE_PRIVATE).getString("backupTree","")))return;getPreferences(MODE_PRIVATE).edit().putString("backupStatus",status).apply();if(!isFinishing()&&!isDestroyed()){if(!success)Toast.makeText(this,status,Toast.LENGTH_LONG).show();if(tab.equals("settings")&&backupExpanded)show();}});
    }
    @Override protected void onActivityResult(int request,int result,Intent resultData){
        super.onActivityResult(request,result,resultData);
        if(request==PICK_ALARM_SOUND){if(result==RESULT_OK&&resultData!=null&&soundChoice!=null){Uri picked=resultData.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI);if(picked!=null){put(soundChoice,"soundUri",picked.toString());if(soundChoiceLabel!=null)soundChoiceLabel.setText(soundTitle(picked));}}soundChoice=null;soundChoiceLabel=null;return;}
        if(result!=RESULT_OK||resultData==null||resultData.getData()==null)return;Uri uri=resultData.getData();
        if(request==PICK_BACKUP_FOLDER){try{int flags=resultData.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);if((flags&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)==0)throw new SecurityException("The folder did not grant write access.");getContentResolver().takePersistableUriPermission(uri,flags);getPreferences(MODE_PRIVATE).edit().putString("backupTree",uri.toString()).putString("backupStatus","Backup in progress…").apply();show();queueBackup();}catch(Exception e){error("Could not connect folder",e.getMessage());}}
        else if(request==PICK_RESTORE_FILE){BACKUPS.execute(()->{try{JSONObject backup=BackupStore.validate(BackupStore.read(getContentResolver(),uri));runOnUiThread(()->{if(isFinishing()||isDestroyed())return;new AlertDialog.Builder(this).setTitle("Restore this backup?").setMessage("This replaces all current boards, work items, defaults, and theme with the selected snapshot. Make a backup of the current data first if you need it.").setNegativeButton("Cancel",null).setPositiveButton("Restore",(d,w)->restoreBackup(backup)).show();});}catch(Exception e){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())error("Could not restore backup",e.getMessage());});}});}
    }
    private void restoreBackup(JSONObject backup){try{JSONObject previous=data;String previousTheme=themeMode();data=BoardTemplate.copy(backup.getJSONObject("data"));ReminderSubscriptions.prepare(this,data,true);getPreferences(MODE_PRIVATE).edit().putString("themeMode",backup.optString("themeMode",backup.getBoolean("darkMode")?"dark":"light")).apply();if(save()){current="";tab="boards";recreate();}else{data=previous;getPreferences(MODE_PRIVATE).edit().putString("themeMode",previousTheme).apply();}}catch(JSONException e){error("Could not restore backup",e.getMessage());}}
    private void settings(){
        section("Appearance");
        Spinner theme=new Spinner(this);theme.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Light Mode","Dark Mode","Follow Device"}));
        theme.setSelection("light".equals(themeMode())?0:"dark".equals(themeMode())?1:2);content.addView(theme);
        theme.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> parent){}public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){String selected=new String[]{"light","dark","device"}[position],previous=themeMode();if(!selected.equals(previous)){getPreferences(MODE_PRIVATE).edit().putString("themeMode",selected).apply();if(save()){Intent intent=getIntent();intent.putExtra("settingsOpen",true);recreate();}else getPreferences(MODE_PRIVATE).edit().putString("themeMode",previous).apply();}}});

        TextView updatesToggle=action((updatesExpanded?"▾":"▸")+"  Updates",()->{updatesExpanded=!updatesExpanded;show();});updatesToggle.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);updatesToggle.setContentDescription((updatesExpanded?"Collapse":"Expand")+" Updates");content.addView(updatesToggle);
        if(updatesExpanded){
        button("Check for updates",()->new UpdateManager(this).check(true));
        TextView channelLabel=text("Release channel",16,INK,true);content.addView(channelLabel);
        Spinner channel=new Spinner(this);channel.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Stable","Beta"}));
        channel.setSelection("beta".equals(getSharedPreferences("app_updates",MODE_PRIVATE).getString("channel","stable"))?1:0);content.addView(channel);
        channel.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> parent){}public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){String selected=position==1?"beta":"stable";String previous=getSharedPreferences("app_updates",MODE_PRIVATE).getString("channel","stable");if(!selected.equals(previous)){getSharedPreferences("app_updates",MODE_PRIVATE).edit().putString("channel",selected).apply();show();new UpdateManager(MainActivity.this).check(true);}}});

        }
        TextView backupToggle=action((backupExpanded?"▾":"▸")+"  Backup",()->{backupExpanded=!backupExpanded;show();});backupToggle.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);backupToggle.setContentDescription((backupExpanded?"Collapse":"Expand")+" Backup");content.addView(backupToggle);
        if(backupExpanded)backupView();
        try{JSONObject t=template();JSONArray lists=t.getJSONArray("lists");
            TextView columnsToggle=action((defaultColumnsExpanded?"▾":"▸")+"  Default Columns",()->{defaultColumnsExpanded=!defaultColumnsExpanded;show();});columnsToggle.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);columnsToggle.setContentDescription((defaultColumnsExpanded?"Collapse":"Expand")+" Default Columns");content.addView(columnsToggle);
            if(defaultColumnsExpanded){
            empty("New boards copy this template. Existing boards are compared when opened. Changes here do not alter existing boards.");
            for(int i=0;i<lists.length();i++){final int index=i;JSONObject col=lists.getJSONObject(i);LinearLayout row=tile();TextView name=text("●  "+col.optString("title"),17,INK,true);row.addView(name);row.addView(text("Color: "+(col.optString("color").isEmpty()?"none":col.optString("color"))+"  ·  "+col.getJSONArray("cards").length()+" starter items",13,MUTED,false));row.setOnClickListener(v->editDefaultColumn(t,index));}
            button("+ Add default column",()->input("New default column","Column name","",name->{try{lists.put(BoardTemplate.empty(name,palette[lists.length()%palette.length]));saveTemplate(t);show();}catch(JSONException e){error("Invalid column",e.getMessage());}}));
            }
            section("Full template");empty("Edit starter items, labels, archive and other extension template fields as JSON. Supported work-item fields appear in the editor; other fields are retained for future features.");button("Edit template JSON",()->editTemplateJson(t));
        }catch(JSONException e){error("Invalid default template",e.getMessage());}}
    private void saveTemplate(JSONObject template){try{BoardTemplate.validate(template);JSONObject previous=data.optJSONObject("defaultTemplate");put(data,"defaultTemplate",template);if(!save())put(data,"defaultTemplate",previous);}catch(JSONException e){error("Invalid default template",e.getMessage());}}
    private void editDefaultColumn(JSONObject template,int index){try{JSONObject draft=BoardTemplate.copy(template);JSONArray lists=draft.getJSONArray("lists");JSONObject c=lists.getJSONObject(index);String[] options={"Rename","Set color (#RRGGBB)","Move up","Move down","Remove"};new AlertDialog.Builder(this).setTitle(c.optString("title")).setItems(options,(d,w)->{try{if(w==0)input("Rename column","Name",c.optString("title"),name->{put(c,"title",name);saveTemplate(draft);show();});else if(w==1)input("Column color","#RRGGBB",c.optString("color","#6366f1"),color->{if(!color.matches("#[0-9a-fA-F]{6}")){error("Invalid color","Use a color like #6366f1.");return;}put(c,"color",color);saveTemplate(draft);show();});else if(w==4){if(lists.length()==1){error("Cannot remove column","Keep at least one default column.");return;}new AlertDialog.Builder(this).setTitle("Remove default column?").setMessage("Starter items in this template column will also be removed. Existing boards will not change.").setNegativeButton("Cancel",null).setPositiveButton("Remove",(x,y)->{lists.remove(index);saveTemplate(draft);show();}).show();}else{int to=w==2?index-1:index+1;if(to>=0&&to<lists.length()){JSONObject other=lists.getJSONObject(to);lists.put(to,c);lists.put(index,other);saveTemplate(draft);show();}}}catch(JSONException e){error("Invalid template",e.getMessage());}}).show();}catch(JSONException e){error("Invalid template",e.getMessage());}}
    private void editTemplateJson(JSONObject t){EditText e=new EditText(this);e.setGravity(Gravity.TOP);e.setTextSize(13);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);try{e.setText(t.toString(2));}catch(JSONException ignored){}ScrollView scroll=new ScrollView(this);scroll.addView(e);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Default template JSON").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{try{JSONObject draft=new JSONObject(e.getText().toString());BoardTemplate.validate(draft);JSONObject old=data.optJSONObject("defaultTemplate");put(data,"defaultTemplate",draft);if(save()){dialog.dismiss();show();}else put(data,"defaultTemplate",old);}catch(JSONException ex){e.setError(ex.getMessage());Toast.makeText(this,ex.getMessage(),Toast.LENGTH_LONG).show();}}));dialog.show();}
    private void useBoardAsDefault(JSONObject b){new AlertDialog.Builder(this).setTitle("Use board columns as default?").setMessage("This replaces the full template with this board's column names and colors. Starter items and template settings will be removed.").setNegativeButton("Cancel",null).setPositiveButton("Use columns",(d,w)->{try{JSONArray lists=new JSONArray(),cols=b.getJSONArray("columns");for(int i=0;i<cols.length();i++){JSONObject c=cols.getJSONObject(i);lists.put(BoardTemplate.empty(c.getString("name"),c.optString("color")));}JSONObject t=new JSONObject().put("lists",lists).put("archive",new JSONObject().put("lists",new JSONArray()).put("cards",new JSONArray())).put("settings",new JSONObject().put("labels",new JSONArray()));saveTemplate(t);Toast.makeText(this,"Default updated",Toast.LENGTH_SHORT).show();}catch(JSONException e){error("Could not set default",e.getMessage());}}).show();}
    interface TextResult{void done(String s);}
    private void input(String title,String hint,String value,TextResult result){EditText e=new EditText(this);e.setSingleLine();e.setHint(hint);e.setText(value);AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(e).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{String s=e.getText().toString().trim();if(s.isEmpty()){e.setError("Required");return;}dialog.dismiss();result.done(s);}));dialog.show();}
    private EditText field(LinearLayout l,String label,String value,boolean multi){TextView t=text(label,13,MUTED,true);t.setPadding(0,dp(12),0,0);l.addView(t);EditText e=new EditText(this);e.setText(value);if(multi){e.setMinLines(2);e.setGravity(Gravity.TOP);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);}else e.setSingleLine();l.addView(e);return e;}
    /** Returns the body of an independently expandable work-item section. */
    private LinearLayout editorSection(LinearLayout container,String name,boolean initiallyOpen){
        LinearLayout section=vertical();section.setBackground(shape(SURFACE,BORDER));
        LinearLayout.LayoutParams margin=new LinearLayout.LayoutParams(-1,-2);margin.topMargin=dp(10);container.addView(section,margin);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(14),dp(14),dp(14),dp(14));section.addView(header);
        TextView label=text(name,17,INK,true);header.addView(label,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=text(initiallyOpen?"▾":"▸",20,MUTED,true);header.addView(arrow);
        LinearLayout body=vertical();body.setPadding(dp(14),0,dp(14),dp(14));body.setVisibility(initiallyOpen?View.VISIBLE:View.GONE);section.addView(body);
        header.setContentDescription((initiallyOpen?"Collapse ":"Expand ")+name);header.setOnClickListener(v->{boolean open=body.getVisibility()!=View.VISIBLE;body.setVisibility(open?View.VISIBLE:View.GONE);arrow.setText(open?"▾":"▸");header.setContentDescription((open?"Collapse ":"Expand ")+name);});
        return body;
    }
    private void editorNote(LinearLayout body,String title,String description){
        TextView heading=text(title,14,INK,true);heading.setPadding(0,dp(16),0,dp(4));body.addView(heading);
        TextView note=text(description,13,MUTED,false);body.addView(note);
    }
    interface DateTimeResult{void selected(long millis);}
    private void pickDateTime(long initial,DateTimeResult callback){
        Calendar selected=Calendar.getInstance();selected.setTimeInMillis(initial);
        new DatePickerDialog(this,(picker,year,month,day)->{
            selected.set(Calendar.YEAR,year);selected.set(Calendar.MONTH,month);selected.set(Calendar.DAY_OF_MONTH,day);
            new TimePickerDialog(this,(clock,hour,minute)->{selected.set(Calendar.HOUR_OF_DAY,hour);selected.set(Calendar.MINUTE,minute);selected.set(Calendar.SECOND,0);selected.set(Calendar.MILLISECOND,0);callback.selected(selected.getTimeInMillis());},selected.get(Calendar.HOUR_OF_DAY),selected.get(Calendar.MINUTE),android.text.format.DateFormat.is24HourFormat(this)).show();
        },selected.get(Calendar.YEAR),selected.get(Calendar.MONTH),selected.get(Calendar.DAY_OF_MONTH)).show();
    }
    private String localDateTime(long time){return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault()).format(new Date(time));}
    private void notificationAccess(){
        if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},2001);return;}
        try{Intent intent=new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName());startActivity(intent);}catch(Exception e){error("Notification settings unavailable",e.getMessage());}
    }
    private void exactAlarmAccess(){
        if(android.os.Build.VERSION.SDK_INT<31)return;
        try{startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));}catch(Exception e){error("Alarm settings unavailable",e.getMessage());}
    }
    private void fullScreenAccess(){if(android.os.Build.VERSION.SDK_INT<34)return;try{startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:"+getPackageName())));}catch(Exception e){error("Full-screen alarm settings unavailable",e.getMessage());}}
    private boolean fullScreenReady(){
        NotificationManager notifications=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel alarmChannel=notifications==null?null:notifications.getNotificationChannel(AlarmService.CHANNEL);
        if(!ReminderScheduler.notificationsAllowed(this)||(notifications!=null&&!notifications.areNotificationsEnabled())||(alarmChannel!=null&&alarmChannel.getImportance()==NotificationManager.IMPORTANCE_NONE)){new AlertDialog.Builder(this).setTitle("Allow alarm notifications").setMessage("Notifications must be enabled for full-screen alarms. Enable them, then return and tap Done again.").setNegativeButton("Cancel",null).setPositiveButton("Settings",(d,w)->notificationAccess()).show();return false;}
        AlarmManager alarms=(AlarmManager)getSystemService(ALARM_SERVICE);
        if(android.os.Build.VERSION.SDK_INT>=31&&alarms!=null&&!alarms.canScheduleExactAlarms()){new AlertDialog.Builder(this).setTitle("Allow exact alarms").setMessage("Exact alarm access is required for full-screen alarms. Enable it, then return and tap Done again.").setNegativeButton("Cancel",null).setPositiveButton("Settings",(d,w)->exactAlarmAccess()).show();return false;}
        if(android.os.Build.VERSION.SDK_INT>=34&&notifications!=null&&!notifications.canUseFullScreenIntent()){new AlertDialog.Builder(this).setTitle("Allow full-screen alarms").setMessage("Allow Code Kanban to show full-screen alarms, then return and tap Done again.").setNegativeButton("Cancel",null).setPositiveButton("Settings",(d,w)->fullScreenAccess()).show();return false;}
        return true;
    }
    private String soundTitle(Uri uri){try{android.media.Ringtone ringtone=android.media.RingtoneManager.getRingtone(this,uri);return ringtone==null?"Default alarm sound":ringtone.getTitle(this);}catch(Exception e){return "Selected alarm sound";}}
    private void remindersView(){
        empty("Green: alerts enabled on this device. Red: alerts disabled on this device. Subscribing here does not change another device's alerts. Past reminders may alert immediately when enabled.");
        EditText search=new EditText(this);search.setSingleLine();search.setHint("Search reminders and work items");content.addView(search);
        LinearLayout results=vertical();content.addView(results);
        Runnable render=()->{results.removeAllViews();int count=0;String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);
            try{JSONArray boards=array(data,"boards");for(int b=0;b<boards.length();b++){JSONObject board=boards.getJSONObject(b);for(WorkItemRelations.Entry entry:WorkItemRelations.entries(board)){JSONArray reminders=entry.card.optJSONArray("reminders");if(reminders==null)continue;
                for(int r=0;r<reminders.length();r++){JSONObject reminder=reminders.optJSONObject(r);if(reminder==null)continue;String title=entry.card.optString("title"),location=path(board),date=localDateTime(ReminderScheduler.time(reminder));if(!(title+" "+location+" "+date).toLowerCase(Locale.ROOT).contains(query))continue;count++;
                    boolean subscribed=ReminderSubscriptions.subscribed(this,entry.card.optString("id"),reminder);int color=Color.parseColor(subscribed?"#22C55E":"#EF4444");
                    LinearLayout row=new LinearLayout(this);row.setBackground(roundedAccentBackground(color,false));row.setOutlineProvider(ViewOutlineProvider.BACKGROUND);row.setClipToOutline(true);LinearLayout.LayoutParams spacing=new LinearLayout.LayoutParams(-1,-2);spacing.bottomMargin=dp(10);results.addView(row,spacing);
                    LinearLayout body=vertical();pad(body,14);body.setPadding(dp(18),dp(14),dp(14),dp(14));row.addView(body,new LinearLayout.LayoutParams(0,-2,1));TextView name=text(title,17,INK,true);body.addView(name);row.setOnClickListener(v->editCard(board,entry.column,entry.card));
                    body.addView(text(location+" · "+entry.column.optString("name"),13,MUTED,false));body.addView(text(date+(reminder.optBoolean("fullScreen")?" · Full-screen alarm":" · Notification"),14,INK,false));
                    body.addView(text(subscribed?"Subscribed on this device":"Not subscribed on this device",14,color,true));
                    body.addView(action(subscribed?"Unsubscribe":"Subscribe on this device",()->{
                        Runnable change=()->{try{if(!subscribed){if(reminder.optBoolean("fullScreen")&&!fullScreenReady())return;if(!ReminderScheduler.notificationsAllowed(this)){notificationAccess();return;}}ReminderSubscriptions.set(this,entry.card.optString("id"),reminder,!subscribed);show();}catch(Exception e){error("Could not change subscription",e.getMessage());}};
                        if(!subscribed&&ReminderScheduler.time(reminder)<=System.currentTimeMillis())new AlertDialog.Builder(this).setTitle("Enable past reminder?").setMessage("This reminder is overdue and may alert immediately on this device.").setNegativeButton("Cancel",null).setPositiveButton("Subscribe",(d,w)->change.run()).show();else change.run();
                    }));
                }}}}catch(JSONException e){error("Could not list reminders",e.getMessage());}
            if(count==0)results.addView(text("No matching reminders",15,MUTED,false));
        };
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){render.run();}public void afterTextChanged(android.text.Editable s){}});render.run();
    }
    private void configureReminder(JSONObject source,java.util.function.Consumer<JSONObject> done){
        final JSONObject reminder;try{reminder=new JSONObject(source.toString());if(reminder.optString("originDeviceId").isEmpty())reminder.put("originDeviceId",ReminderSubscriptions.device(this));}catch(JSONException e){error("Could not edit reminder",e.getMessage());return;}
        LinearLayout form=vertical();pad(form,20);TextView when=text(localDateTime(ReminderScheduler.time(reminder)),16,INK,true);form.addView(when);
        form.addView(action("Change date and time",()->pickDateTime(ReminderScheduler.time(reminder),at->{if(at<=System.currentTimeMillis()){Toast.makeText(this,"Choose a future time",Toast.LENGTH_SHORT).show();return;}if(at!=ReminderScheduler.time(reminder))reminder.remove("notifiedAt");put(reminder,"at",at);when.setText(localDateTime(at));})));
        CheckBox full=new CheckBox(this);full.setText("Full-screen alarm");full.setChecked(reminder.optBoolean("fullScreen"));form.addView(full);form.addView(text("A full-screen alarm rings until dismissed or snoozed. Its sound uses the phone's alarm volume.",13,MUTED,false));
        Uri existing=reminder.optString("soundUri").isEmpty()?android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI:Uri.parse(reminder.optString("soundUri"));TextView sound=text(soundTitle(existing),14,INK,false);sound.setPadding(0,dp(12),0,0);form.addView(sound);
        form.addView(action("Choose alarm sound",()->{soundChoice=reminder;soundChoiceLabel=sound;Intent picker=new Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER);picker.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE,android.media.RingtoneManager.TYPE_ALARM);picker.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,reminder.optString("soundUri").isEmpty()?android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI:Uri.parse(reminder.optString("soundUri")));picker.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,true);picker.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,false);startActivityForResult(picker,PICK_ALARM_SOUND);}));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Reminder settings").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Done",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{if(full.isChecked()&&!fullScreenReady())return;put(reminder,"fullScreen",full.isChecked());dialog.dismiss();done.accept(reminder);}));dialog.show();
    }
    private void renderReminders(LinearLayout list,JSONArray reminders,String cardId){
        list.removeAllViews();if(reminders.length()==0){TextView empty=text("No reminders",14,MUTED,false);pad(empty,8);list.addView(empty);return;}
        for(int i=0;i<reminders.length();i++){final int index=i;JSONObject reminder=at(reminders,i);if(reminder==null)continue;LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);long at=ReminderScheduler.time(reminder);String label=(at>0?java.text.DateFormat.getDateTimeInstance().format(new Date(at)):"Invalid reminder time")+(reminder.optBoolean("fullScreen")?" · Alarm":" · Notification");String occurrence=ReminderScheduler.key(cardId,reminder.optString("id"),at);android.content.SharedPreferences state=getSharedPreferences(ReminderScheduler.PREFS,MODE_PRIVATE);long snoozed=state.getLong("snooze/"+occurrence,0);if(state.getBoolean("active/"+occurrence,false))label+=" · Ringing";else if(snoozed>0)label+=" · Snoozed until "+java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new Date(snoozed));else if(state.getBoolean("delivered/"+occurrence,false))label+=reminder.optBoolean("fullScreen")?" · Dismissed":" · Notified";TextView when=text(label,14,INK,false);row.addView(when,new LinearLayout.LayoutParams(0,-2,1));row.addView(action("Edit",()->configureReminder(reminder,updated->{try{reminders.put(index,updated);renderReminders(list,reminders,cardId);}catch(JSONException e){error("Could not edit reminder",e.getMessage());}})));row.addView(action("Remove",()->{remove(reminders,reminder.optString("id"));renderReminders(list,reminders,cardId);}));list.addView(row);}
    }
    private void restoreObject(JSONObject target,JSONObject snapshot){
        ArrayList<String> keys=new ArrayList<>();Iterator<String> existing=target.keys();while(existing.hasNext())keys.add(existing.next());
        for(String key:keys)target.remove(key);Iterator<String> saved=snapshot.keys();while(saved.hasNext()){String key=saved.next();put(target,key,snapshot.opt(key));}
    }
    private void editCard(JSONObject b,JSONObject c,JSONObject card){
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);LinearLayout form=vertical();form.setPadding(dp(16),dp(8),dp(16),dp(20));form.setBackgroundColor(BG);sc.addView(form);
        TextView location=text(b.optString("name")+"  ·  "+c.optString("name"),13,MUTED,false);location.setPadding(0,0,0,dp(6));form.addView(location);
        EditText title=field(form,"Title",card.optString("title"),false);
        EditText desc=field(form,"Description",card.optString("description"),true);
        EditText labels=field(form,"Labels (comma separated)",card.optString("labels"),false);

        LinearLayout time=editorSection(form,"Time",false);
        editorNote(time,"Recurring", "Not available yet on Android.");
        TextView reminderTitle=text("Reminders",14,INK,true);reminderTitle.setPadding(0,dp(16),0,dp(6));time.addView(reminderTitle);
        JSONArray reminders;try{JSONArray existing=card.optJSONArray("reminders");reminders=existing==null?new JSONArray():new JSONArray(existing.toString());}catch(JSONException e){error("Could not open reminders",e.getMessage());return;}
        LinearLayout reminderList=vertical();time.addView(reminderList);renderReminders(reminderList,reminders,card.optString("id"));
        time.addView(action("+ Add reminder",()->pickDateTime(System.currentTimeMillis()+3600000,at->{if(at<=System.currentTimeMillis()){Toast.makeText(this,"Choose a future time",Toast.LENGTH_SHORT).show();return;}JSONObject reminder=put(put(put(obj(),"id",id()),"kind","scheduled"),"at",at);configureReminder(reminder,configured->{reminders.put(configured);renderReminders(reminderList,reminders,card.optString("id"));Toast.makeText(this,"Save the work item to schedule this reminder",Toast.LENGTH_LONG).show();if(!ReminderScheduler.notificationsAllowed(this))notificationAccess();});})));
        NotificationManager notifications=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel reminderChannel=notifications==null?null:notifications.getNotificationChannel(ReminderScheduler.CHANNEL);
        if(!ReminderScheduler.notificationsAllowed(this)||(notifications!=null&&!notifications.areNotificationsEnabled())||(reminderChannel!=null&&reminderChannel.getImportance()==NotificationManager.IMPORTANCE_NONE))time.addView(action("Enable notifications",this::notificationAccess));
        AlarmManager alarms=(AlarmManager)getSystemService(ALARM_SERVICE);if(android.os.Build.VERSION.SDK_INT>=31&&alarms!=null&&!alarms.canScheduleExactAlarms())time.addView(action("Allow exact reminder timing",this::exactAlarmAccess));
        TextView reminderInfo=text("Notifications require permission. Without exact alarm access, reminders may arrive late.",13,MUTED,false);time.addView(reminderInfo);
        EditText due=field(time,"Due date and time",card.optString("due"),false);
        time.addView(action("Choose due date and time",()->pickDateTime(System.currentTimeMillis(),at->due.setText(localDateTime(at)))));
        time.addView(action("Clear due date",()->due.setText("")));
        LinearLayout history=editorSection(time,"Work item history",false);
        editorNote(history,"History", "Work item movement history is not available yet.");

        LinearLayout fields=editorSection(form,"Fields",false);
        JSONObject original=card.optJSONObject("templateData");String preference=card.optString("userPreference",original==null?"":original.optString("userPreference",""));
        editorNote(fields,"User preference",preference.isEmpty()?"Not ranked · Ranking is not available yet.":preference+" · Ranking is not available yet.");
        EditText points=field(fields,"Story points",card.optString("points"),false);
        EditText priority=field(fields,"Priority",card.optString("priority"),false);
        EditText difficulty=field(fields,"Difficulty estimation",card.optString("difficulty"),false);

        LinearLayout relational=editorSection(form,"Relational",false);
        EditText ado=field(relational,"ADO work item",card.optString("ado"),false);
        EditText sm=field(relational,"SM item",card.optString("sm"),false);
        try{
            WorkItemRelations.Entry parent=WorkItemRelations.find(b,card.optString("parentId"));
            editorNote(relational,"Parent",parent==null?"No parent":""+parent.card.optString("title"));
            StringBuilder children=new StringBuilder();for(WorkItemRelations.Entry entry:WorkItemRelations.entries(b))if(card.optString("id").equals(entry.card.optString("parentId"))){if(children.length()>0)children.append("\n");children.append(entry.card.optString("title"));}
            editorNote(relational,"Children",children.length()==0?"No child work items":children.toString());
            relational.addView(action("Manage relationships and conversions",()->{new AlertDialog.Builder(this).setTitle("Discard unsaved edits?").setMessage("Save your changes before managing relationships, or discard them to continue.").setNegativeButton("Keep editing",null).setPositiveButton("Discard and continue",(d,w)->{((AlertDialog)sc.getTag()).dismiss();cardMenu(b,c,card);}).show();}));
        }catch(JSONException e){editorNote(relational,"Relationships","Unable to load relationships.");}
        editorNote(relational,"File bookmarks","Not available yet on Android.");
        TextView taskHead=text("Task list",14,INK,true);taskHead.setPadding(0,dp(18),0,dp(8));relational.addView(taskHead);
        JSONArray tasks;try{tasks=new JSONArray(array(card,"tasks").toString());}catch(JSONException ex){throw new IllegalStateException(ex);}
        LinearLayout taskBox=vertical();relational.addView(taskBox);
        java.util.concurrent.atomic.AtomicReference<java.util.function.Consumer<String>> convertTask=new java.util.concurrent.atomic.AtomicReference<>();
        java.util.function.Consumer<String> convertFromDraft=taskId->{if(convertTask.get()!=null)convertTask.get().accept(taskId);};
        editCardRefresh(taskBox,tasks,convertFromDraft);
        relational.addView(action("+ Add task",()->input("Add task","Task","",value->{tasks.put(put(put(put(obj(),"id",id()),"title",value),"done",false));editCardRefresh(taskBox,tasks,convertFromDraft);})));


        LinearLayout commentSection=editorSection(form,"Comments",true);
        EditText comments=field(commentSection,"Comments (one per line)",card.optString("commentsText"),true);

        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Work item").setView(sc).setNegativeButton("Cancel",(d,w)->show()).setNeutralButton("More",null).setPositiveButton("Save",null).create();sc.setTag(dialog);
        java.util.function.BooleanSupplier saveDraft=()->{String value=title.getText().toString().trim();if(value.isEmpty()){title.setError("Required");title.requestFocus();return false;}
            JSONObject previous;try{previous=new JSONObject(card.toString());}catch(JSONException e){error("Could not save work item",e.getMessage());return false;}
            put(card,"tasks",tasks);put(card,"reminders",reminders);put(card,"title",value);put(card,"description",desc.getText().toString());put(card,"labels",labels.getText().toString());put(card,"due",due.getText().toString());put(card,"points",points.getText().toString());put(card,"priority",priority.getText().toString());put(card,"difficulty",difficulty.getText().toString());put(card,"ado",ado.getText().toString());put(card,"sm",sm.getText().toString());put(card,"commentsText",comments.getText().toString());
            if(save())return true;restoreObject(card,previous);return false;
        };
        convertTask.set(taskId->{if(saveDraft.getAsBoolean()){dialog.dismiss();mutateBoard(b,draft->WorkItemRelations.taskToCard(draft,card.optString("id"),taskId));}});
        dialog.setOnShowListener(x->{
            dialog.getButton(-1).setOnClickListener(v->{if(saveDraft.getAsBoolean()){dialog.dismiss();show();}});
            dialog.getButton(-3).setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Discard unsaved edits?").setMessage("Save your changes before using work item actions, or discard them to continue.").setNegativeButton("Keep editing",null).setPositiveButton("Discard and continue",(d,w)->{dialog.dismiss();cardMenu(b,c,card);}).show());
            dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels-dp(24),WindowManager.LayoutParams.WRAP_CONTENT);
        });dialog.show();
    }
    private void editCardRefresh(LinearLayout box,JSONArray tasks,java.util.function.Consumer<String> convert){
        box.removeAllViews();for(int i=0;i<tasks.length();i++){JSONObject task=at(tasks,i);LinearLayout row=new LinearLayout(this);CheckBox cb=new CheckBox(this);cb.setText(task.optString("title"));cb.setChecked(task.optBoolean("done"));cb.setOnCheckedChangeListener((v,on)->put(task,"done",on));
            cb.setOnLongClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);taskMenu(task,()->convert.accept(task.optString("id")),()->{remove(tasks,task.optString("id"));editCardRefresh(box,tasks,convert);});return true;});
            row.addView(cb,new LinearLayout.LayoutParams(0,-2,1));row.addView(action("×",()->{remove(tasks,task.optString("id"));editCardRefresh(box,tasks,convert);}));box.addView(row);}
    }
    private boolean transferCard(JSONObject from,JSONObject to,JSONObject card){
        return CardMoves.transfer(from,to,card,this::save);
    }
    private void moveCard(JSONObject b,JSONObject from,JSONObject card){JSONArray cols=array(b,"columns");String[] names=new String[cols.length()];for(int i=0;i<names.length;i++)names[i]=at(cols,i).optString("name");new AlertDialog.Builder(this).setTitle("Move to column").setItems(names,(d,n)->{JSONObject to=at(cols,n);if(to!=from)transferCard(from,to,card);show();}).setOnCancelListener(d->show()).show();}
    @Override public void onBackPressed(){if(!tab.equals("boards")){tab="boards";show();}else if(board()!=null){String parent=board().optString("parent");if(parent.isEmpty()){current="";show();}else openBoard(parent);}else super.onBackPressed();}
}
