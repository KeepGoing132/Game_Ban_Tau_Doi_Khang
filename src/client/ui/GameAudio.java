package client.ui;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.util.Collections;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

public final class GameAudio {
    public enum Sound {
        CLICK, SELECT, LAUNCH, MISS, HIT, SUNK, TURN, WIN, LOSE, SPLASH, WAVE, PING
    }

    private static final int RATE = 22050;
    private static final Preferences PREF = Preferences.userNodeForPackage(GameAudio.class);

    private static volatile boolean enabled = PREF.getBoolean("sound", true);
    private static volatile float masterVolume = PREF.getFloat("master_volume", .85f);
    private static volatile float effectsVolume = PREF.getFloat("sfx_volume", .95f);
    private static volatile float seaVolume = PREF.getFloat("ambience_volume", .60f);

    private static final Set<Clip> playing = Collections.newSetFromMap(new ConcurrentHashMap<Clip, Boolean>());
    private static final Random VARIATION = new Random();
    private static final ExecutorService WORK = new ThreadPoolExecutor(1, 2, 20, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(16), GameAudio::audioThread, new ThreadPoolExecutor.DiscardPolicy());

    private static volatile Clip ambience;
    private static volatile boolean inBattle;

    private GameAudio() {
    }

    private static Thread audioThread(Runnable task) {
        Thread thread = new Thread(task, "game-audio");
        thread.setDaemon(true);
        return thread;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        PREF.putBoolean("sound", value);
        if (!value) {
            fadeOutAll();
            return;
        }
        WORK.execute(() -> {
            if (inBattle) {
                startAmbience();
            }
        });
    }

    public static float getMasterVolume() {
        return masterVolume;
    }

    public static void setMasterVolume(float value) {
        masterVolume = clamp(value);
        PREF.putFloat("master_volume", masterVolume);
        applyAmbienceGain();
    }

    public static float getSfxVolume() {
        return effectsVolume;
    }

    public static void setSfxVolume(float value) {
        effectsVolume = clamp(value);
        PREF.putFloat("sfx_volume", effectsVolume);
    }

    public static float getAmbienceVolume() {
        return seaVolume;
    }

    public static void setAmbienceVolume(float value) {
        seaVolume = clamp(value);
        PREF.putFloat("ambience_volume", seaVolume);
        applyAmbienceGain();
    }

    public static void resetLevels() {
        setMasterVolume(.85f);
        setSfxVolume(.95f);
        setAmbienceVolume(.60f);
        setEnabled(true);
    }

    private static float clamp(float value) {
        if (value < 0) {
            return 0;
        }
        return value > 1 ? 1 : value;
    }

    private static double sfxGain() {
        return enabled ? masterVolume * effectsVolume : 0;
    }

    public static void play(Sound sound) {
        if (!enabled || sfxGain() <= 0) {
            return;
        }
        final double gain = sfxGain();
        WORK.execute(() -> playNow(sound, gain));
        if (sound == Sound.HIT || sound == Sound.SUNK || sound == Sound.WIN || sound == Sound.LOSE) {
            duckAmbience(sound == Sound.SUNK ? 900 : 620, .35);
        }
    }

    private static void playNow(Sound sound, double gain) {
        if (!enabled) {
            return;
        }
        try {
            double pitch = 1 + (VARIATION.nextDouble() * 2 - 1) * .055;
            double varied = gain * (.94 + VARIATION.nextDouble() * .12);
            byte[] data = synthesize(sound, varied, pitch, VARIATION.nextLong());
            Clip clip = AudioSystem.getClip();
            clip.open(format(), data, 0, data.length);
            playing.add(clip);
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    playing.remove(clip);
                    closeQuietly(clip);
                }
            });
            if (enabled) {
                clip.start();
            } else {
                closeQuietly(clip);
            }
        } catch (Exception ignored) {
        }
    }

    private static void fadeOutAll() {
        for (Clip clip : playing) {
            WORK.execute(() -> ramp(clip, gainOf(clip), -80f, 180, true));
        }
        playing.clear();
        WORK.execute(() -> stopAmbience(true));
    }

    public static AudioFormat format() {
        return new AudioFormat(RATE, 16, 1, true, false);
    }

    public static byte[] synthesize(Sound sound) {
        return synthesize(sound, .9, 1, 73 + sound.ordinal());
    }

    public static byte[] synthesize(Sound sound, double pitch, long seed) {
        return synthesize(sound, .9, pitch, seed);
    }

    static byte[] synthesize(Sound sound, double gain) {
        return synthesize(sound, gain, 1, 73 + sound.ordinal());
    }

    private static byte[] synthesize(Sound sound, double gain, double pitch, long seed) {
        double duration = duration(sound);
        int samples = Math.max(1, (int) (RATE * duration));
        double[] dry = new double[samples];
        Random noise = new Random(seed);
        double[] dropStart = new double[3], dropFreq = new double[3];
        for (int k = 0; k < 3; k++) {
            dropStart[k] = .06 + noise.nextDouble() * (duration * .55);
            dropFreq[k] = 720 + noise.nextDouble() * 980;
        }
        double phase = 0, phase2 = 0, phase3 = 0;
        double hiss = 0, rumble = 0, swell = 0, foam = 0;
        for (int i = 0; i < samples; i++) {
            double t = i / (double) RATE;
            double p = t / duration;
            double grain = noise.nextDouble() * 2 - 1;
            hiss += .35 * (grain - hiss);
            rumble += .05 * (grain - rumble);
            swell += .08 * (grain - swell);
            foam += .08 * (grain - foam);
            double value;
            switch (sound) {
                case CLICK:
                    phase += 2 * Math.PI * pitch * (1480 - 420 * p) / RATE;
                    phase2 += 2 * Math.PI * pitch * 620 / RATE;
                    value = .52 * Math.sin(phase) * Math.exp(-30 * t)
                            + .26 * Math.sin(phase2) * Math.exp(-20 * t)
                            + .10 * hiss * Math.exp(-45 * t);
                    break;
                case SELECT:
                    phase += 2 * Math.PI * pitch * (1180 + 260 * smooth(p * 1.6)) / RATE;
                    value = .72 * Math.sin(phase) * Math.exp(-8 * t)
                            + .24 * Math.sin(2 * phase) * Math.exp(-13 * t)
                            + .12 * Math.sin(3 * phase) * Math.exp(-18 * t);
                    break;
                case TURN: {
                    double firstChime = Math.exp(-5.6 * t) * (Math.sin(2 * Math.PI * pitch * 660 * t)
                            + .42 * Math.sin(2 * Math.PI * pitch * 990 * t)
                            + .22 * Math.sin(2 * Math.PI * pitch * 1320 * t));
                    double secondChime = t < .17 ? 0 : Math.exp(-5 * (t - .17)) * (Math.sin(2 * Math.PI * pitch * 990 * (t - .17))
                            + .36 * Math.sin(2 * Math.PI * pitch * 1485 * (t - .17))) * smooth((t - .17) / .1);
                    double gong = .30 * Math.sin(2 * Math.PI * pitch * 165 * t) * Math.exp(-2.6 * t);
                    value = .58 * (firstChime + .9 * secondChime) + gong;
                    break;
                }
                case LAUNCH: {
                    phase += 2 * Math.PI * pitch * (150 - 112 * smooth(p * 1.3)) / RATE;
                    phase2 += 2 * Math.PI * pitch * (1500 + 420 * Math.sin(2 * Math.PI * 6.5 * t)) / RATE;
                    double crack = .58 * (grain - hiss) * Math.exp(-38 * t);
                    double boom = .92 * Math.sin(phase) * Math.exp(-4.6 * t);
                    double body = rumble * 3.4 * Math.exp(-3.2 * t);
                    double whistle = .30 * Math.sin(phase2) * smooth((t - .055) / .09)
                            * (1 - smooth((t - duration * .62) / (duration * .38))) * Math.exp(-.8 * t);
                    value = crack + boom + body + whistle;
                    break;
                }
                case MISS: {
                    phase += 2 * Math.PI * pitch * (520 - 260 * smooth(p)) / RATE;
                    double plume = Math.pow(smooth(t / .06), .7) * Math.exp(-3 * t);
                    value = .58 * (grain - foam) * .9 * plume
                            + .46 * Math.sin(phase) * Math.exp(-4.2 * t) * plume
                            + droplets(t, dropStart, dropFreq, pitch);
                    break;
                }
                case HIT: {
                    phase += 2 * Math.PI * pitch * (170 - 120 * smooth(p * 1.35)) / RATE;
                    phase2 += 2 * Math.PI * pitch * 620 / RATE;
                    phase3 += 2 * Math.PI * pitch * 1310 / RATE;
                    double kick = .36 * hiss * Math.exp(-30 * t);
                    double blast = .86 * Math.sin(phase) * Math.exp(-5.4 * t);
                    double steel = (.18 * Math.sin(phase2) + .12 * Math.sin(phase3) + .08 * Math.sin(1.5 * phase2)) * Math.exp(-7.5 * t);
                    value = kick + blast + steel + .30 * rumble * 3 * Math.exp(-3 * t);
                    break;
                }
                case SUNK: {
                    phase += 2 * Math.PI * pitch * (120 - 74 * smooth(p * 1.1)) / RATE;
                    phase2 += 2 * Math.PI * pitch * (176 + 14 * Math.sin(2 * Math.PI * 3.1 * t)) / RATE;
                    phase3 += 2 * Math.PI * pitch * (58 + 5 * Math.sin(2 * Math.PI * .7 * t)) / RATE;
                    double blast = .88 * Math.sin(phase) * Math.exp(-2.4 * t) + .40 * hiss * Math.exp(-14 * t);
                    double groan = (.36 * Math.sin(phase3) + .22 * Math.sin(1.5 * phase3) + .12 * Math.sin(phase2))
                            * smooth((t - .18) / .5) * Math.exp(-1.1 * t);
                    double rush = swell * 2.2 * smooth((t - .1) / .35) * Math.exp(-1.6 * t) * .55;
                    double bubbles = t < .5 ? 0 : Math.max(0, .34 * Math.sin(2 * Math.PI * (4 + 9 * p) * t) * Math.sin(Math.PI * Math.min(1, (p - .5) / .5)));
                    value = blast + groan + rush + bubbles + .26 * rumble * 3.4 * Math.exp(-1.5 * t);
                    break;
                }
                case SPLASH: {
                    phase += 2 * Math.PI * pitch * (860 + 700 * smooth(p)) / RATE;
                    double bloom = Math.pow(smooth(t / .05), .6) * Math.exp(-3.2 * t);
                    value = .55 * (grain - foam) * .9 * bloom
                            + .38 * Math.sin(phase) * Math.exp(-3.6 * t) * bloom
                            + .30 * swell * 2 * bloom
                            + droplets(t, dropStart, dropFreq, pitch);
                    break;
                }
                case WAVE: {
                    double surge = Math.pow(Math.sin(Math.PI * Math.min(1, p * 1.05)), .8);
                    value = (swell * 2.2 + .22 * Math.sin(2 * Math.PI * pitch * (74 - 24 * p) * t)
                            + .12 * Math.sin(2 * Math.PI * pitch * (150 + 60 * p) * t)) * surge;
                    break;
                }
                case PING: {
                    phase += 2 * Math.PI * pitch * (1420 - 380 * smooth(p * 1.1)) / RATE;
                    double ping = .70 * Math.sin(phase) * Math.exp(-3.1 * t) + .14 * Math.sin(2.3 * phase) * Math.exp(-4.5 * t);
                    double echoes = 0;
                    double[] echoAt = {.42, .84, 1.26};
                    for (int k = 0; k < echoAt.length; k++) {
                        double age = t - echoAt[k];
                        if (age > 0) {
                            echoes += (.34 / (k + 1.6)) * Math.sin(2 * Math.PI * pitch * (1180 - k * 90) * age) * Math.exp(-4.4 * age);
                        }
                    }
                    value = ping + echoes + .10 * hiss * Math.exp(-6 * t);
                    break;
                }
                case WIN: {
                    double[] notes = {523.25, 659.25, 783.99, 1046.5, 1318.5};
                    double arpeggio = 0;
                    for (int note = 0; note < notes.length; note++) {
                        double start = note * .17;
                        double age = t - start;
                        if (age < 0) {
                            continue;
                        }
                        double bell = Math.sin(2 * Math.PI * pitch * notes[note] * age)
                                + .34 * Math.sin(4 * Math.PI * pitch * notes[note] * age)
                                + .16 * Math.sin(6 * Math.PI * pitch * notes[note] * age);
                        arpeggio += bell * Math.exp(-2.3 * age) * (1 - .12 * note);
                    }
                    double horn = (.30 * Math.sin(2 * Math.PI * pitch * 261.6 * t)
                            + .26 * Math.sin(2 * Math.PI * pitch * 329.6 * t)
                            + .22 * Math.sin(2 * Math.PI * pitch * 392 * t)
                            + .14 * Math.sin(2 * Math.PI * pitch * 523.25 * t)) * smooth(t / .45) * (1 - p * .5);
                    double timpani = .34 * Math.sin(2 * Math.PI * pitch * 98 * t) * Math.exp(-3.4 * t)
                            + (t < .62 ? 0 : .30 * Math.sin(2 * Math.PI * pitch * 98 * (t - .62)) * Math.exp(-3.4 * (t - .62)));
                    double shimmer = .12 * hiss * (.6 + .4 * Math.sin(2 * Math.PI * 5.5 * t)) * smooth((t - .3) / .5) * (1 - p);
                    value = .40 * arpeggio + horn + timpani + shimmer;
                    break;
                }
                case LOSE: {
                    phase += 2 * Math.PI * pitch * (392 - 96 * smooth(p)) / RATE;
                    phase2 += 2 * Math.PI * pitch * (311.1 - 70 * smooth(p)) / RATE;
                    double pad = (.26 * Math.sin(2 * Math.PI * pitch * 98 * t)
                            + .20 * Math.sin(2 * Math.PI * pitch * 130.8 * t)
                            + .12 * Math.sin(2 * Math.PI * pitch * 155.6 * t)) * smooth(t / .6) * (1 - p * .45);
                    double foghorn = (.22 * Math.sin(2 * Math.PI * pitch * 110 * t)
                            + .16 * Math.sin(2 * Math.PI * pitch * 145.8 * t)) * smooth((t - .35) / .8) * (1 - p * .3);
                    value = .40 * Math.sin(phase) * (1 - p * .3)
                            + .34 * Math.sin(phase2) * (1 - p * .35)
                            + pad
                            + foghorn
                            + .30 * rumble * 2.6 * smooth((t - .2) / .8) * (1 - p);
                    break;
                }
                default:
                    value = 0;
            }
            dry[i] = softLimit(value) * window(t, duration) * gain;
        }
        double[] wet = room(dry, tail(sound), damping(sound));
        double mix = room(sound);
        byte[] bytes = new byte[wet.length * 2];
        for (int i = 0; i < wet.length; i++) {
            double release = smooth((wet.length - i) / (RATE * .075));
            double value = softLimit((i < dry.length ? dry[i] : 0) + mix * wet[i]) * release;
            short sample = (short) (Math.max(-1, Math.min(1, value)) * 9200);
            bytes[i * 2] = (byte) sample;
            bytes[i * 2 + 1] = (byte) (sample >> 8);
        }
        return bytes;
    }

    private static double droplets(double t, double[] start, double[] freq, double pitch) {
        double sum = 0;
        for (int k = 0; k < start.length; k++) {
            double age = t - start[k];
            if (age > 0 && age < .16) {
                sum += .16 * Math.sin(2 * Math.PI * pitch * freq[k] * age) * Math.exp(-32 * age) * Math.sin(Math.PI * age / .16);
            }
        }
        return sum;
    }

    private static double[] room(double[] dry, double tailSeconds, double damping) {
        int tail = Math.max(1, (int) (RATE * tailSeconds));
        double[] wet = new double[dry.length + tail];
        int[] delays = {(int) (RATE * .0297), (int) (RATE * .0371), (int) (RATE * .0411), (int) (RATE * .0437)};
        double[] feedback = {.805, .795, .784, .773};
        for (int comb = 0; comb < delays.length; comb++) {
            int delay = Math.max(1, delays[comb]);
            double[] line = new double[delay];
            double damped = 0;
            int at = 0;
            for (int i = 0; i < wet.length; i++) {
                double delayed = line[at];
                damped += damping * (delayed - damped);
                line[at] = (i < dry.length ? dry[i] * .25 : 0) + damped * feedback[comb];
                at = at + 1 == delay ? 0 : at + 1;
                wet[i] += delayed * .25;
            }
        }
        int[] spreads = {(int) (RATE * .0051), (int) (RATE * .0017)};
        for (int stage = 0; stage < spreads.length; stage++) {
            int delay = Math.max(1, spreads[stage]);
            double[] line = new double[delay];
            double scale = .5;
            int at = 0;
            for (int i = 0; i < wet.length; i++) {
                double input = wet[i];
                double delayed = line[at];
                double output = delayed - scale * input;
                line[at] = input + scale * output;
                at = at + 1 == delay ? 0 : at + 1;
                wet[i] = output;
            }
        }
        return wet;
    }

    private static double tail(Sound sound) {
        switch (sound) {
            case CLICK: return .07;
            case SELECT: return .10;
            case TURN: return .45;
            case LAUNCH: return .70;
            case MISS: return .30;
            case HIT: return .55;
            case SUNK: return 1.20;
            case SPLASH: return .45;
            case WAVE: return .50;
            case PING: return 1;
            case WIN: return .95;
            default: return 1.10;
        }
    }

    private static double damping(Sound sound) {
        switch (sound) {
            case CLICK: case SELECT: return .55;
            case LOSE: return .22;
            case SUNK: return .26;
            case WAVE: return .30;
            case PING: return .32;
            case WIN: return .34;
            case TURN: case HIT: return .35;
            default: return .40;
        }
    }

    private static double room(Sound sound) {
        switch (sound) {
            case SUNK: case LOSE: return .30;
            case WIN: return .28;
            case LAUNCH: case HIT: case PING: return .26;
            case TURN: return .20;
            case WAVE: return .18;
            case MISS: case SPLASH: return .16;
            default: return .10;
        }
    }

    private static double duration(Sound sound) {
        switch (sound) {
            case CLICK: return .14;
            case SELECT: return .24;
            case TURN: return .70;
            case LAUNCH: return .80;
            case MISS: return .60;
            case HIT: return .68;
            case SUNK: return 1.75;
            case SPLASH: return .95;
            case WAVE: return 1.40;
            case PING: return 1.85;
            case WIN: return 2.30;
            default: return 2.10;
        }
    }

    private static double window(double t, double duration) {
        double attack = smooth(t / .020);
        double release = smooth((duration - t) / .055);
        return attack * release;
    }

    private static double smooth(double x) {
        double value = x < 0 ? 0 : x > 1 ? 1 : x;
        return value * value * (3 - 2 * value);
    }

    private static double softLimit(double value) {
        return value / (1 + Math.abs(value) * .35);
    }

    public static void battle(boolean active) {
        inBattle = active;
        if (active && enabled) {
            WORK.execute(GameAudio::startAmbience);
        } else {
            WORK.execute(() -> stopAmbience(true));
        }
    }

    private static synchronized void stopAmbience(boolean fade) {
        Clip clip = ambience;
        if (clip == null) {
            return;
        }
        ambience = null;
        if (fade) {
            ramp(clip, gainOf(clip), -80f, 420, true);
        } else {
            closeQuietly(clip);
        }
    }

    private static synchronized void startAmbience() {
        if (!enabled || !inBattle || ambience != null) {
            return;
        }
        try {
            byte[] pcm = surfLoop(masterVolume * seaVolume);
            Clip clip = AudioSystem.getClip();
            clip.open(format(), pcm, 0, pcm.length);
            ambience = clip;
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            FloatControl control = gain(clip);
            if (control != null) {
                control.setValue(Math.max(control.getMinimum(), -80f));
            }
            clip.start();
            ramp(clip, -80f, gainOf(clip), 700, false);
        } catch (Exception ignored) {
        }
    }

    private static void applyAmbienceGain() {
        Clip clip = ambience;
        if (clip == null) {
            return;
        }
        final float target = enabled ? gainOf(clip) : -80f;
        WORK.execute(() -> ramp(clip, gainOf(clip), target, 260, false));
    }

    private static void duckAmbience(int millis, double factor) {
        Clip clip = ambience;
        if (clip == null) {
            return;
        }
        final float room = gainOf(clip);
        final float ducked = Math.max(-80f, room + (float) (20 * Math.log10(Math.max(.05, factor))));
        WORK.execute(() -> {
            ramp(clip, room, ducked, 140, false);
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            ramp(clip, ducked, room, 420, false);
        });
    }

    private static float gainOf(Clip clip) {
        FloatControl control = gain(clip);
        if (control == null) {
            return 0f;
        }
        return Math.max(control.getMinimum(), control.getValue());
    }

    private static FloatControl gain(Clip clip) {
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return null;
        }
        try {
            return (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void ramp(Clip clip, float from, float to, int millis, boolean closeAfter) {
        try {
            FloatControl control = gain(clip);
            if (control == null) {
                if (closeAfter) {
                    closeQuietly(clip);
                }
                return;
            }
            int steps = Math.max(4, millis / 16);
            for (int step = 1; step <= steps; step++) {
                if (!clip.isOpen()) {
                    return;
                }
                double ratio = step / (double) steps;
                control.setValue((float) (from + (to - from) * smooth(ratio)));
                Thread.sleep(Math.max(1, millis / steps));
            }
        } catch (Exception ignored) {
        } finally {
            if (closeAfter) {
                closeQuietly(clip);
            }
        }
    }

    private static void closeQuietly(Clip clip) {
        try {
            clip.stop();
        } catch (Exception ignored) {
        }
        try {
            clip.close();
        } catch (Exception ignored) {
        }
    }

    public static byte[] surfLoop() {
        return surfLoop(.6);
    }

    public static byte[] surfLoop(double gain) {
        int frames = RATE * 4;
        int seam = RATE / 2;
        short[] wave = new short[frames + seam];
        Random noise = new Random(9187);
        double low = 0, deep = 0, foam = 0, phase = 0, drone = 0, breeze = 0;
        for (int i = 0; i < wave.length; i++) {
            double t = i / (double) RATE;
            double p = (t % 4.0) / 4.0;
            double surge = Math.pow(.5 + .5 * Math.sin(2 * Math.PI * 2 * p - 1.2), 1.4);
            double grain = noise.nextDouble() * 2 - 1;
            low += .05 * (grain - low);
            deep += .010 * (grain - deep);
            foam += .30 * (grain - foam);
            breeze += .12 * (grain - breeze);
            double rumble = (low - deep) * 3.0;
            phase += 2 * Math.PI * (44 + 6 * Math.sin(2 * Math.PI * p)) / RATE;
            drone += 2 * Math.PI * (55 + 3 * Math.sin(2 * Math.PI * .5 * p)) / RATE;
            double hiss = (grain - foam) * (.30 + .40 * surge) * .55;
            double wind = breeze * (.22 + .34 * (.5 + .5 * Math.sin(2 * Math.PI * .5 * p)));
            double lap = .10 * Math.sin(2 * Math.PI * 118 * t) * Math.pow(surge, 3);
            double gull = gullCry(p, t, .60, .70, 1430, 420) + gullCry(p, t, .24, .33, 1180, 360);
            double horn = p > .78 && p < .93 ? .06 * Math.sin(2 * Math.PI * 138.6 * t) * Math.sin(Math.PI * (p - .78) / .15) : 0;
            double value = (rumble * .75 + hiss * (.35 + .65 * surge) + Math.sin(phase) * .30 + Math.sin(drone) * .10 + wind + lap) * (.44 + .56 * surge);
            value = softLimit((value + gull + horn) * gain);
            wave[i] = (short) (Math.max(-1, Math.min(1, value)) * 7600);
        }
        for (int i = 0; i < seam; i++) {
            double mix = smooth(i / (double) seam);
            wave[i] = (short) (wave[i] * mix + wave[frames + i] * (1 - mix));
        }
        byte[] bytes = new byte[frames * 2];
        for (int i = 0; i < frames; i++) {
            bytes[i * 2] = (byte) wave[i];
            bytes[i * 2 + 1] = (byte) (wave[i] >> 8);
        }
        return bytes;
    }

    private static double gullCry(double p, double t, double from, double to, double base, double fall) {
        if (p <= from || p >= to) {
            return 0;
        }
        double position = (p - from) / (to - from);
        return .10 * Math.sin(2 * Math.PI * (base - fall * position) * t) * Math.sin(Math.PI * position);
    }
}
