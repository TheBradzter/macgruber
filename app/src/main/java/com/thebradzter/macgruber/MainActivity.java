package com.thebradzter.macgruber;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
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

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
        /** A clip loaded from the phone's clips folder, or null for a built-in sound. */
        final File file;
        /** A picture or GIF from the phone's gifs folder shown on this button, or null. */
        File gif;

        Sound(String key, String emoji, String label, int resId) {
            this(key, emoji, label, resId, null);
        }

        Sound(String key, String emoji, String label, int resId, File file) {
            this.key = key;
            this.emoji = emoji;
            this.label = label;
            this.resId = resId;
            this.file = file;
        }
    }

    /** Fallback sounds, used only when the phone's clips folder is empty. */
    private static final Sound[] BUILT_IN = {
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

    private Sound[] sounds;
    private Tile[] tiles;
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

        sounds = loadSounds();
        tiles = new Tile[sounds.length];
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

    private static final List<String> AUDIO_EXTENSIONS = Arrays.asList(
            "mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "flac", "3gp", "amr", "mp4");
    private static final String[] FALLBACK_EMOJI = {
            "\uD83D\uDCA5", "\uD83D\uDCAA", "\uD83D\uDE0E", "\uD83D\uDD25", "\uD83D\uDCA3",
            "\uD83E\uDD6C", "\uD83D\uDE97", "\uD83C\uDF96\uFE0F", "\uD83D\uDE80", "\uD83D\uDE31",
            "\uD83C\uDFB8", "\uD83E\uDD85", "\uD83D\uDCCE", "\uD83D\uDE81", "\uD83D\uDCAF",
            "\uD83D\uDE02"};

    /**
     * Clips copied into Android/data/com.thebradzter.macgruber/files/clips on the phone become the
     * buttons, one per file, labelled from the file name. Built-in sounds are only used if that
     * folder has no audio in it.
     */
    private Sound[] loadSounds() {
        File dir = getExternalFilesDir("clips");
        File[] files = dir == null ? null : dir.listFiles();
        List<Sound> found = new ArrayList<>();
        if (files != null) {
            Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File f : files) {
                String name = f.getName();
                int dot = name.lastIndexOf('.');
                if (!f.isFile() || dot <= 0) {
                    continue;
                }
                String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (!AUDIO_EXTENSIONS.contains(ext)) {
                    continue;
                }
                String label = labelFromFileName(name.substring(0, dot));
                String emoji = FALLBACK_EMOJI[found.size() % FALLBACK_EMOJI.length];
                found.add(new Sound("file:" + name, emoji, label, 0, f));
            }
        }
        Sound[] result = found.isEmpty() ? BUILT_IN : found.toArray(new Sound[0]);
        assignGifs(result);
        return result;
    }

    private static final List<String> IMAGE_EXTENSIONS = Arrays.asList(
            "gif", "webp", "png", "jpg", "jpeg");

    /**
     * Pictures in Android/data/com.thebradzter.macgruber/files/gifs go on the buttons. A picture whose
     * name matches a clip's name goes on that clip's button; the rest fill the other buttons in order.
     */
    private void assignGifs(Sound[] list) {
        for (Sound s : list) {
            s.gif = null;
        }
        File dir = getExternalFilesDir("gifs");
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null) {
            return;
        }
        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        List<File> unmatched = new ArrayList<>();
        for (File f : files) {
            String name = f.getName();
            int dot = name.lastIndexOf('.');
            if (!f.isFile() || dot <= 0
                    || !IMAGE_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT))) {
                continue;
            }
            String base = name.substring(0, dot);
            Sound match = null;
            for (Sound s : list) {
                if (s.gif == null && s.file != null && baseName(s.file).equalsIgnoreCase(base)) {
                    match = s;
                    break;
                }
            }
            if (match != null) {
                match.gif = f;
            } else {
                unmatched.add(f);
            }
        }
        int next = 0;
        for (Sound s : list) {
            if (next >= unmatched.size()) {
                break;
            }
            if (s.gif == null) {
                s.gif = unmatched.get(next++);
            }
        }
    }

    private static String baseName(File f) {
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** "03_kfbr-392" becomes "KFBR 392". */
    static String labelFromFileName(String base) {
        String label = base.trim().replaceFirst("^\\d+[\\s._-]*", "")
                .replaceAll("[_\\-.]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (label.isEmpty()) {
            label = base;
        }
        return label.toUpperCase(Locale.ROOT);
    }

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
        for (int i = 0; i < sounds.length; i++) {
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
            emoji.setText(sounds[i].emoji);
            emoji.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
            emoji.setGravity(Gravity.CENTER);
            column.addView(emoji);

            TextView label = new TextView(this);
            label.setText(sounds[i].label);
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
        Sound s = sounds[index];
        Tile tile = tiles[index];
        tile.badge.setVisibility(customUri(s) != null ? View.VISIBLE : View.GONE);

        // A picture chosen on the phone wins over one from the gifs folder.
        Uri custom = pictureUri(s);
        Uri picture = custom != null ? custom : (s.gif != null ? Uri.fromFile(s.gif) : null);
        if (picture == null) {
            tile.picture.setImageDrawable(null);
            tile.picture.setVisibility(View.GONE);
            tile.scrim.setVisibility(View.GONE);
            return;
        }
        imageLoader.execute(() -> {
            Drawable drawable = decodePicture(picture);
            mainHandler.post(() -> {
                Uri nowCustom = pictureUri(s);
                Uri now = nowCustom != null ? nowCustom : (s.gif != null ? Uri.fromFile(s.gif) : null);
                if (isDestroyed() || !picture.equals(now)) {
                    return; // Changed again while loading.
                }
                boolean ok = drawable != null;
                tile.picture.setImageDrawable(drawable);
                tile.picture.setVisibility(ok ? View.VISIBLE : View.GONE);
                tile.scrim.setVisibility(ok ? View.VISIBLE : View.GONE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                        && drawable instanceof AnimatedImageDrawable) {
                    ((AnimatedImageDrawable) drawable).start();
                }
            });
        });
    }

    /** Loads a picture (animated if it's a GIF) scaled down to tile size, or null on failure. */
    private Drawable decodePicture(Uri uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                ImageDecoder.Source source = "file".equals(uri.getScheme())
                        ? ImageDecoder.createSource(new File(uri.getPath()))
                        : ImageDecoder.createSource(getContentResolver(), uri);
                return ImageDecoder.decodeDrawable(source, (decoder, info, src) -> {
                    int sample = 1;
                    while (info.getSize().getWidth() / (sample * 2) >= PICTURE_MAX_PX
                            && info.getSize().getHeight() / (sample * 2) >= PICTURE_MAX_PX) {
                        sample *= 2;
                    }
                    decoder.setTargetSampleSize(sample);
                });
            } catch (Exception | OutOfMemoryError e) {
                return null;
            }
        }
        Bitmap bitmap = decodeBitmap(uri);
        return bitmap == null ? null : new BitmapDrawable(getResources(), bitmap);
    }

    /** Loads a still picture scaled down to tile size, or null if it can't be read. */
    private Bitmap decodeBitmap(Uri uri) {
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
        Sound s = sounds[index];
        MediaPlayer mp = null;

        Uri custom = customUri(s);
        if (custom != null) {
            mp = createPlayer(custom);
            if (mp == null) {
                Toast.makeText(this, R.string.clip_failed, Toast.LENGTH_SHORT).show();
            }
        }
        if (mp == null && s.file != null) {
            mp = createPlayer(Uri.fromFile(s.file));
        }
        if (mp == null && s.resId != 0) {
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
        Sound s = sounds[index];
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
                || index < 0 || index >= sounds.length) {
            return;
        }

        boolean isPicture = requestCode == REQUEST_PICK_PICTURE;
        String key = isPicture ? PICTURE_PREFIX + sounds[index].key : sounds[index].key;
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
        String[] keys = {sounds[index].key, PICTURE_PREFIX + sounds[index].key};
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
