package com.battlemanson.codekanban;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import java.io.IOException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

final class BoardIconPicker {
    private final Activity activity;private final MdiIcons icons;private final int ink,muted,surface,border,accent;
    private String selected;private int color;private TextView preview;private GridLayout swatches;private IconAdapter adapter;
    private BoardIconPicker(Activity activity,MdiIcons icons,String selected,int color,int ink,int muted,int surface,int border,int accent){this.activity=activity;this.icons=icons;this.selected=icons.get(selected).name;this.color=color;this.ink=ink;this.muted=muted;this.surface=surface;this.border=border;this.accent=accent;}
    static void show(Activity activity,String selected,int color,int ink,int muted,int surface,int border,int accent,BiConsumer<String,Integer> save){
        try{new BoardIconPicker(activity,MdiIcons.load(activity),selected,color,ink,muted,surface,border,accent).open(save);}
        catch(Exception e){new AlertDialog.Builder(activity).setTitle("Icons unavailable").setMessage("Could not load the offline icon catalog.").setPositiveButton("OK",null).show();}
    }
    private int dp(float n){return (int)(n*activity.getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String value,int size,int color){TextView view=new TextView(activity);view.setText(value);view.setTextSize(size);view.setTextColor(color);return view;}
    private GradientDrawable shape(int color,int stroke,boolean circle){GradientDrawable d=new GradientDrawable();d.setColor(color);if(circle)d.setShape(GradientDrawable.OVAL);else d.setCornerRadius(dp(10));if(stroke!=0)d.setStroke(dp(2),stroke);return d;}
    static int iconBackdrop(int color){return Color.luminance(color)<.24?Color.rgb(239,244,250):Color.rgb(34,43,59);}
    private TextView icon(String name,int color,int size){TextView glyph=text(icons.get(name).glyph(),size,color);glyph.setTypeface(icons.font);glyph.setGravity(Gravity.CENTER);glyph.setIncludeFontPadding(false);return glyph;}
    private void open(BiConsumer<String,Integer> save){
        ScrollView scroll=new ScrollView(activity);LinearLayout form=new LinearLayout(activity);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(16),dp(8),dp(16),dp(16));scroll.addView(form);
        LinearLayout top=new LinearLayout(activity);top.setGravity(Gravity.CENTER_VERTICAL);form.addView(top);preview=icon(selected,color,32);preview.setBackground(shape(iconBackdrop(color),0,true));LinearLayout.LayoutParams iconSize=new LinearLayout.LayoutParams(dp(56),dp(56));iconSize.rightMargin=dp(12);top.addView(preview,iconSize);TextView selectedName=text("",14,ink);top.addView(selectedName,new LinearLayout.LayoutParams(0,-2,1));
        EditText search=new EditText(activity);search.setSingleLine();search.setHint("Search 7,447 icons");search.setContentDescription("Search Material Design Icons");form.addView(search);
        GridView grid=new GridView(activity);grid.setNumColumns(4);grid.setVerticalSpacing(dp(4));grid.setHorizontalSpacing(dp(4));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setBackground(shape(surface,border,false));form.addView(grid,new LinearLayout.LayoutParams(-1,dp(260)));
        adapter=new IconAdapter();grid.setAdapter(adapter);grid.setOnItemClickListener((parent,view,position,id)->{selected=adapter.items.get(position).name;updatePreview(selectedName);adapter.notifyDataSetChanged();});
        grid.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN)v.getParent().requestDisallowInterceptTouchEvent(true);else if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)v.getParent().requestDisallowInterceptTouchEvent(false);return false;});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){adapter.items=icons.search(s.toString());adapter.notifyDataSetChanged();grid.setSelection(0);}public void afterTextChanged(Editable e){}});
        TextView colors=text("Color",16,ink);colors.setPadding(0,dp(16),0,dp(6));form.addView(colors);swatches=new GridLayout(activity);swatches.setColumnCount(8);form.addView(swatches);
        TextView custom=text("◉  Custom color wheel",15,accent);custom.setPadding(dp(4),dp(12),dp(4),dp(12));custom.setOnClickListener(v->customColor(chosen->{color=chosen;updatePreview(selectedName);renderSwatches(selectedName);adapter.notifyDataSetChanged();}));form.addView(custom);
        renderSwatches(selectedName);updatePreview(selectedName);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Set board icon").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->save.accept(selected,color)).create();dialog.setOnShowListener(v->dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN));dialog.show();
    }
    private void updatePreview(TextView label){preview.setText(icons.get(selected).glyph());preview.setTextColor(color);preview.setBackground(shape(iconBackdrop(color),0,true));label.setText(selected.replace('-', ' ') + "\n"+String.format(Locale.ROOT,"#%06X",color&0xffffff));}
    private void renderSwatches(TextView selectedName){swatches.removeAllViews();ArrayList<Integer> colors=new ArrayList<>(Arrays.asList(Color.BLACK,Color.DKGRAY,Color.GRAY,Color.LTGRAY,Color.WHITE,Color.rgb(128,64,0),Color.rgb(255,192,203),Color.rgb(128,0,128)));for(int i=0;i<24;i++)colors.add(Color.HSVToColor(new float[]{i*15f,1f,1f}));
        for(int value:colors){TextView swatch=text(value==color?"✓":"",18,Color.luminance(value)<.35?Color.WHITE:Color.BLACK);swatch.setGravity(Gravity.CENTER);swatch.setBackground(shape(value,value==color?accent:border,true));swatch.setContentDescription(String.format(Locale.ROOT,"Color #%06X%s",value&0xffffff,value==color?", selected":""));GridLayout.LayoutParams params=new GridLayout.LayoutParams();params.width=dp(34);params.height=dp(34);params.setMargins(dp(2),dp(2),dp(2),dp(2));swatches.addView(swatch,params);swatch.setOnClickListener(v->{color=value;updatePreview(selectedName);renderSwatches(selectedName);adapter.notifyDataSetChanged();});}}
    private final class IconAdapter extends android.widget.BaseAdapter {
        List<MdiIcons.Icon> items=icons.all;
        @Override public int getCount(){return items.size();}@Override public Object getItem(int position){return items.get(position);}@Override public long getItemId(int position){return position;}
        @Override public View getView(int position,View recycled,android.view.ViewGroup parent){LinearLayout cell;if(recycled instanceof LinearLayout)cell=(LinearLayout)recycled;else{cell=new LinearLayout(activity);cell.setOrientation(LinearLayout.VERTICAL);cell.setGravity(Gravity.CENTER);cell.setPadding(dp(2),dp(4),dp(2),dp(4));TextView symbol=text("",27,color);symbol.setTypeface(icons.font);symbol.setGravity(Gravity.CENTER);cell.addView(symbol,new LinearLayout.LayoutParams(-1,dp(36)));TextView name=text("",10,muted);name.setGravity(Gravity.CENTER);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);cell.addView(name,new LinearLayout.LayoutParams(-1,dp(17)));}
            MdiIcons.Icon item=items.get(position);TextView symbol=(TextView)cell.getChildAt(0),name=(TextView)cell.getChildAt(1);symbol.setText(item.glyph());symbol.setTextColor(color);symbol.setBackground(shape(iconBackdrop(color),0,false));name.setText(item.name);cell.setBackground(shape(surface,item.name.equals(selected)?accent:0,false));cell.setContentDescription(item.name+(item.name.equals(selected)?", selected":""));return cell;}
    }
    private void customColor(IntConsumer accept){
        LinearLayout form=new LinearLayout(activity);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(20),dp(12),dp(20),dp(16));
        TextView preview=text("",14,ink);preview.setGravity(Gravity.CENTER);form.addView(preview,new LinearLayout.LayoutParams(-1,dp(40)));
        ColorWheelView wheel=new ColorWheelView(activity,color);int side=Math.min(dp(260),activity.getResources().getDisplayMetrics().widthPixels-dp(80));LinearLayout.LayoutParams wheelSize=new LinearLayout.LayoutParams(side,side);wheelSize.gravity=Gravity.CENTER;form.addView(wheel,wheelSize);
        TextView label=text("Brightness",14,muted);form.addView(label);SeekBar brightness=new SeekBar(activity);brightness.setMax(100);float[] initial=new float[3];Color.colorToHSV(color,initial);brightness.setProgress(Math.round(initial[2]*100));form.addView(brightness);
        EditText hex=new EditText(activity);hex.setSingleLine();hex.setHint("#RRGGBB");hex.setText(String.format(Locale.ROOT,"#%06X",color&0xffffff));form.addView(hex);boolean[] updating={false};
        IntConsumer fromWheel=value->{updating[0]=true;hex.setText(String.format(Locale.ROOT,"#%06X",value&0xffffff));preview.setTextColor(Color.luminance(value)<.35?Color.WHITE:Color.BLACK);preview.setBackground(shape(value,0,false));preview.setText(String.format(Locale.ROOT,"#%06X",value&0xffffff));updating[0]=false;};
        wheel.setOnColorChanged(fromWheel);fromWheel.accept(color);brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int value,boolean user){if(user)wheel.setBrightness(value/100f);}public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}});
        hex.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(updating[0]||!s.toString().matches("#[0-9a-fA-F]{6}"))return;int value=Color.parseColor(s.toString());wheel.setColor(value);float[] hsv=new float[3];Color.colorToHSV(value,hsv);brightness.setProgress(Math.round(hsv[2]*100));preview.setTextColor(Color.luminance(value)<.35?Color.WHITE:Color.BLACK);preview.setBackground(shape(value,0,false));preview.setText(s.toString().toUpperCase(Locale.ROOT));}public void afterTextChanged(Editable e){}});
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Custom color").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Use color",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{String value=hex.getText().toString();if(!value.matches("#[0-9a-fA-F]{6}")){hex.setError("Use #RRGGBB");return;}accept.accept(Color.parseColor(value));dialog.dismiss();}));dialog.show();
    }
}
