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

/**
 * Original synthesized sounds, mixed for a cinematic feel.
 *
 * <p>Every cue fades in and out with a raised-cosine window, so a buffer never starts or stops on a
 * step (that step is what is heard as a click). Noise is low-pass filtered instead of raw, and the
 * loud cues are layered (body + rumble + bubbles) rather than one bright buzz. Levels are split
 * into a master, an effects and a sea-ambience channel, all persisted; the ambience one is applied
 * live through the line's gain control when the mixer supports it.</p>
 *
 * <p>Audio runs on its own daemon threads and never blocks Swing or network processing.</p>
 */
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

    // ------------------------------------------------------------------ levels

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

    /** Back to the factory levels; used by the settings dialog. */
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

    // ------------------------------------------------------------------ playback

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
            byte[] data = synthesize(sound, gain);
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
            // machines without an audio device still play normally
        }
    }

    /** Every voice fades out instead of being cut, so muting is never a click. */
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

    /** PCM used by the playback path and by the audio tests. */
    public static byte[] synthesize(Sound sound) {
        return synthesize(sound, .9);
    }

    static byte[] synthesize(Sound sound, double gain) {
        double duration = duration(sound);
        int samples = (int) (RATE * duration);
        byte[] bytes = new byte[samples * 2];
        Random noise = new Random(73 + sound.ordinal());
        double phase = 0, phase2 = 0;
        double hiss = 0, rumble = 0, swell = 0;
        for (int i = 0; i < samples; i++) {
            double t = i / (double) RATE;
            double p = t / duration;
            double grain = noise.nextDouble() * 2 - 1;
            // filtered noise beds: a fast one for texture, a slow one for weight
            hiss += .35 * (grain - hiss);
            rumble += .05 * (grain - rumble);
            double value;
            switch (sound) {
                case CLICK:
                    phase += 2 * Math.PI * (860 - 240 * p) / RATE;
                    value = Math.sin(phase) * Math.exp(-18 * t) + .18 * hiss * Math.exp(-40 * t);
                    break;
                case SELECT:
                    phase += 2 * Math.PI * (940 + 320 * smooth(p * 1.6)) / RATE;
                    value = .8 * Math.sin(phase) * Math.exp(-7 * t) + .2 * Math.sin(2 * phase) * Math.exp(-11 * t);
                    break;
                case TURN:
                    // two soft chimes that overlap, so the change of note is a crossfade, not a step
                    double firstChime = Math.exp(-6 * t) * Math.sin(2 * Math.PI * 660 * t);
                    double secondChime = t < .18 ? 0 : Math.exp(-5.4 * (t - .18)) * Math.sin(2 * Math.PI * 990 * (t - .18)) * smooth((t - .18) / .10);
                    value = .72 * (firstChime + .85 * secondChime);
                    break;
                case LAUNCH:
                    phase += 2 * Math.PI * (170 + 1150 * smooth(p)) / RATE;
                    swell += .12 * (grain - swell);
                    value = .55 * Math.sin(phase) * (1 - p) + .55 * swell * 2.4 * Math.sin(Math.PI * Math.min(1, p * 1.15));
                    break;
                case MISS:
                    phase += 2 * Math.PI * (430 - 190 * smooth(p)) / RATE;
                    swell += .18 * (grain - swell);
                    value = .62 * Math.sin(phase) * Math.exp(-3.4 * t) + .42 * (grain - swell) * Math.exp(-2.6 * t) * (p < .3 ? 1 : .45);
                    break;
                case HIT:
                    phase += 2 * Math.PI * (128 - 58 * smooth(p * 1.4)) / RATE;
                    phase2 += 2 * Math.PI * 246 / RATE;
                    value = .78 * Math.sin(phase) * Math.exp(-6.2 * t)
                            + .30 * Math.sin(phase2) * Math.exp(-16 * t)
                            + .34 * hiss * Math.exp(-9 * t);
                    break;
                case SUNK:
                    phase += 2 * Math.PI * (96 - 50 * smooth(p * 1.2)) / RATE;
                    phase2 += 2 * Math.PI * (176 + 12 * Math.sin(2 * Math.PI * 3.1 * t)) / RATE;
                    swell += .05 * (grain - swell);
                    // deep boom, groaning hull, then a rising stream of bubbles
                    double bubbles = t < .55 ? 0 : Math.max(0, .30 * Math.sin(2 * Math.PI * (4 + 9 * p) * t) * Math.sin(Math.PI * Math.min(1, (p - .55) / .45)));
                    value = .85 * Math.sin(phase) * Math.exp(-2.1 * t)
                            + .30 * Math.sin(phase2) * Math.exp(-3.2 * t) * .6
                            + .70 * rumble * 3.2 * Math.exp(-1.7 * t)
                            + bubbles
                            + .22 * hiss * Math.exp(-5 * t);
                    break;
                case SPLASH:
                    swell += .16 * (grain - swell);
                    value = .40 * Math.sin(2 * Math.PI * (760 + 900 * smooth(p)) * t) * Math.exp(-3.6 * t)
                            + .85 * (grain - swell) * Math.sin(Math.PI * Math.min(1, p * 1.25)) * .9;
                    break;
                case WAVE:
                    swell += .035 * (grain - swell);
                    double surge = Math.pow(Math.sin(Math.PI * Math.min(1, p * 1.05)), .8);
                    value = (swell * 2.6 + .18 * Math.sin(2 * Math.PI * (74 - 24 * p) * t)) * surge;
                    break;
                case PING:
                    phase += 2 * Math.PI * (1320 - 330 * smooth(p * 1.1)) / RATE;
                    double echo = t < .42 ? 0 : .34 * Math.sin(2 * Math.PI * 1180 * (t - .42)) * Math.exp(-4.6 * (t - .42));
                    value = .74 * Math.sin(phase) * Math.exp(-2.7 * t) + echo + .14 * Math.sin(2.3 * phase) * Math.exp(-4 * t);
                    break;
                case WIN: {
                    // rising major arpeggio over a soft pad, with a shimmer on top
                    double[] notes = {523.25, 659.25, 783.99, 1046.5};
                    double arpeggio = 0;
                    for (int note = 0; note < notes.length; note++) {
                        double start = note * .19;
                        double age = t - start;
                        if (age < 0) {
                            continue;
                        }
                        double bell = Math.sin(2 * Math.PI * notes[note] * age) + .35 * Math.sin(4 * Math.PI * notes[note] * age);
                        arpeggio += bell * Math.exp(-2.5 * age) * (1 - .18 * note);
                    }
                    double pad = (.30 * Math.sin(2 * Math.PI * 261.6 * t)
                            + .26 * Math.sin(2 * Math.PI * 329.6 * t)
                            + .22 * Math.sin(2 * Math.PI * 392 * t)) * smooth(t / .5) * (1 - p * .55);
                    double shimmer = .10 * Math.sin(2 * Math.PI * 2093 * t)
                            * (0.6 + 0.4 * Math.sin(2 * Math.PI * 5.5 * t)) * smooth((t - .35) / .6) * (1 - p);
                    value = .42 * arpeggio + pad + shimmer + .10 * swell * 2.2 * smooth((t - .1) / .7) * (1 - p);
                    break;
                }
                case LOSE: {
                    // a low minor swell sliding down, like a hull giving up
                    phase += 2 * Math.PI * (392 - 96 * smooth(p)) / RATE;
                    phase2 += 2 * Math.PI * (311.1 - 70 * smooth(p)) / RATE;
                    double pad = (.26 * Math.sin(2 * Math.PI * 98 * t) + .20 * Math.sin(2 * Math.PI * 130.8 * t))
                            * smooth(t / .7) * (1 - p * .5);
                    value = .40 * Math.sin(phase) * (1 - p * .35)
                            + .34 * Math.sin(phase2) * (1 - p * .4)
                            + pad
                            + .34 * rumble * 2.6 * smooth((t - .2) / .8) * (1 - p);
                    break;
                }
                default:
                    value = 0;
            }
            value = softLimit(value);
            short sample = (short) (Math.max(-1, Math.min(1, value * window(t, duration) * gain)) * 9200);
            bytes[i * 2] = (byte) sample;
            bytes[i * 2 + 1] = (byte) (sample >> 8);
        }
        return bytes;
    }

    private static double duration(Sound sound) {
        switch (sound) {
            case CLICK: return .14;
            case SELECT: return .24;
            case TURN: return .62;
            case LAUNCH: return .62;
            case MISS: return .52;
            case HIT: return .58;
            case SUNK: return 1.50;
            case SPLASH: return .85;
            case WAVE: return 1.30;
            case PING: return 1.35;
            case WIN: return 1.90;
            default: return 1.70;
        }
    }

    /** Raised-cosine fades on both ends: the one rule that keeps every cue click-free. */
    private static double window(double t, double duration) {
        double attack = smooth(t / .020);
        double release = smooth((duration - t) / .055);
        return attack * release;
    }

    private static double smooth(double x) {
        double value = x < 0 ? 0 : x > 1 ? 1 : x;
        return value * value * (3 - 2 * value);
    }

    /** Gentle saturation instead of hard clipping, so loud layers stay musical. */
    private static double softLimit(double value) {
        return value / (1 + Math.abs(value) * .35);
    }

    // ------------------------------------------------------------------ sea ambience

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
            // no ambience on machines without an audio device
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

    /** Lowers the sea for a moment under a heavy impact, then brings it back: cinematic ducking. */
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

    /** Steps the line gain in small increments; without a gain control the clip is simply closed. */
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
            // a line that disappears mid-ramp is already gone
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
            // already stopped
        }
        try {
            clip.close();
        } catch (Exception ignored) {
            // already closed
        }
    }

    /** Four seconds of open sea: swell, foam hiss, a low drone and a distant gull. */
    public static byte[] surfLoop() {
        return surfLoop(.6);
    }

    /**
     * Synthesizes four seconds of sea plus half a second extra, then folds that extra tail back into
     * the head. The waveform therefore continues across the loop point instead of restarting, which
     * is what a plain loop would hear as a tick every four seconds.
     */
    public static byte[] surfLoop(double gain) {
        int frames = RATE * 4;
        int seam = RATE / 2;
        short[] wave = new short[frames + seam];
        Random noise = new Random(9187);
        double low = 0, deep = 0, foam = 0, phase = 0, drone = 0;
        for (int i = 0; i < wave.length; i++) {
            double t = i / (double) RATE;
            double p = (t % 4.0) / 4.0;
            double surge = Math.pow(.5 + .5 * Math.sin(2 * Math.PI * 2 * p - 1.2), 1.4);
            double grain = noise.nextDouble() * 2 - 1;
            low += .05 * (grain - low);
            deep += .010 * (grain - deep);
            foam += .30 * (grain - foam);
            double rumble = (low - deep) * 3.0;
            phase += 2 * Math.PI * (44 + 6 * Math.sin(2 * Math.PI * p)) / RATE;
            drone += 2 * Math.PI * (55 + 3 * Math.sin(2 * Math.PI * .5 * p)) / RATE;
            // long foam hiss that peaks with each swell instead of a constant static bed
            double hiss = (grain - foam) * (.30 + .40 * surge) * .55;
            double gull = gullCry(p, t);
            double horn = p > .78 && p < .93 ? .06 * Math.sin(2 * Math.PI * 138.6 * t) * Math.sin(Math.PI * (p - .78) / .15) : 0;
            double value = (rumble * .75 + hiss * (.35 + .65 * surge) + Math.sin(phase) * .30 + Math.sin(drone) * .10) * (.44 + .56 * surge);
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

    private static double gullCry(double p, double t) {
        if (p <= .60 || p >= .70) {
            return 0;
        }
        double position = (p - .60) / .10;
        return .10 * Math.sin(2 * Math.PI * (1430 - 420 * position) * t) * Math.sin(Math.PI * position);
    }
}
