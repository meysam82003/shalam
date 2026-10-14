package android.media;

/** Beeps generated in the page (oscillator), for the timer ticks and alerts. */
public class ToneGenerator {
    public static final int TONE_PROP_BEEP = 24, TONE_PROP_BEEP2 = 28, TONE_PROP_ACK = 25, TONE_PROP_NACK = 26, TONE_PROP_PROMPT = 27,
        TONE_CDMA_ALERT_CALL_GUARD = 93, TONE_CDMA_PIP = 89, TONE_SUP_PIP = 33, TONE_DTMF_0 = 0, TONE_CDMA_HIGH_L = 40, TONE_CDMA_ONE_MIN_BEEP = 88,
        TONE_SUP_RADIO_ACK = 23, TONE_CDMA_ABBR_ALERT = 97, TONE_CDMA_EMERGENCY_RINGBACK = 92, TONE_CDMA_HIGH_PBX_SLS = 70;
    public static final int MAX_VOLUME = 100, MIN_VOLUME = 0;
    private final int volume;

    public ToneGenerator(int streamType, int volume) { this.volume = volume; }

    public boolean startTone(int toneType) { return startTone(toneType, -1); }

    public boolean startTone(int toneType, int durationMs) { divan.web.Sound.tone(toneType, durationMs, volume / 100f); return true; }

    public void stopTone() { divan.web.Sound.stopTone(); }

    public void release() {}
}
