// SPDX-License-Identifier: GPL-2.0-or-later
package info.exult;

final class NovaInputMath {
    private NovaInputMath() {}
    static float clamp(float v,float lo,float hi) { return Math.max(lo,Math.min(hi,v)); }
    static float[] velocity(float x,float y,float deadZone) {
        float radius=(float)Math.sqrt(x*x+y*y);
        if(radius<=deadZone || radius==0) return new float[]{0,0};
        float magnitude=clamp((radius-deadZone)/(1-deadZone),0,1);
        magnitude*=magnitude;
        return new float[]{x/radius*magnitude,y/radius*magnitude};
    }
    static int directionKey(int x,int y) {
        // Android KEYCODE_NUMPAD_0 = 144. One key represents the full diagonal.
        if(x==0 && y==0) return 0;
        return 144+(y<0?8:y>0?2:5)+x;
    }
}
