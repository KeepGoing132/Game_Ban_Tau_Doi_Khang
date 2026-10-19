package client.ui;

import javax.sound.sampled.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.prefs.Preferences;

/** Original synthesized sounds. Audio never blocks Swing or network processing. */
public final class GameAudio {
    public enum Sound { CLICK, SELECT, LAUNCH, MISS, HIT, SUNK, TURN, WIN, LOSE, SPLASH, WAVE, PING }
    private static final Preferences PREF=Preferences.userNodeForPackage(GameAudio.class);
    private static volatile boolean enabled=PREF.getBoolean("sound",true);
    private static final Set<Clip> playing=Collections.newSetFromMap(new ConcurrentHashMap<Clip,Boolean>());
    private static final ExecutorService WORK=new ThreadPoolExecutor(1,2,20,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(12),r->{Thread t=new Thread(r,"game-audio");t.setDaemon(true);return t;},new ThreadPoolExecutor.DiscardPolicy());
    private static volatile Clip ambience;
    private static volatile boolean inBattle;
    private GameAudio(){}
    public static boolean isEnabled(){return enabled;}
    public static void setEnabled(boolean value){enabled=value;PREF.putBoolean("sound",value);if(!value){for(Clip c:playing)c.close();stopAmbience();}else if(inBattle)startAmbience();}
    public static void play(Sound sound){if(!enabled)return;WORK.execute(()->{if(!enabled)return;try{Clip c=AudioSystem.getClip();byte[] data=synthesize(sound);c.open(format(),data,0,data.length);playing.add(c);c.addLineListener(e->{if(e.getType()==LineEvent.Type.STOP){playing.remove(c);c.close();}});if(enabled)c.start();else c.close();}catch(Exception ignored){/* Machines without an audio device still play normally. */}});}
    public static AudioFormat format(){return new AudioFormat(22050,16,1,true,false);}
    public static byte[] synthesize(Sound sound){
        double duration=sound==Sound.SUNK?1.1:sound==Sound.WAVE?1.0:sound==Sound.WIN||sound==Sound.LOSE?.9:sound==Sound.PING?.8:sound==Sound.SPLASH?.5:sound==Sound.LAUNCH?.42:sound==Sound.HIT?.38:sound==Sound.MISS?.32:.10;
        int count=(int)(22050*duration);byte[] bytes=new byte[count*2];Random noise=new Random(73+sound.ordinal());double phase=0,swell=0;
        for(int i=0;i<count;i++){double t=i/22050.0,p=t/duration,envelope=Math.min(1,t/.008)*Math.pow(1-p,2),frequency=600,value;
            switch(sound){
                case CLICK:frequency=700-300*p;break;case SELECT:frequency=1100+450*p;break;
                case TURN:frequency=p<.5?660:990;break;
                case WIN:frequency=new double[]{523.25,659.25,783.99,1046.5}[Math.min(3,(int)(p*4))];envelope=Math.min(1,t/.01)*.5*(1-p);break;
                case LOSE:frequency=440-260*p;break;
                case LAUNCH:frequency=180+1400*p;break;
                case MISS:frequency=420-250*p;break;
                case HIT:case SUNK:frequency=100-65*p;break;
                case SPLASH:frequency=900+2200*p;envelope=Math.min(1,t/.004)*Math.pow(1-p,1.5)*(p<.12?1:.72);break;
                case WAVE:frequency=90-30*p;envelope=Math.pow(Math.sin(Math.PI*p),.7)*.9;break;
                case PING:frequency=1250-260*p;envelope=Math.min(1,t/.006)*Math.exp(-3.4*p);break;
                default:break;
            }
            phase+=2*Math.PI*frequency/22050;value=Math.sin(phase);
            if(sound==Sound.HIT||sound==Sound.SUNK)value=.38*value+.62*(noise.nextDouble()*2-1);
            if(sound==Sound.LAUNCH||sound==Sound.MISS)value=.65*value+.35*(noise.nextDouble()*2-1);
            if(sound==Sound.SPLASH){double grain=noise.nextDouble()*2-1;swell+=.22*(grain-swell);value=.30*value+.80*(grain-swell*.4);}
            if(sound==Sound.WAVE){double grain=noise.nextDouble()*2-1;swell+=.045*(grain-swell);value=.42*value+.85*swell*2.4;}
            short sample=(short)(Math.max(-1,Math.min(1,value))*envelope*8500);bytes[i*2]=(byte)sample;bytes[i*2+1]=(byte)(sample>>8);
        }return bytes;
    }
    public static void battle(boolean active){inBattle=active;if(active&&enabled)startAmbience();else stopAmbience();}
    private static synchronized void stopAmbience(){if(ambience!=null){ambience.stop();ambience.close();ambience=null;}}
    private static void startAmbience(){WORK.execute(()->{synchronized(GameAudio.class){if(!enabled||!inBattle||ambience!=null)return;try{byte[] data=surfLoop();Clip c=AudioSystem.getClip();c.open(format(),data,0,data.length);ambience=c;c.loop(Clip.LOOP_CONTINUOUSLY);}catch(Exception ignored){}}});}
    /** Four seconds of open sea: swell, hiss, rumble and a distant gull, looped seamlessly. */
    public static byte[] surfLoop(){
        int count=22050*4;byte[] bytes=new byte[count*2];Random noise=new Random(9187);double low=0,deep=0,phase=0;
        for(int i=0;i<count;i++){
            double t=i/22050.0,p=t/4.0;
            double surge=Math.pow(.5+.5*Math.sin(2*Math.PI*2*p-1.2),1.5);
            double grain=noise.nextDouble()*2-1;
            low+=.06*(grain-low);deep+=.012*(grain-deep);
            double rumble=(low-deep)*3.2;
            phase+=2*Math.PI*(46+7*Math.sin(2*Math.PI*p))/22050;
            double gull=0;
            if(p>.60&&p<.70){double gp=(p-.60)/.10;gull=.15*Math.sin(2*Math.PI*(1450-460*gp)*t)*Math.sin(Math.PI*gp);}
            double value=(rumble*.8+(grain-low)*.85*(.35+.65*surge)+Math.sin(phase)*.32)*(.42+.58*surge)+gull;
            short sample=(short)(Math.max(-1,Math.min(1,value))*7000);bytes[i*2]=(byte)sample;bytes[i*2+1]=(byte)(sample>>8);
        }
        int seam=600;
        for(int i=0;i<seam;i++){int tail=count-seam+i;double mix=i/(double)seam;
            short head=(short)((bytes[i*2]&0xff)|(bytes[i*2+1]<<8)),end=(short)((bytes[tail*2]&0xff)|(bytes[tail*2+1]<<8));
            short blended=(short)(head*(1-mix)+end*mix);bytes[i*2]=(byte)blended;bytes[i*2+1]=(byte)(blended>>8);}
        return bytes;
    }
}
