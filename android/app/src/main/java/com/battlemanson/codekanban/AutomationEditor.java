package com.battlemanson.codekanban;

import android.app.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.function.Consumer;

/** Native rule builder: repeatable conditions and actions, without requiring JSON editing. */
final class AutomationEditor {
    private static final String[] TRIGGER_LABELS={"Work item created","Work item updated","Work item moved to a column","Conditions become true (including age)"};
    private static final String[] FIELD_LABELS={"Column name","Previous column name","Item age (hours)","Column age (hours)","Title","Description","Labels","Priority","Story points","Difficulty","Due date overdue (true/false)","All tasks complete (true/false)"};
    private static final String[] OP_LABELS={"Equals","Does not equal","Contains","At least","At most"};
    private static final String[] ACTION_LABELS={"Set card accent color","Clear card accent color","Complete all tasks","Reopen all tasks","Add reminder after minutes","Add label","Set priority","Set story points","Set difficulty"};
    private final Activity activity;private final JSONObject data,rule;private final int ink,muted;private final Consumer<JSONObject> saved;
    private LinearLayout conditions,actions;private EditText name;private Spinner scope,trigger,match;private CheckBox enabled;
    private final ArrayList<String> boardIds=new ArrayList<>();
    private AutomationEditor(Activity activity,JSONObject data,JSONObject rule,int ink,int muted,Consumer<JSONObject> saved){this.activity=activity;this.data=data;this.rule=rule;this.ink=ink;this.muted=muted;this.saved=saved;}
    static void show(Activity activity,JSONObject data,JSONObject source,int ink,int muted,Consumer<JSONObject> saved){
        try{JSONObject rule=source==null?new JSONObject().put("id",UUID.randomUUID().toString()).put("name","").put("enabled",true).put("trigger","moved").put("match","all").put("conditions",new JSONArray()).put("actions",new JSONArray()):BoardTemplate.copy(source);
            new AutomationEditor(activity,data,rule,ink,muted,saved).open();
        }catch(JSONException e){new AlertDialog.Builder(activity).setMessage(e.getMessage()).setPositiveButton("OK",null).show();}
    }
    private int dp(int n){return (int)(n*activity.getResources().getDisplayMetrics().density+.5f);}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(activity);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView text(String value,int size,int color){TextView t=new TextView(activity);t.setText(value);t.setTextColor(color);t.setTextSize(size);t.setPadding(0,dp(8),0,dp(8));return t;}
    private void label(LinearLayout parent,String title){TextView t=text(title,16,ink);t.setTypeface(null,Typeface.BOLD);parent.addView(t);}
    private void button(LinearLayout parent,String title,Runnable action){Button b=new Button(activity);b.setText(title);b.setOnClickListener(v->action.run());parent.addView(b);}
    private Spinner spinner(LinearLayout parent,String[] options,int selected){Spinner s=new Spinner(activity);s.setAdapter(new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_dropdown_item,options));s.setSelection(Math.max(0,selected));parent.addView(s);return s;}
    private EditText input(LinearLayout parent,String value,String hint){EditText edit=new EditText(activity);edit.setText(value);edit.setHint(hint);edit.setTextColor(ink);edit.setSingleLine();parent.addView(edit);return edit;}
    private static int index(String[] options,String value){int i=Arrays.asList(options).indexOf(value);return Math.max(0,i);}
    private String boardPath(JSONObject board){ArrayList<String> parts=new ArrayList<>();Set<String> seen=new HashSet<>();JSONArray boards=data.optJSONArray("boards");while(board!=null&&seen.add(board.optString("id"))){parts.add(board.optString("name"));String parent=board.optString("parent");board=null;if(boards!=null)for(int i=0;i<boards.length();i++)if(parent.equals(boards.optJSONObject(i).optString("id"))){board=boards.optJSONObject(i);break;}}Collections.reverse(parts);return android.text.TextUtils.join(" / ",parts);}
    private void open()throws JSONException{
        LinearLayout form=vertical();form.setPadding(dp(20),dp(12),dp(20),dp(16));ScrollView scroll=new ScrollView(activity);scroll.addView(form);
        name=input(form,rule.optString("name"),"Automation name");enabled=new CheckBox(activity);enabled.setText("Enabled");enabled.setTextColor(ink);enabled.setChecked(rule.optBoolean("enabled",true));form.addView(enabled);
        label(form,"Apply to");ArrayList<String> boards=new ArrayList<>();boards.add("All boards");boardIds.add("");JSONArray list=data.optJSONArray("boards");if(list!=null)for(int i=0;i<list.length();i++){JSONObject board=list.getJSONObject(i);boards.add(boardPath(board));boardIds.add(board.optString("id"));}
        String selectedScope=rule.optString("boardId");if(!boardIds.contains(selectedScope)){boards.add("Unavailable board (choose another)");boardIds.add(selectedScope);}
        scope=spinner(form,boards.toArray(new String[0]),boardIds.indexOf(selectedScope));
        label(form,"When");trigger=spinner(form,TRIGGER_LABELS,index(AutomationEngine.TRIGGERS,rule.optString("trigger")));
        form.addView(text("Age rules are checked on saves, on opening the app and every minute while visible. Existing items start aging when this feature is first installed. Actions run in list order; they do not recursively trigger other rules.",13,muted));
        label(form,"Conditions");match=spinner(form,new String[]{"All conditions must match","Any condition may match"},rule.optString("match","all").equals("any")?1:0);
        conditions=vertical();form.addView(conditions);button(form,"+ Add condition",()->condition(null,-1));
        label(form,"Actions");actions=vertical();form.addView(actions);button(form,"+ Add action",()->action(null,-1));
        form.addView(text("Column names match exactly, ignoring letter case. Board scope applies to that board only. Colors add an accent border. Automated reminders use normal notifications on the device running the automation; configure full-screen alarms in the work item afterwards.",13,muted));
        render();AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Automation").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        dialog.setOnShowListener(d->{dialog.getWindow().setLayout(activity.getResources().getDisplayMetrics().widthPixels-dp(24),activity.getResources().getDisplayMetrics().heightPixels-dp(72));dialog.getButton(-1).setOnClickListener(v->{try{
            rule.put("name",name.getText().toString().trim()).put("enabled",enabled.isChecked()).put("boardId",boardIds.get(scope.getSelectedItemPosition())).put("trigger",AutomationEngine.TRIGGERS[trigger.getSelectedItemPosition()]).put("match",match.getSelectedItemPosition()==1?"any":"all");AutomationEngine.validate(rule);saved.accept(rule);dialog.dismiss();
        }catch(Exception e){new AlertDialog.Builder(activity).setTitle("Check automation").setMessage(e.getMessage()).setPositiveButton("OK",null).show();}});});dialog.show();
    }
    private void render(){try{
        conditions.removeAllViews();JSONArray cs=rule.getJSONArray("conditions");if(cs.length()==0)conditions.addView(text("No conditions: match every work item in scope.",14,muted));
        for(int i=0;i<cs.length();i++){final int n=i;JSONObject c=cs.getJSONObject(i);TextView row=text(FIELD_LABELS[index(AutomationEngine.FIELDS,c.optString("field"))]+" "+OP_LABELS[index(AutomationEngine.OPS,c.optString("op"))].toLowerCase(Locale.ROOT)+" "+c.optString("value"),15,ink);conditions.addView(row);row.setOnClickListener(v->condition(c,n));}
        actions.removeAllViews();JSONArray as=rule.getJSONArray("actions");if(as.length()==0)actions.addView(text("Add an action to finish this rule.",14,muted));
        for(int i=0;i<as.length();i++){final int n=i;JSONObject a=as.getJSONObject(i);TextView row=text((i+1)+". "+ACTION_LABELS[index(AutomationEngine.ACTIONS,a.optString("type"))]+(a.optString("value").isEmpty()?"":": "+a.optString("value")),15,ink);actions.addView(row);row.setOnClickListener(v->action(a,n));}
    }catch(JSONException e){throw new IllegalStateException(e);}}
    private void condition(JSONObject source,int position){
        LinearLayout form=vertical();form.setPadding(dp(20),dp(10),dp(20),dp(12));Spinner field=spinner(form,FIELD_LABELS,index(AutomationEngine.FIELDS,source==null?"column":source.optString("field"))),op=spinner(form,OP_LABELS,index(AutomationEngine.OPS,source==null?"eq":source.optString("op")));
        EditText value=input(form,source==null?"":source.optString("value"),"Comparison value");
        button(form,"Choose a column name",()->{try{Set<String> names=new LinkedHashSet<>();JSONArray boards=data.getJSONArray("boards");String boardId=boardIds.get(scope.getSelectedItemPosition());for(int b=0;b<boards.length();b++){JSONObject board=boards.getJSONObject(b);if(!boardId.isEmpty()&&!boardId.equals(board.optString("id")))continue;JSONArray columns=board.getJSONArray("columns");for(int c=0;c<columns.length();c++)names.add(columns.getJSONObject(c).optString("name"));}String[] options=names.toArray(new String[0]);new AlertDialog.Builder(activity).setTitle("Column name").setItems(options,(d,n)->{field.setSelection(0);op.setSelection(0);value.setText(options[n]);}).setNegativeButton("Cancel",null).show();}catch(JSONException e){value.setError(e.getMessage());}});
        form.addView(text("For stale work items: Column age At least 48 hours (2 days). Column age resets to zero on every move, including returning to a previous column or moving to another board. Ordinary edits do not reset it. Use Conditions become true to check as time passes. Tap a condition or action to edit or remove it.",13,muted));
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Condition").setView(form).setNegativeButton("Cancel",null).setNeutralButton(position<0?null:"Remove",(d,w)->{rule.optJSONArray("conditions").remove(position);render();}).setPositiveButton("Done",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{JSONObject c=new JSONObject().put("field",AutomationEngine.FIELDS[field.getSelectedItemPosition()]).put("op",AutomationEngine.OPS[op.getSelectedItemPosition()]).put("value",value.getText().toString());JSONArray cs=rule.getJSONArray("conditions");if(position<0)cs.put(c);else cs.put(position,c);render();dialog.dismiss();}catch(JSONException e){value.setError(e.getMessage());}}));dialog.show();
    }
    private void action(JSONObject source,int position){
        LinearLayout form=vertical();form.setPadding(dp(20),dp(10),dp(20),dp(12));Spinner type=spinner(form,ACTION_LABELS,index(AutomationEngine.ACTIONS,source==null?"completeTasks":source.optString("type")));
        EditText value=input(form,source==null?"":source.optString("value"),"Color, minutes, label or numeric value");
        form.addView(text("Color: #EF4444. Reminder: minutes after the trigger (e.g. 60). Complete/reopen tasks and clear color need no value. Conditions-become-true rules add a reminder only when they first match, not every minute.",13,muted));
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Action").setView(form).setNegativeButton("Cancel",null).setNeutralButton(position<0?null:"Remove",(d,w)->{rule.optJSONArray("actions").remove(position);render();}).setPositiveButton("Done",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{JSONObject a=new JSONObject().put("type",AutomationEngine.ACTIONS[type.getSelectedItemPosition()]).put("value",value.getText().toString().trim());JSONArray as=rule.getJSONArray("actions");if(position<0)as.put(a);else as.put(position,a);render();dialog.dismiss();}catch(JSONException e){value.setError(e.getMessage());}}));dialog.show();
    }
}
