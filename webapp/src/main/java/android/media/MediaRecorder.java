package android.media;

/** Voice recording through the browser microphone; the file is written when recording stops. */
public class MediaRecorder {
    public static final class AudioSource { public static final int DEFAULT = 0, MIC = 1, VOICE_RECOGNITION = 6; }
    public static final class OutputFormat { public static final int DEFAULT = 0, THREE_GPP = 1, MPEG_4 = 2, AAC_ADTS = 6, OGG = 11, WEBM = 9; }
    public static final class AudioEncoder { public static final int DEFAULT = 0, AMR_NB = 1, AAC = 3, HE_AAC = 4, OPUS = 7; }

    private String output;
    private int session = -1;

    public MediaRecorder() {}
    public MediaRecorder(android.content.Context context) {}
    public void setAudioSource(int source) {}
    public void setOutputFormat(int format) {}
    public void setAudioEncoder(int encoder) {}
    public void setAudioEncodingBitRate(int bitRate) {}
    public void setAudioSamplingRate(int samplingRate) {}
    public void setAudioChannels(int numChannels) {}
    public void setMaxDuration(int ms) {}
    public void setOutputFile(String path) { output = path; }
    public void setOutputFile(java.io.File file) { output = file.getAbsolutePath(); }

    public void prepare() throws java.io.IOException {
        if (!divan.web.Recorder.ready()) throw new java.io.IOException("microphone not available");
    }

    public void start() { session = divan.web.Recorder.start(output); if (session < 0) throw new IllegalStateException("start failed"); }
    public void stop() { if (session >= 0) divan.web.Recorder.stop(session, false); session = -1; }
    public void reset() { if (session >= 0) divan.web.Recorder.stop(session, true); session = -1; }
    public void release() { reset(); }
    public int getMaxAmplitude() { return session >= 0 ? divan.web.Recorder.amplitude() : 0; }
}
