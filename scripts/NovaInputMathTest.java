package info.exult;
public final class NovaInputMathTest {
    private static void check(boolean value,String why) { if(!value) throw new AssertionError(why); }
    public static void main(String[] args) {
        check(NovaInputMath.directionKey(0,0)==0,"center must release");
        int[][] samples={{-1,-1,151},{0,-1,152},{1,-1,153},{-1,0,148},{1,0,150},{-1,1,145},{0,1,146},{1,1,147}};
        for(int[] s:samples) check(NovaInputMath.directionKey(s[0],s[1])==s[2],"eight-direction mapping");
        float[] v=NovaInputMath.velocity(.08f,.08f,.12f);
        check(v[0]==0 && v[1]==0,"radial noise must remain stationary");
        v=NovaInputMath.velocity(1,1,.12f);
        check(Math.abs(Math.hypot(v[0],v[1])-1)<.00001,"diagonal must not move cursor faster");
        check(Math.abs(v[0]-v[1])<.00001,"diagonal symmetry");
        float at60=0,at120=0;
        for(int i=0;i<60;i++) at60+=.75f/60;
        for(int i=0;i<120;i++) at120+=.75f/120;
        check(Math.abs(at60-at120)<.00001,"time-based speed must match at 60 and 120 Hz");
        check(NovaInputMath.clamp(1.5f,0,1)==1,"cursor clamps to viewport");
        System.out.println("Nova input math tests passed");
    }
}
