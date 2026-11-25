package com.chizui.tekken6;

import org.libsdl.app.SDLActivity;

/**
 * The game activity. SDLActivity loads the native libraries, starts the
 * SDL main thread (SDL_main in libmain.so) and hosts the game surface.
 */
public class Tekken6Activity extends SDLActivity {

    @Override
    protected String[] getLibraries() {
        // SDL is statically linked into librexruntime.so (there is no
        // separate libSDL3.so), so load the runtime first, then the game.
        return new String[] {
            "rexruntime",
            "main"
        };
    }

    @Override
    protected String[] getArguments() {
        // Forward the launcher's paths as cvar args (--name=value, CLI11).
        String gameData = getIntent().getStringExtra("game_data_root");
        String userData = getIntent().getStringExtra("user_data_root");
        java.util.ArrayList<String> args = new java.util.ArrayList<>();
        if (gameData != null) {
            args.add("--game_data_root=" + gameData);
        }
        if (userData != null) {
            args.add("--user_data_root=" + userData);
        }
        return args.toArray(new String[0]);
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        // The internal resolution is read from the environment by the game's
        // hooks (TEKKEN6_INTERNAL_RESOLUTION). Set it before SDL starts.
        String res = getIntent().getStringExtra("internal_resolution");
        if (res != null && !res.isEmpty()) {
            Tekken6Helper.setEnv("TEKKEN6_INTERNAL_RESOLUTION", res);
            // The offscreen texture follows the same preset.
            Tekken6Helper.setEnv("TEKKEN6_OFFSCREEN_RESOLUTION",
                    "640x360".equals(res) ? "512x512" : "1024x1024");
        }
        super.onCreate(savedInstanceState);
    }
}
