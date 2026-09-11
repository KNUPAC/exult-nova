// SPDX-License-Identifier: GPL-2.0-or-later
// Nova controller adapter for Exult 1.12.1; uses SDL's Android input bridge.
package info.exult;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.libsdl.app.SDLActivity;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class NovaController implements Choreographer.FrameCallback, InputManager.InputDeviceListener {
    private final ExultActivity activity;
    private final View surface;
    private final SharedPreferences prefs;
    private final InputManager inputs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<Integer> held = new HashSet<>();
    private final Set<String> leftOwners = new HashSet<>(), rightOwners = new HashSet<>();
    private final String[] actions = {"Click / drag", "Back", "Double click", "Inventory", "Precision", "Drag", "Slow walk", "Right click", "Menu", "Map", "Pause combat", "Toggle combat"};
    private final int[] defaults = {KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_L1,
        KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR};
    private final int[] mapping = new int[defaults.length];
    private float sx, sy, hatX, hatY, cursorX = .5f, cursorY = .5f;
    private float deadZone, speed;
    private boolean precision, slow, slowButton, slowAxis, running, settings, rightStick;
    private int directionKey, mouseState, capture = -1, lastDevice = -1;
    private long lastFrame;
    private String diagnostic = "Move a stick or press a button to inspect input.";
    private TextView diagnosticView;

    NovaController(ExultActivity activity, View surface) {
        this.activity = activity;
        this.surface = surface;
        prefs = activity.getSharedPreferences("nova_controls", Context.MODE_PRIVATE);
        inputs = (InputManager) activity.getSystemService(Context.INPUT_SERVICE);
        for (int i=0; i<mapping.length; i++) mapping[i]=prefs.getInt("button_"+i, defaults[i]);
        deadZone=prefs.getFloat("deadzone", .12f);
        speed=prefs.getFloat("speed", .75f);
        rightStick=prefs.getBoolean("rightstick", false);
        inputs.registerInputDeviceListener(this, handler);
    }
    static boolean controller(KeyEvent e) {
        int s=e.getSource();
        return (s & InputDevice.SOURCE_GAMEPAD)==InputDevice.SOURCE_GAMEPAD
            || (s & InputDevice.SOURCE_JOYSTICK)==InputDevice.SOURCE_JOYSTICK
            || (s & InputDevice.SOURCE_DPAD)==InputDevice.SOURCE_DPAD;
    }
    boolean key(KeyEvent e) {
        if (!controller(e)) return false;
        lastDevice=e.getDeviceId();
        diagnostic="Device "+lastDevice+"  "+KeyEvent.keyCodeToString(e.getKeyCode())+"  code="+e.getKeyCode();
        if (settings) return false; // Native Android dialog owns its own navigation.
        int code=e.getKeyCode();
        boolean down=e.getAction()==KeyEvent.ACTION_DOWN;
        if (down && e.getRepeatCount()>0) return true;
        if (down) held.add(code); else held.remove(code);
        if (held.contains(KeyEvent.KEYCODE_BUTTON_START) && held.contains(KeyEvent.KEYCODE_BUTTON_SELECT)) {
            showSettings(); return true;
        }
        if (code>=KeyEvent.KEYCODE_DPAD_UP && code<=KeyEvent.KEYCODE_DPAD_RIGHT) { updateDirection(); return true; }
        for (int i=0; i<mapping.length; i++) if (mapping[i]==code) { action(i,down); return true; }
        return true; // Do not let SDL's default stick/controller path also move the avatar.
    }
    private void action(int a, boolean down) {
        switch(a) {
            case 0: mouseOwner(leftOwners,"click",down,1); break;
            case 1: if(down) { stopDirection(); tap(KeyEvent.KEYCODE_ESCAPE); } break;
            case 2: if(down && leftOwners.isEmpty()) {
                mouseOwner(leftOwners,"double",true,1);
                handler.postDelayed(()->mouseOwner(leftOwners,"double",false,1),35);
                handler.postDelayed(()->mouseOwner(leftOwners,"double",true,1),100);
                handler.postDelayed(()->mouseOwner(leftOwners,"double",false,1),135);
            } break;
            case 3: if(down) { stopDirection(); tap(KeyEvent.KEYCODE_I); } break;
            case 4: precision=down; break;
            case 5: mouseOwner(leftOwners,"drag",down,1); break;
            case 6: slowButton=down; updateSlow(); break;
            case 7: mouseOwner(rightOwners,"right",down,2); break;
            case 8: if(!down) { stopDirection(); tap(KeyEvent.KEYCODE_ESCAPE); } break;
            case 9: if(!down) { stopDirection(); tap(KeyEvent.KEYCODE_M); } break;
            case 10: if(down) tap(KeyEvent.KEYCODE_SPACE); break;
            case 11: if(down) tap(KeyEvent.KEYCODE_C); break;
            default: break;
        }
    }
    private void tap(int key) { SDLActivity.onNativeKeyDown(key); SDLActivity.onNativeKeyUp(key); }
    private void mouseOwner(Set<String> owners, String owner, boolean down, int mask) {
        boolean before=!owners.isEmpty();
        if(down) owners.add(owner); else owners.remove(owner);
        boolean after=!owners.isEmpty();
        if(before==after) return;
        mouseState=after ? mouseState|mask : mouseState&~mask;
        SDLActivity.onNativeMouse(mouseState,after?MotionEvent.ACTION_DOWN:MotionEvent.ACTION_UP,
            cursorX*Math.max(1,surface.getWidth()-1),cursorY*Math.max(1,surface.getHeight()-1),false);
    }
    private void stopDirection() {
        if(directionKey!=0) SDLActivity.onNativeKeyUp(directionKey);
        directionKey=0;
        SDLActivity.onNativeKeyUp(KeyEvent.KEYCODE_SHIFT_LEFT);
    }
    private void updateDirection() {
        int x=(held.contains(KeyEvent.KEYCODE_DPAD_RIGHT)?1:0)-(held.contains(KeyEvent.KEYCODE_DPAD_LEFT)?1:0);
        int y=(held.contains(KeyEvent.KEYCODE_DPAD_DOWN)?1:0)-(held.contains(KeyEvent.KEYCODE_DPAD_UP)?1:0);
        if(x==0) x=hatX>.5f?1:hatX<-.5f?-1:0;
        if(y==0) y=hatY>.5f?1:hatY<-.5f?-1:0;
        int next=NovaInputMath.directionKey(x,y);
        if(next==directionKey) return;
        stopDirection();
        directionKey=next;
        if(next!=0) {
            if(slow) SDLActivity.onNativeKeyDown(KeyEvent.KEYCODE_SHIFT_LEFT);
            SDLActivity.onNativeKeyDown(next);
        }
    }
    private void updateSlow() {
        boolean value=slowButton || slowAxis;
        if(value!=slow) { stopDirection(); slow=value; updateDirection(); }
    }
    private boolean trigger(MotionEvent e,String setting,int primary,int fallback,boolean previous) {
        InputDevice d=e.getDevice(); if(d==null) return false;
        int chosen=prefs.getInt(setting,d.getMotionRange(primary,e.getSource())!=null?primary:fallback);
        InputDevice.MotionRange r=d.getMotionRange(chosen,e.getSource());
        if(r==null || r.getRange()<=0) return false;
        float value=(e.getAxisValue(chosen)-r.getMin())/r.getRange();
        return value>(previous?.35f:.55f);
    }
    private float axis(MotionEvent e,int axis) {
        InputDevice d=e.getDevice();
        if(d==null) return 0;
        InputDevice.MotionRange r=d.getMotionRange(axis,e.getSource());
        if(r==null) return 0;
        float v=e.getAxisValue(axis);
        if(Math.abs(v)<=r.getFlat()) return 0;
        float extent=v<0?-r.getMin():r.getMax();
        return extent<=0?0:Math.max(-1,Math.min(1,v/extent));
    }
    boolean motion(MotionEvent e) {
        if((e.getSource() & InputDevice.SOURCE_JOYSTICK)!=InputDevice.SOURCE_JOYSTICK) return false;
        lastDevice=e.getDeviceId();
        int ax=prefs.getInt("axis_x",MotionEvent.AXIS_X), ay=prefs.getInt("axis_y",MotionEvent.AXIS_Y);
        float x=axis(e,ax), y=axis(e,ay);
        if(rightStick) {
            float rx=axis(e,MotionEvent.AXIS_Z), ry=axis(e,MotionEvent.AXIS_RZ);
            if(rx*rx+ry*ry>x*x+y*y) { x=rx; y=ry; }
        }
        diagnostic=String.format(Locale.ROOT,"Device %d  %s=%.3f  %s=%.3f  HAT=%.1f,%.1f",lastDevice,
            MotionEvent.axisToString(ax),x,MotionEvent.axisToString(ay),y,e.getAxisValue(MotionEvent.AXIS_HAT_X),e.getAxisValue(MotionEvent.AXIS_HAT_Y));
        if(diagnosticView!=null) diagnosticView.setText(diagnostic);
        if(settings) return true;
        sx=x; sy=y; hatX=axis(e,MotionEvent.AXIS_HAT_X); hatY=axis(e,MotionEvent.AXIS_HAT_Y);
        slowAxis=trigger(e,"slow_trigger",MotionEvent.AXIS_LTRIGGER,MotionEvent.AXIS_BRAKE,slowAxis);
        updateSlow();
        mouseOwner(rightOwners,"axis",trigger(e,"right_trigger",MotionEvent.AXIS_RTRIGGER,MotionEvent.AXIS_GAS,rightOwners.contains("axis")),2);
        updateDirection();
        return true;
    }
    @Override public void doFrame(long now) {
        if(!running) return;
        float dt=lastFrame==0?0:Math.min(.05f,(now-lastFrame)/1_000_000_000f);
        lastFrame=now;
        if(!settings && surface.getWidth()>0 && surface.getHeight()>0) {
            float[] velocity=NovaInputMath.velocity(sx,sy,deadZone);
            float scale=speed*(precision?.25f:1)*dt;
            if(velocity[0]!=0 || velocity[1]!=0) {
                cursorX=NovaInputMath.clamp(cursorX+velocity[0]*scale,0,1);
                cursorY=NovaInputMath.clamp(cursorY+velocity[1]*scale*surface.getWidth()/surface.getHeight(),0,1);
                SDLActivity.onNativeMouse(mouseState,MotionEvent.ACTION_MOVE,
                    cursorX*(surface.getWidth()-1),cursorY*(surface.getHeight()-1),false);
            }
        }
        Choreographer.getInstance().postFrameCallback(this);
    }
    void touch(MotionEvent e) {
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN) {
            release();
            cursorX=NovaInputMath.clamp(e.getX()/Math.max(1,surface.getWidth()),0,1);
            cursorY=NovaInputMath.clamp(e.getY()/Math.max(1,surface.getHeight()),0,1);
        }
    }
    void resume() { if(!running) { running=true; lastFrame=0; Choreographer.getInstance().postFrameCallback(this); } }
    void pause() { running=false; Choreographer.getInstance().removeFrameCallback(this); release(); }
    void destroy() { pause(); inputs.unregisterInputDeviceListener(this); }
    private void release() {
        handler.removeCallbacksAndMessages(null);
        stopDirection(); held.clear(); sx=sy=hatX=hatY=0; precision=slow=slowButton=slowAxis=false;
        for(String s:new HashSet<>(leftOwners)) mouseOwner(leftOwners,s,false,1);
        for(String s:new HashSet<>(rightOwners)) mouseOwner(rightOwners,s,false,2);
    }
    @Override public void onInputDeviceRemoved(int id) { release(); }
    @Override public void onInputDeviceChanged(int id) { release(); }
    @Override public void onInputDeviceAdded(int id) { release(); }

    void showSettings() {
        if(settings) return;
        release(); settings=true;
        LinearLayout list=new LinearLayout(activity); list.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(16*activity.getResources().getDisplayMetrics().density); list.setPadding(pad,pad,pad,pad);
        diagnosticView=new TextView(activity); diagnosticView.setText(diagnostic); list.addView(diagnosticView);
        TextView help=new TextView(activity);
        help.setText("D-pad: eight-direction walking. Left stick: pointer. Hold Start + Select to open this screen. Button labels below are Android labels; remap if your Nova differs. Use the pointer and Click for Exult menus. Trigger axes can be assigned below; M1/M2 work only when reported as distinct key events."); list.addView(help);
        for(int i=0;i<actions.length;i++) {
            final int index=i;
            Button b=new Button(activity); b.setText(actions[i]+": "+KeyEvent.keyCodeToString(mapping[i]));
            b.setOnClickListener(v->{
                capture=index;
                AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Press button for "+actions[index]).setMessage("Press a controller button. Use Cancel to leave unchanged.").setNegativeButton("Cancel",null).create();
                dialog.setOnKeyListener((d,k,e)->{
                    if(!controller(e)) return false;
                    if(e.getAction()==KeyEvent.ACTION_UP && capture>=0) {
                        for(int j=0;j<mapping.length;j++) if(j!=index && mapping[j]==k) { mapping[j]=-1; prefs.edit().putInt("button_"+j,-1).apply(); }
                        mapping[index]=k; prefs.edit().putInt("button_"+index,k).apply(); capture=-1;
                        b.setText(actions[index]+": "+KeyEvent.keyCodeToString(k)); d.dismiss();
                    }
                    return true;
                }); dialog.show();
            }); list.addView(b);
        }
        addChoice(list,"Dead zone",new String[]{"8%","12%","18%","25%"},i->{deadZone=new float[]{.08f,.12f,.18f,.25f}[i];prefs.edit().putFloat("deadzone",deadZone).apply();});
        addChoice(list,"Cursor speed",new String[]{"Slow","Balanced","Fast"},i->{speed=new float[]{.4f,.75f,1.2f}[i];prefs.edit().putFloat("speed",speed).apply();});
        addChoice(list,"Right stick also moves cursor (Z/RZ)",new String[]{"Off","On"},i->{rightStick=i==1;prefs.edit().putBoolean("rightstick",rightStick).apply();});
        for(String key:new String[]{"axis_x","axis_y","slow_trigger","right_trigger"}) {
            Button b=new Button(activity);b.setText("Choose "+key+" from detected axes");
            b.setOnClickListener(v->chooseAxis(key));list.addView(b);
        }
        Button reset=new Button(activity);reset.setText("Reset controller settings");reset.setOnClickListener(v->{prefs.edit().clear().apply();System.arraycopy(defaults,0,mapping,0,defaults.length);deadZone=.12f;speed=.75f;rightStick=false;reset.setText("Reset complete — reopen this screen");});list.addView(reset);
        ScrollView scroll=new ScrollView(activity);scroll.addView(list);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Nova controls / input test").setView(scroll).setPositiveButton("Done",null).create();
        dialog.setOnKeyListener((d,k,e)->{
            if(controller(e)) {
                diagnosticView.setText("Device "+e.getDeviceId()+"  "+KeyEvent.keyCodeToString(k)+"  code="+k);
                lastDevice=e.getDeviceId();
            }
            return false;
        });
        dialog.setOnDismissListener(d->{settings=false;diagnosticView=null;release();});dialog.show();
        dialog.getWindow().getDecorView().setOnGenericMotionListener((v,e)->motion(e));
    }
    private interface Choice { void choose(int index); }
    private void addChoice(LinearLayout list,String title,String[] values,Choice chosen) {
        Button b=new Button(activity);b.setText(title);b.setOnClickListener(v->new AlertDialog.Builder(activity).setTitle(title).setItems(values,(d,i)->{chosen.choose(i);b.setText(title+": "+values[i]);}).show());list.addView(b);
    }
    private void chooseAxis(String key) {
        InputDevice d=InputDevice.getDevice(lastDevice);
        if(d==null) { new AlertDialog.Builder(activity).setMessage("Move the controller first so its axes can be detected.").setPositiveButton("OK",null).show();return; }
        java.util.ArrayList<Integer> axes=new java.util.ArrayList<>();java.util.ArrayList<String> labels=new java.util.ArrayList<>();
        axes.add(-1);labels.add("Disabled");
        for(InputDevice.MotionRange r:d.getMotionRanges()) {
            if((r.getSource() & InputDevice.SOURCE_JOYSTICK)!=InputDevice.SOURCE_JOYSTICK) continue;
            axes.add(r.getAxis());labels.add(MotionEvent.axisToString(r.getAxis())+" ["+r.getMin()+", "+r.getMax()+"] flat="+r.getFlat());
        }
        new AlertDialog.Builder(activity).setTitle(key).setItems(labels.toArray(new String[0]),(dialog,i)->prefs.edit().putInt(key,axes.get(i)).apply()).show();
    }
}
