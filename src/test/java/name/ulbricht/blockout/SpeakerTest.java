package name.ulbricht.blockout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpeakerTest {

    @Test
    void everyEffectEndsWithAFullBufferOfSilence() {
        // Leaves only silence in the line buffer, even if a driver keeps looping it
        for (Speaker.Effect effect : Speaker.Effect.values()) {
            byte[] samples = Speaker.synthesize(effect);
            int tail = Speaker.BUFFER_FRAMES * 2;
            for (int i = samples.length - tail; i < samples.length; i++) {
                assertEquals(0, samples[i], effect + " byte " + i);
            }
            boolean sound = false;
            for (int i = 0; i < samples.length - tail; i++) sound |= samples[i] != 0;
            assertTrue(sound, effect + " is silent");
        }
    }
}
