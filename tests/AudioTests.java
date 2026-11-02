package tests;

import client.ui.GameAudio;

public final class AudioTests {
    private static void check(boolean condition, String message) { TestRunner.check(condition, message); }
    private static final int FULL_SCALE = 32767;

    public static void run() {
        for (GameAudio.Sound sound : GameAudio.Sound.values()) {
            byte[] pcm = GameAudio.synthesize(sound);
            check(pcm.length > 2000, sound + " carries a usable amount of PCM");
            double peak = 0, energy = 0, maxStep = 0;
            int previous = 0;
            for (int i = 0; i < pcm.length / 2; i++) {
                int sample = sample(pcm, i);
                peak = Math.max(peak, Math.abs(sample));
                energy += (double) sample * sample;
                if (i > 0) maxStep = Math.max(maxStep, Math.abs(sample - previous));
                previous = sample;
            }
            double rms = Math.sqrt(energy / (pcm.length / 2));
            int first = sample(pcm, 0), last = sample(pcm, pcm.length / 2 - 1);
            check(first == 0 && last == 0, sound + " fades in and out to silence, so it never clicks");
            check(peak < FULL_SCALE * 0.5, sound + " keeps headroom instead of clipping");
            check(rms > FULL_SCALE * 0.01, sound + " is audible");
            if (isTonal(sound)) check(maxStep < FULL_SCALE * 0.12, sound + " has no step between neighbouring samples");
        }
        byte[] surf = GameAudio.surfLoop(.6);
        int surfMaxStep = 0, surfPeak = 0, previous = 0;
        for (int i = 0; i < surf.length / 2; i++) {
            int sample = sample(surf, i);
            surfPeak = Math.max(surfPeak, Math.abs(sample));
            if (i > 0) surfMaxStep = Math.max(surfMaxStep, Math.abs(sample - previous));
            previous = sample;
        }
        int seamStep = Math.abs(sample(surf, 0) - sample(surf, surf.length / 2 - 1));
        check(seamStep <= Math.max(900, surfMaxStep), "ambience loop crosses its seam without a jump, step " + seamStep);
        check(surfPeak < FULL_SCALE * 0.4, "ambience keeps headroom instead of clipping");
        float master = GameAudio.getMasterVolume();
        GameAudio.setMasterVolume(2f); check(GameAudio.getMasterVolume() == 1f, "master level is clamped to 1");
        GameAudio.setMasterVolume(-1f); check(GameAudio.getMasterVolume() == 0f, "master level is clamped to 0");
        GameAudio.setMasterVolume(master);
        check(GameAudio.getSfxVolume() >= 0 && GameAudio.getSfxVolume() <= 1, "effects level stays in range");
        check(GameAudio.getAmbienceVolume() >= 0 && GameAudio.getAmbienceVolume() <= 1, "sea level stays in range");
        System.out.println("PASS audio: click-free envelopes, headroom without clipping, smooth tonal cues, seamless sea loop, clamped levels");
    }

    private static int sample(byte[] pcm, int index) {
        return (short) ((pcm[index * 2] & 0xff) | (pcm[index * 2 + 1] << 8));
    }

    private static boolean isTonal(GameAudio.Sound sound) {
        return sound != GameAudio.Sound.SPLASH && sound != GameAudio.Sound.MISS && sound != GameAudio.Sound.LAUNCH;
    }
}
