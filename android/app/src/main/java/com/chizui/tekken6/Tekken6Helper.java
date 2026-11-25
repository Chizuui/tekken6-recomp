package com.chizui.tekken6;

/**
 * JNI bridge to the small native helper library (libtekken6helper.so).
 * The helper links the runtime (for the GDFX disc reader) but does not
 * start the game, so the launcher can use it before the game runs.
 */
public class Tekken6Helper {
    static {
        System.loadLibrary("tekken6helper");
    }

    public interface ProgressListener {
        void onProgress(float fraction, String currentFile);
    }

    /** setenv() for the game process (internal resolution, etc.). */
    public static native void setEnv(String key, String value);

    /**
     * Extracts an Xbox 360 (GDFX) ISO into destDir. Returns 0 on success,
     * non-zero on failure. Calls listener.onProgress(0..1, currentFile)
     * as files are written.
     */
    public static native int extractIso(String isoPath, String destDir, ProgressListener listener);
}
