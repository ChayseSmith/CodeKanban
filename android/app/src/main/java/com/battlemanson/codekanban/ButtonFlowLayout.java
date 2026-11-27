package com.battlemanson.codekanban;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;

/** Packs content-sized automation buttons into rows within the actual container width. */
final class ButtonFlowLayout extends ViewGroup {
    private final int gap;private final ArrayList<Rect> positions=new ArrayList<>();
    ButtonFlowLayout(Context context){super(context);float density=getResources().getDisplayMetrics().density;gap=Math.round(8*density);setPadding(0,Math.round(6*density),0,Math.round(6*density));}
    @Override protected LayoutParams generateDefaultLayoutParams(){return new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT);}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int widthMode=MeasureSpec.getMode(widthSpec),available=widthMode==MeasureSpec.UNSPECIFIED?Integer.MAX_VALUE:Math.max(0,MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight());
        int x=0,y=getPaddingTop(),rowHeight=0,widest=0,state=0;boolean rowHasChild=false;positions.clear();
        for(int i=0;i<getChildCount();i++){View child=getChildAt(i);if(child.getVisibility()==GONE){positions.add(null);continue;}
            child.measure(MeasureSpec.makeMeasureSpec(widthMode==MeasureSpec.UNSPECIFIED?0:available,widthMode==MeasureSpec.UNSPECIFIED?MeasureSpec.UNSPECIFIED:MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
            int width=child.getMeasuredWidth(),height=child.getMeasuredHeight();
            if(rowHasChild&&(long)x+gap+width>available){y+=rowHeight+gap;x=0;rowHeight=0;rowHasChild=false;}
            if(rowHasChild)x+=gap;
            positions.add(new Rect(x,y,x+width,y+height));x+=width;rowHeight=Math.max(rowHeight,height);widest=Math.max(widest,x);rowHasChild=true;state=combineMeasuredStates(state,child.getMeasuredState());
        }
        setMeasuredDimension(resolveSizeAndState(Math.max(getSuggestedMinimumWidth(),widest+getPaddingLeft()+getPaddingRight()),widthSpec,state),resolveSizeAndState(Math.max(getSuggestedMinimumHeight(),y+rowHeight+getPaddingBottom()),heightSpec,state<<MEASURED_HEIGHT_STATE_SHIFT));
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        boolean rtl=getLayoutDirection()==LAYOUT_DIRECTION_RTL;
        for(int i=0;i<getChildCount();i++){View child=getChildAt(i);Rect p=positions.get(i);if(p==null||child.getVisibility()==GONE)continue;
            int x=rtl?getWidth()-getPaddingRight()-p.right:getPaddingLeft()+p.left;child.layout(x,p.top,x+p.width(),p.bottom);
        }
    }
}
