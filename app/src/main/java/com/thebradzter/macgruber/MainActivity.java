package com.thebradzter.macgruber;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQUEST_PICK_CLIP = 392;
    private static final int REQUEST_PICK_PICTURE = 393;
    private static final String PREFS = "custom_clips";
    private static final String PICTURE_PREFIX = "picture_";
    private static final String STATE_PENDING = "pending_sound";
    private static final int PICTURE_MAX_PX = 480;

    /** One soundboard button. The key is stable and is used to store a custom clip. */
    private static final class Sound {
        final String key;
        final String emoji;
        final String label;
        final int resId;

        Sound(String key, String emoji, String label, int resId) {
            this.key = key;
            this.emoji = emoji;
            this.label = label;
            this.resId = resId;
        }
    }

    private static final Sound[] SOUNDS = {
            new Sound("macgruber", "\uD83D\uDCAA", "MACGRUBER!", R.raw.snd_macgruber),
            new Sound("kfbr392", "\uD83D\uDE97", "KFBR392", R.raw.snd_kfbr392),
            new Sound("countdown", "\u23F1\uFE0F", "3… 2… 1… BOOM", R.raw.snd_countdown),
            new Sound("boom", "\uD83D\uDCA5", "BIG BOOM", R.raw.snd_boom),
            new Sound("paperclip", "\uD83D\uDCCE", "PAPERCLIP & GUM", R.raw.snd_paperclip),
            new Sound("celery", "\uD83E\uDD6C", "CELERY STALK", R.raw.snd_celery),
            new Sound("mullet", "\uD83D\uDC87", "MULLET POWER", R.raw.snd_mullet),
            new Sound("seal", "\uD83C\uDF96\uFE0F", "NAVY SEAL… ETC", R.raw.snd_seal),
            new Sound("lets_do_this", "\uD83D\uDD25", "LET'S DO THIS!", R.raw.snd_lets_do_this),
            new Sound("high_five", "\u270B", "HIGH FIVE?", R.raw.snd_high_five),
            new Sound("nooo", "\uD83D\uDE31", "SLOW-MO NOOO", R.raw.snd_nooo),
            new Sound("ticking", "\uD83D\uDCA3", "TICKING BOMB", R.raw.snd_ticking),
            new Sound("rocket", "\uD83D\uDE80", "ROCKET LAUNCHER", R.raw.snd_rocket),
            new Sound("gunshot", "\uD83D\uDD2B", "PEW PEW PEW", R.raw.snd_gunshot),
            new Sound("helicopter", "\uD83D\uDE81", "CHOPPER", R.raw.snd_helicopter),
            new Sound("synth", "\uD83C\uDFB9", "80s SYNTH STAB", R.raw.snd_synth),
            new Sound("synth_arp", "\uD83D\uDD7A", "MONTAGE SYNTH", R.raw.snd_synth_arp),
            new Sound("guitar", "\uD83C\uDFB8", "POWER RIFF", R.raw.snd_guitar),
            new Sound("airhorn", "\uD83D\uDCE2", "AIR HORN", R.raw.snd_airhorn),
            new Sound("sad", "\uD83C\uDFBA", "SAD TROMBONE", R.raw.snd_sad),
    };

    /** Views that make up one tile. */
    private static final class Tile {
        FrameLayout frame;
        ImageView picture;
        View scrim;
        TextView badge;
    }

    private final Tile[] tiles = new Tile[SOUNDS.length];
    private final ExecutorService imageLoader = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        imageLoader.shutdownNow();
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
            Tile tile = new Tile();
            tiles[i] = tile;

            FrameLayout frame = new FrameLayout(this);
            frame.setBackgroundResource(R.drawable.tile);
            frame.setClipToOutline(true);
            frame.setClickable(true);
            frame.setOnClickListener(v -> play(index));
            frame.setOnLongClickListener(v -> {
                showCustomiseDialog(index);
                return true;
            });
            tile.frame = frame;

            ImageView picture = new ImageView(this);
            picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
            picture.setVisibility(View.GONE);
            frame.addView(picture, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            tile.picture = picture;

            View scrim = new View(this);
            scrim.setBackgroundResource(R.drawable.tile_scrim);
            scrim.setVisibility(View.GONE);
            frame.addView(scrim, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            tile.scrim = scrim;

            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER);
            column.setPadding(dp(6), dp(6), dp(6), dp(6));

            TextView emoji = new TextView(this);
            emoji.setText(SOUNDS[i].emoji);
            emoji.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
            emoji.setGravity(Gravity.CENTER);
            column.addView(emoji);

            TextView label = new TextView(this);
            label.setText(SOUNDS[i].label);
            label.setTextColor(Color.WHITE);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            label.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
            label.setShadowLayer(4f, 2f, 2f, 0xCC000000);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            column.addView(label);

            TextView badge = new TextView(this);
            badge.setText(R.string.your_clip);
            badge.setTextColor(0xFFFFC233);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            badge.setShadowLayer(3f, 1f, 1f, 0xCC000000);
            badge.setGravity(Gravity.CENTER);
            column.addView(badge);
            tile.badge = badge;

            frame.addView(column, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.height = dp(116);
            lp.setMargins(margin, margin, margin, margin);
            grid.addView(frame, lp);

            refreshTile(i);
        }
    }

    private void refreshTile(int index) {
        Sound s = SOUNDS[index];
        Tile tile = tiles[index];
        tile.badge.setVisibility(customUri(s) != null ? View.VISIBLE : View.GONE);

        Uri picture = pictureUri(s);
        if (picture == null) {
            tile.picture.setImageDrawable(null);
            tile.picture.setVisibility(View.GONE);
            tile.scrim.setVisibility(View.GONE);
            return;
        }
        imageLoader.execute(() -> {
            Bitmap bitmap = decodePicture(picture);
            mainHandler.post(() -> {
                if (isDestroyed() || !picture.equals(pictureUri(s))) {
                    return; // Changed again while loading.
                }
                boolean ok = bitmap != null;
                tile.picture.setImageBitmap(bitmap);
                tile.picture.setVisibility(ok ? View.VISIBLE : View.GONE);
                tile.scrim.setVisibility(ok ? View.VISIBLE : View.GONE);
            });
        });
    }

    /** Loads a picture scaled down to tile size, or null if it can't be read. */
    private Bitmap decodePicture(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    return null;
                }
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= PICTURE_MAX_PX
                    && bounds.outHeight / (sample * 2) >= PICTURE_MAX_PX) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                return in == null ? null : BitmapFactory.decodeStream(in, null, opts);
            }
        } catch (Exception | OutOfMemoryError e) {
            return null;
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
        tiles[index].frame.setForeground(getDrawable(R.drawable.tile_playing));
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
            tiles[playingIndex].frame.setForeground(null);
            playingIndex = -1;
        }
    }

    // ---------------------------------------------------------- custom clips

    private Uri customUri(Sound s) {
        return storedUri(s.key);
    }

    private Uri pictureUri(Sound s) {
        return storedUri(PICTURE_PREFIX + s.key);
    }

    private Uri storedUri(String key) {
        String value = prefs.getString(key, null);
        return value == null ? null : Uri.parse(value);
    }

    private void showCustomiseDialog(int index) {
        Sound s = SOUNDS[index];
        boolean hasCustom = customUri(s) != null || pictureUri(s) != null;

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
        title.setText(getString(R.string.dialog_title, s.label));

        Button reset = dialog.findViewById(R.id.btn_reset);
        reset.setVisibility(hasCustom ? View.VISIBLE : View.GONE);
        reset.setOnClickListener(v -> {
            resetTile(index);
            dialog.dismiss();
        });
        dialog.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialog.findViewById(R.id.btn_choose_clip).setOnClickListener(v -> {
            dialog.dismiss();
            pick(index, "audio/*", REQUEST_PICK_CLIP);
        });
        dialog.findViewById(R.id.btn_choose_picture).setOnClickListener(v -> {
            dialog.dismiss();
            pick(index, "image/*", REQUEST_PICK_PICTURE);
        });
        dialog.show();
    }

    private void pick(int index, String mimeType, int requestCode) {
        pendingIndex = index;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        try {
            startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            pendingIndex = -1;
            Toast.makeText(this, R.string.no_picker, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_CLIP && requestCode != REQUEST_PICK_PICTURE) {
            return;
        }
        int index = pendingIndex;
        pendingIndex = -1;
        if (resultCode != RESULT_OK || data == null || data.getData() == null
                || index < 0 || index >= SOUNDS.length) {
            return;
        }

        boolean isPicture = requestCode == REQUEST_PICK_PICTURE;
        String key = isPicture ? PICTURE_PREFIX + SOUNDS[index].key : SOUNDS[index].key;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Some providers don't offer persistable access; the file still works until reboot.
        }
        releaseOldPermission(key, uri);
        prefs.edit().putString(key, uri.toString()).apply();
        refreshTile(index);
        Toast.makeText(this, isPicture ? R.string.picture_saved : R.string.clip_saved, Toast.LENGTH_SHORT).show();
    }

    private void resetTile(int index) {
        if (playingIndex == index) {
            stopPlayback();
        }
        String[] keys = {SOUNDS[index].key, PICTURE_PREFIX + SOUNDS[index].key};
        for (String key : keys) {
            releaseOldPermission(key, null);
            prefs.edit().remove(key).apply();
        }
        refreshTile(index);
    }

    /** Drops our read access to a key's previous file, unless another button still uses it. */
    private void releaseOldPermission(String key, Uri replacement) {
        Uri old = storedUri(key);
        if (old == null || old.equals(replacement)) {
            return;
        }
        for (String other : prefs.getAll().keySet()) {
            if (!other.equals(key) && old.equals(storedUri(other))) {
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
