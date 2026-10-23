package com.tanimsarker.lossless;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int BLUE = 0xFF3D8BFF, SILVER = 0xFFC9CED6, MUTE = 0xFF7D8590;
    private final List<File> files = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout listBox;
    private TextView title, quality, time;
    private SeekBar seek;
    private Button playBtn;
    private int idx = -1;
    private boolean dragging = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.BLACK);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(36), dp(18), dp(24));
        sv.addView(root);

        TextView logo = tv("L O S S L E S S", 20, SILVER);
        logo.setGravity(Gravity.CENTER);
        TextView sig = tv("TanimSarker", 11, MUTE);
        sig.setGravity(Gravity.CENTER);
        root.addView(logo); root.addView(sig);

        title = tv("Add music to begin", 17, SILVER);
        title.setPadding(0, dp(18), 0, 0);
        quality = tv(" ", 12, BLUE);
        root.addView(title); root.addView(quality);

        seek = new SeekBar(this);
        seek.setMax(1000);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {}
            @Override public void onStartTrackingTouch(SeekBar s) { dragging = true; }
            @Override public void onStopTrackingTouch(SeekBar s) {
                Engine.seek(s.getProgress() / 1000.0 * Engine.duration());
                dragging = false;
            }
        });
        time = tv("0:00 / 0:00", 12, MUTE);
        root.addView(seek); root.addView(time);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        Button open = btn("Add files", v -> pick());
        Button prev = btn("Prev", v -> step(-1));
        playBtn = btn("Play", v -> toggle());
        Button next = btn("Next", v -> step(1));
        row.addView(open); row.addView(prev); row.addView(playBtn); row.addView(next);
        root.addView(row);

        CheckBox eq = new CheckBox(this);
        eq.setText("Equalizer on (off = bypass, samples untouched)");
        eq.setTextColor(SILVER);
        eq.setOnCheckedChangeListener((c, on) -> Engine.eq(on));
        root.addView(eq);
        for (int i = 0; i < 10; i++) root.addView(slider(Engine.BAND_LABELS[i] + " Hz", 24, 12, i));
        root.addView(slider("Preamp", 18, 12, -1));

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(0, dp(16), 0, 0);
        root.addView(listBox);
        setContentView(sv);
        tick.run();
    }

    private View slider(String label, int max, int zero, int band) {
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = tv(label, 12, MUTE);
        t.setWidth(dp(80));
        SeekBar s = new SeekBar(this);
        s.setMax(max);
        s.setProgress(band >= 0 ? zero : 12);
        s.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar x, int p, boolean user) {
                if (!user) return;
                if (band >= 0) Engine.band(band, p - 12); else Engine.preamp(p - 12);
            }
            @Override public void onStartTrackingTouch(SeekBar x) {}
            @Override public void onStopTrackingTouch(SeekBar x) {}
        });
        l.addView(t); l.addView(s);
        return l;
    }

    private void pick() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, 1);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != 1 || res != RESULT_OK || data == null) return;
        final List<Uri> uris = new ArrayList<>();
        ClipData cd = data.getClipData();
        if (cd != null) for (int i = 0; i < cd.getItemCount(); i++) uris.add(cd.getItemAt(i).getUri());
        else if (data.getData() != null) uris.add(data.getData());
        new Thread(() -> {
            for (Uri u : uris) {
                String seg = u.getLastPathSegment();
                final String name = (seg == null ? "track" : seg).replaceAll("[^A-Za-z0-9._-]", "_");
                final File f = new File(getCacheDir(), System.nanoTime() + "_" + name);
                try (InputStream in = getContentResolver().openInputStream(u);
                     OutputStream out = new FileOutputStream(f)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    runOnUiThread(() -> add(f, name));
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, "Could not read " + name, Toast.LENGTH_LONG).show());
                }
            }
        }).start();
    }

    private void add(File f, String name) {
        files.add(f); names.add(name);
        final int i = files.size() - 1;
        TextView row = tv(name, 14, SILVER);
        row.setPadding(0, dp(10), 0, dp(10));
        row.setOnClickListener(v -> load(i));
        listBox.addView(row);
        if (idx < 0) load(i);
    }

    private void load(int i) {
        if (i < 0 || i >= files.size()) return;
        if (Engine.open(files.get(i).getAbsolutePath()) != 0) {
            Toast.makeText(this, "Unsupported or unreadable file", Toast.LENGTH_LONG).show();
            return;
        }
        idx = i;
        title.setText(names.get(i));
        quality.setText(Engine.describe());
        Engine.play();
        playBtn.setText("Pause");
    }

    private void toggle() {
        if (idx < 0) return;
        if (Engine.playing() == 1) { Engine.pause(); playBtn.setText("Play"); }
        else { Engine.play(); playBtn.setText("Pause"); }
    }

    private void step(int d) { if (!files.isEmpty()) load((idx + d + files.size()) % files.size()); }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (idx >= 0) {
                double p = Engine.position(), d = Engine.duration();
                if (!dragging && d > 0) seek.setProgress((int) (p / d * 1000));
                time.setText(Engine.fmt(p) + " / " + Engine.fmt(d));
                if (Engine.finished() == 1) {
                    if (idx + 1 < files.size()) load(idx + 1); else playBtn.setText("Play");
                }
            }
            handler.postDelayed(this, 250);
        }
    };

    @Override protected void onDestroy() { handler.removeCallbacks(tick); super.onDestroy(); }

    private TextView tv(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        return t;
    }
    private Button btn(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s); b.setOnClickListener(l);
        return b;
    }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
