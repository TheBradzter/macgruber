package com.thebradzter.macgruber;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQUEST_PICK_CLIP = 392;
    private static final String PREFS = "custom_clips";
    private static final String STATE_PENDING = "pending_sound";

    /** One soundboard button. The key is stable and is used to store a custom clip. */
    private static final class Sound {
        final String key;
        final String label;
        final int resId;

        Sound(String key, String label, int resId) {
            this.key = key;
            this.label = label;
            this.resId = resId;
        }
    }

    private static final Sound[] SOUNDS = {
            new Sound("macgruber", "MACGRUBER!", R.raw.snd_macgruber),
            new Sound("kfbr392", "KFBR392", R.raw.snd_kfbr392),
            new Sound("countdown", "3… 2… 1… BOOM", R.raw.snd_countdown),
            new Sound("boom", "BIG BOOM", R.raw.snd_boom),
            new Sound("paperclip", "PAPERCLIP & GUM", R.raw.snd_paperclip),
            new Sound("celery", "CELERY STALK", R.raw.snd_celery),
            new Sound("mullet", "MULLET POWER", R.raw.snd_mullet),
            new Sound("seal", "NAVY SEAL… ETC", R.raw.snd_seal),
            new Sound("lets_do_this", "LET'S DO THIS!", R.raw.snd_lets_do_this),
            new Sound("high_five", "HIGH FIVE?", R.raw.snd_high_five),
            new Sound("nooo", "SLOW-MO NOOO", R.raw.snd_nooo),
            new Sound("ticking", "TICKING BOMB", R.raw.snd_ticking),
            new Sound("rocket", "ROCKET LAUNCHER", R.raw.snd_rocket),
            new Sound("gunshot", "PEW PEW PEW", R.raw.snd_gunshot),
            new Sound("helicopter", "CHOPPER", R.raw.snd_helicopter),
            new Sound("synth", "80s SYNTH STAB", R.raw.snd_synth),
            new Sound("synth_arp", "MONTAGE SYNTH", R.raw.snd_synth_arp),
            new Sound("guitar", "POWER RIFF", R.raw.snd_guitar),
            new Sound("airhorn", "AIR HORN", R.raw.snd_airhorn),
            new Sound("sad", "SAD TROMBONE", R.raw.snd_sad),
    };

    private final Button[] buttons = new Button[SOUNDS.length];
    private SharedPreferences prefs;
    private MediaPlayer player;
    private int playingIndex = -1;
    private int pendingIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (savedInstanceState != null) {
            pendingIndex = savedInstanceState.getInt(STATE_PENDING, -1);
        }

        applyWindowInsets();
        buildGrid();
        findViewById(R.id.stop).setOnClickListener(v -> stopPlayback());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_PENDING, pendingIndex);
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopPlayback();
    }

    // ---------------------------------------------------------------- layout

    /**
     * Android 15+ draws apps edge to edge, so pad the bars to clear the status and
     * navigation bars. Older versions already lay the app out between the system bars.
     */
    private void applyWindowInsets() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return;
        }
        View root = findViewById(R.id.root);
        View top = findViewById(R.id.top_bar);
        View bottom = findViewById(R.id.bottom_bar);
        final int topPad = top.getPaddingTop();
        final int bottomPad = bottom.getPaddingBottom();

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, 0, bars.right, 0);
            top.setPadding(top.getPaddingLeft(), topPad + bars.top, top.getPaddingRight(), top.getPaddingBottom());
            bottom.setPadding(bottom.getPaddingLeft(), bottom.getPaddingTop(), bottom.getPaddingRight(), bottomPad + bars.bottom);
            return insets;
        });
    }

    private void buildGrid() {
        GridLayout grid = findViewById(R.id.grid);
        int margin = dp(6);
        for (int i = 0; i < SOUNDS.length; i++) {
            final int index = i;
            Button b = new Button(this);
            b.setAllCaps(false);
            b.setTextColor(Color.WHITE);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            b.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
            b.setShadowLayer(4f, 2f, 2f, 0xAA000000);
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(8), dp(8), dp(8), dp(8));
            b.setStateListAnimator(null);
            b.setBackgroundResource(R.drawable.tile);
            b.setOnClickListener(v -> play(index));
            b.setOnLongClickListener(v -> {
                showClipDialog(index);
                return true;
            });

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.height = dp(96);
            lp.setMargins(margin, margin, margin, margin);
            grid.addView(b, lp);
            buttons[i] = b;
            refreshLabel(i);
        }
    }

    private void refreshLabel(int index) {
        Sound s = SOUNDS[index];
        if (customUri(s) != null) {
            buttons[index].setText(s.label + "\n" + getString(R.string.your_clip));
        } else {
            buttons[index].setText(s.label);
        }
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    // -------------------------------------------------------------- playback

    private void play(int index) {
        stopPlayback();
        Sound s = SOUNDS[index];
        MediaPlayer mp = null;

        Uri custom = customUri(s);
        if (custom != null) {
            mp = createPlayer(custom);
            if (mp == null) {
                Toast.makeText(this, R.string.clip_failed, Toast.LENGTH_SHORT).show();
            }
        }
        if (mp == null) {
            mp = MediaPlayer.create(this, s.resId);
        }
        if (mp == null) {
            return;
        }

        player = mp;
        playingIndex = index;
        buttons[index].setBackgroundResource(R.drawable.tile_playing);
        buttons[index].setTextColor(Color.BLACK);
        buttons[index].setShadowLayer(0f, 0f, 0f, 0);
        mp.setOnCompletionListener(p -> {
            if (p == player) {
                stopPlayback();
            }
        });
        mp.setOnErrorListener((p, what, extra) -> {
            if (p == player) {
                stopPlayback();
            }
            return true;
        });
        mp.start();
    }

    private MediaPlayer createPlayer(Uri uri) {
        try {
            return MediaPlayer.create(this, uri);
        } catch (RuntimeException e) {
            // The file was moved or deleted, or permission to read it was lost.
            return null;
        }
    }

    private void stopPlayback() {
        if (player != null) {
            try {
                player.stop();
            } catch (IllegalStateException ignored) {
                // Already stopped.
            }
            player.release();
            player = null;
        }
        if (playingIndex >= 0) {
            Button b = buttons[playingIndex];
            b.setBackgroundResource(R.drawable.tile);
            b.setTextColor(Color.WHITE);
            b.setShadowLayer(4f, 2f, 2f, 0xAA000000);
            playingIndex = -1;
        }
    }

    // ---------------------------------------------------------- custom clips

    private Uri customUri(Sound s) {
        String value = prefs.getString(s.key, null);
        return value == null ? null : Uri.parse(value);
    }

    private void showClipDialog(int index) {
        Sound s = SOUNDS[index];
        boolean hasCustom = customUri(s) != null;

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_clip);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(480));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView title = dialog.findViewById(R.id.dialog_title);
        TextView body = dialog.findViewById(R.id.dialog_body);
        title.setText(getString(R.string.dialog_title, s.label));
        body.setText(hasCustom ? R.string.dialog_body_custom : R.string.dialog_body_default);

        Button reset = dialog.findViewById(R.id.btn_reset);
        reset.setVisibility(hasCustom ? View.VISIBLE : View.GONE);
        reset.setOnClickListener(v -> {
            clearCustom(index);
            dialog.dismiss();
        });
        dialog.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.btn_choose).setOnClickListener(v -> {
            dialog.dismiss();
            pickClip(index);
        });
        dialog.show();
    }

    private void pickClip(int index) {
        pendingIndex = index;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        try {
            startActivityForResult(intent, REQUEST_PICK_CLIP);
        } catch (ActivityNotFoundException e) {
            pendingIndex = -1;
            Toast.makeText(this, R.string.no_picker, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_CLIP) {
            return;
        }
        int index = pendingIndex;
        pendingIndex = -1;
        if (resultCode != RESULT_OK || data == null || data.getData() == null
                || index < 0 || index >= SOUNDS.length) {
            return;
        }

        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Some providers don't offer persistable access; the clip still works until reboot.
        }
        releaseOldPermission(SOUNDS[index], uri);
        prefs.edit().putString(SOUNDS[index].key, uri.toString()).apply();
        refreshLabel(index);
        Toast.makeText(this, R.string.clip_saved, Toast.LENGTH_SHORT).show();
    }

    private void clearCustom(int index) {
        if (playingIndex == index) {
            stopPlayback();
        }
        releaseOldPermission(SOUNDS[index], null);
        prefs.edit().remove(SOUNDS[index].key).apply();
        refreshLabel(index);
    }

    /** Drops our read access to a button's previous clip, unless another button still uses it. */
    private void releaseOldPermission(Sound sound, Uri replacement) {
        Uri old = customUri(sound);
        if (old == null || old.equals(replacement)) {
            return;
        }
        for (Sound other : SOUNDS) {
            if (other != sound && old.equals(customUri(other))) {
                return;
            }
        }
        try {
            getContentResolver().releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // We never held persistable access to it.
        }
    }
}
