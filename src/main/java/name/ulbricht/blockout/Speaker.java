package name.ulbricht.blockout;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/// PC speaker style square wave sounds, synthesized at runtime and played one after the
/// other on a background thread.
final class Speaker {

    enum Effect {
        // Pairs of frequency (Hz, 0 = pause) and duration (ms)
        HIT(140, 35, 0, 10, 100, 45),
        LAYER(523, 45, 659, 45, 784, 45, 1047, 90),
        EMPTY_PIT(523, 50, 659, 50, 784, 50, 1047, 50, 784, 50, 1047, 50, 1319, 50, 1568, 220),
        LEVEL_UP(784, 90, 0, 30, 1047, 90, 0, 30, 1319, 160),
        GAME_OVER(392, 140, 330, 140, 262, 140, 196, 320),
        MENU(1200, 12),
        WELL_DONE(523, 110, 659, 110, 784, 110, 0, 40, 1047, 300);

        final int[] notes;

        Effect(int... notes) {
            this.notes = notes;
        }
    }

    private static final float RATE = 22050;
    private static final short AMPLITUDE = 7000;
    /// Line buffer and trailing silence: 100 ms.
    static final int BUFFER_FRAMES = (int) RATE / 10;

    private final BlockingQueue<Effect> queue = new LinkedBlockingQueue<>();
    private volatile boolean enabled;

    Speaker(boolean enabled) {
        this.enabled = enabled;
        Thread.ofPlatform().name("speaker").daemon().start(this::run);
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) queue.clear();
    }

    void play(Effect effect) {
        if (enabled) queue.offer(effect);
    }

    void play(Game.Sound sound) {
        play(switch (sound) {
            case HIT -> Effect.HIT;
            case LAYER -> Effect.LAYER;
            case EMPTY_PIT -> Effect.EMPTY_PIT;
            case LEVEL_UP -> Effect.LEVEL_UP;
            case GAME_OVER -> Effect.GAME_OVER;
        });
    }

    private void run() {
        AudioFormat format = new AudioFormat(RATE, 16, 1, true, false);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format, BUFFER_FRAMES * 2);
            while (true) {
                byte[] samples = synthesize(queue.take());
                if (!enabled) continue;
                // Run the line only while there is something to play: a started line that runs
                // dry replays stale buffer contents on DirectSound, every 2 s for as long as it runs
                line.start();
                line.write(samples, 0, samples.length);
                line.drain();
                line.stop();
            }
        } catch (LineUnavailableException | IllegalArgumentException e) {
            System.err.println("No sound: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /// 16 bit little endian samples of the effect, followed by one buffer of silence so the
    /// line buffer holds nothing but silence once the effect has been played.
    static byte[] synthesize(Effect effect) {
        int[] n = effect.notes;
        int total = BUFFER_FRAMES;
        for (int i = 1; i < n.length; i += 2) total += (int) (RATE * n[i] / 1000);
        byte[] out = new byte[total * 2];
        int pos = 0;
        for (int i = 0; i < n.length; i += 2) {
            int len = (int) (RATE * n[i + 1] / 1000);
            double period = n[i] > 0 ? RATE / n[i] : 0;
            for (int s = 0; s < len; s++, pos += 2) {
                short v = period == 0 ? 0 : (s % period) < period / 2 ? AMPLITUDE : (short) -AMPLITUDE;
                out[pos] = (byte) v;
                out[pos + 1] = (byte) (v >> 8);
            }
        }
        return out;
    }
}
