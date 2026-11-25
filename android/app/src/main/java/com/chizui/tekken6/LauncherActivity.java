package com.chizui.tekken6;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * First screen: import the Tekken 6 ISO, pick a resolution, launch the
 * game, or export the log. All game data lives under the app's external
 * files dir (shared storage has room for a 7.8 GB game).
 */
public class LauncherActivity extends Activity {

    private static final int REQ_PICK_ISO = 1;
    private static final int REQ_SAVE_LOG = 2;

    private static final String PREFS = "tekken6";
    private static final String KEY_RESOLUTION = "resolution";

    private static final String[] RES_LABELS = {
        "Low 640x360 (fastest)",
        "Medium 768x432",
        "High 1024x576 (retail)"
    };
    private static final String[] RES_VALUES = { "640x360", "768x432", "1024x576" };

    private TextView statusText;
    private TextView progressText;
    private ProgressBar progressBar;
    private Button playButton;
    private Spinner resolutionSpinner;
    private SharedPreferences prefs;

    private File gameDir;
    private File userDataDir;
    private File isoFile;
    private File pendingLogFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        File base = getExternalFilesDir(null);
        if (base == null) {
            base = getFilesDir();
        }
        gameDir = new File(base, "Tekken6");
        userDataDir = new File(base, "tekken6-data");
        isoFile = new File(base, "tekken6.iso");

        statusText = findViewById(R.id.status_text);
        progressText = findViewById(R.id.progress_text);
        progressBar = findViewById(R.id.progress_bar);
        playButton = findViewById(R.id.play_button);
        resolutionSpinner = findViewById(R.id.resolution_spinner);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, RES_LABELS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        resolutionSpinner.setAdapter(adapter);
        resolutionSpinner.setSelection(indexOfResolution(getResolution()));
        resolutionSpinner.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(android.widget.AdapterView<?> parent,
                            View view, int position, long id) {
                        prefs.edit().putString(KEY_RESOLUTION, RES_VALUES[position]).apply();
                    }

                    @Override
                    public void onNothingSelected(android.widget.AdapterView<?> parent) {
                    }
                });

        findViewById(R.id.import_button).setOnClickListener(v -> pickIso());
        playButton.setOnClickListener(v -> play());
        findViewById(R.id.export_log_button).setOnClickListener(v -> exportLog());

        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private String getResolution() {
        // Low by default: the target phones are weak, so the first boot
        // should be playable. Raise it in the settings if it runs well.
        return prefs.getString(KEY_RESOLUTION, "640x360");
    }

    private int indexOfResolution(String value) {
        for (int i = 0; i < RES_VALUES.length; i++) {
            if (RES_VALUES[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }

    private boolean isGameReady() {
        return new File(gameDir, "Default.xex").isFile()
                || new File(gameDir, "default.xex").isFile();
    }

    private void refreshStatus() {
        if (isGameReady()) {
            statusText.setText("Game ready: " + gameDir.getAbsolutePath());
            playButton.setEnabled(true);
        } else {
            statusText.setText("No game data yet. Import your Tekken 6 ISO first.");
            playButton.setEnabled(false);
        }
    }

    private void play() {
        if (!isGameReady()) {
            Toast.makeText(this, "Import the ISO first.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, Tekken6Activity.class);
        intent.putExtra("game_data_root", gameDir.getAbsolutePath());
        intent.putExtra("user_data_root", userDataDir.getAbsolutePath());
        intent.putExtra("internal_resolution", getResolution());
        startActivity(intent);
    }

    private void pickIso() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_PICK_ISO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        if (requestCode == REQ_PICK_ISO) {
            importIso(data.getData());
        } else if (requestCode == REQ_SAVE_LOG) {
            saveLogTo(data.getData());
        }
    }

    private void importIso(Uri uri) {
        setBusy(true);
        new Thread(() -> {
            try {
                // 1. Copy the picked ISO into app storage (SAF gives a
                // content:// URI; the native extractor needs a real file).
                long total = querySize(uri);
                runOnUiThread(() -> progressText.setText("Copying ISO..."));
                copyUriToFile(uri, isoFile, total);

                // 2. Extract it (GDFX) into the game folder.
                runOnUiThread(() -> progressText.setText("Extracting ISO..."));
                int rc = Tekken6Helper.extractIso(
                        isoFile.getAbsolutePath(),
                        gameDir.getAbsolutePath(),
                        (fraction, currentFile) -> runOnUiThread(() -> {
                            progressBar.setProgress(Math.round(fraction * 100));
                            progressText.setText("Extracting: " + currentFile);
                        }));
                if (rc != 0) {
                    throw new RuntimeException("extract failed (code " + rc + ")");
                }

                // 3. The ISO copy is no longer needed; delete it to free ~8 GB.
                isoFile.delete();

                runOnUiThread(() -> {
                    setBusy(false);
                    progressText.setText("Import done.");
                    Toast.makeText(this, "Game imported.", Toast.LENGTH_SHORT).show();
                    refreshStatus();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    progressText.setText("Import failed: " + e.getMessage());
                    Toast.makeText(this, "Import failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private long querySize(Uri uri) {
        try (Cursor c = getContentResolver().query(
                uri, new String[] { OpenableColumns.SIZE }, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                return c.getLong(0);
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private void copyUriToFile(Uri uri, File dest, long total) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[1024 * 1024];
            long copied = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                copied += n;
                if (total > 0) {
                    final int pct = (int) (copied * 100 / total);
                    runOnUiThread(() -> {
                        progressBar.setProgress(pct);
                        progressText.setText("Copying ISO... " + pct + "%");
                    });
                }
            }
        }
    }

    private void exportLog() {
        File logDir = new File(userDataDir, "logs");
        File newest = null;
        if (logDir.isDirectory()) {
            File[] logs = logDir.listFiles((dir, name) -> name.endsWith(".log"));
            if (logs != null) {
                for (File f : logs) {
                    if (newest == null || f.lastModified() > newest.lastModified()) {
                        newest = f;
                    }
                }
            }
        }
        if (newest == null) {
            Toast.makeText(this, "No log yet - run the game first.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, newest.getName());
        pendingLogFile = newest;
        startActivityForResult(intent, REQ_SAVE_LOG);
    }

    private void saveLogTo(Uri uri) {
        final File src = pendingLogFile;
        pendingLogFile = null;
        if (src == null || !src.isFile()) {
            Toast.makeText(this, "Log file is gone.", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            try (InputStream in = new FileInputStream(src);
                 OutputStream out = getContentResolver().openOutputStream(uri)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                runOnUiThread(() -> Toast.makeText(this, "Log saved.",
                        Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void setBusy(boolean busy) {
        runOnUiThread(() -> {
            progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
            progressBar.setProgress(0);
            findViewById(R.id.import_button).setEnabled(!busy);
            playButton.setEnabled(!busy && isGameReady());
            findViewById(R.id.export_log_button).setEnabled(!busy);
        });
    }
}
