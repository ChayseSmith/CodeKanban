package com.battlemanson.codekanban;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import java.util.function.IntConsumer;

/** Hue/saturation wheel; brightness is controlled separately by the picker. */
final class ColorWheelView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final float[] hsv=new float[3];private IntConsumer listener;
    ColorWheelView(Context context,int color){super(context);Color.colorToHSV(color,hsv);setContentDescription("Color wheel. For precise or accessible color selection, use the hex color field.");}
    void setOnColorChanged(IntConsumer listener){this.listener=listener;}
    int color(){return Color.HSVToColor(hsv);}
    void setColor(int color){Color.colorToHSV(color,hsv);invalidate();}
    void setBrightness(float brightness){hsv[2]=Math.max(0,Math.min(1,brightness));invalidate();if(listener!=null)listener.accept(color());}
    private float radius(){return Math.max(1,Math.min(getWidth(),getHeight())/2f-getResources().getDisplayMetrics().density*12);}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float x=getWidth()/2f,y=getHeight()/2f,r=radius();
        paint.setShader(new SweepGradient(x,y,new int[]{Color.RED,Color.YELLOW,Color.GREEN,Color.CYAN,Color.BLUE,Color.MAGENTA,Color.RED},null));canvas.drawCircle(x,y,r,paint);
        paint.setShader(new RadialGradient(x,y,r,Color.WHITE,Color.TRANSPARENT,Shader.TileMode.CLAMP));canvas.drawCircle(x,y,r,paint);paint.setShader(null);
        paint.setColor(Color.argb(Math.round((1-hsv[2])*255),0,0,0));canvas.drawCircle(x,y,r,paint);
        double angle=Math.toRadians(hsv[0]);float mx=x+(float)Math.cos(angle)*r*hsv[1],my=y+(float)Math.sin(angle)*r*hsv[1];paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(5*getResources().getDisplayMetrics().density);paint.setColor(Color.BLACK);canvas.drawCircle(mx,my,7*getResources().getDisplayMetrics().density,paint);paint.setStrokeWidth(2*getResources().getDisplayMetrics().density);paint.setColor(Color.WHITE);canvas.drawCircle(mx,my,7*getResources().getDisplayMetrics().density,paint);paint.setStyle(Paint.Style.FILL);
    }
    @Override public boolean onTouchEvent(MotionEvent event){if(event.getActionMasked()!=MotionEvent.ACTION_DOWN&&event.getActionMasked()!=MotionEvent.ACTION_MOVE&&event.getActionMasked()!=MotionEvent.ACTION_UP)return false;float dx=event.getX()-getWidth()/2f,dy=event.getY()-getHeight()/2f;hsv[0]=(float)((Math.toDegrees(Math.atan2(dy,dx))+360)%360);hsv[1]=Math.min(1,(float)Math.hypot(dx,dy)/radius());invalidate();if(listener!=null)listener.accept(color());if(event.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;}
    @Override public boolean performClick(){super.performClick();return true;}
}
