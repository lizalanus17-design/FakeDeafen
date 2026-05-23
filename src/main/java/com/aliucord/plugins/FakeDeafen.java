package com.aliucord.plugins;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.aliucord.Logger;
import com.aliucord.annotations.AliucordPlugin;
import com.aliucord.entities.Plugin;
import com.aliucord.patcher.Hook;
import com.aliucord.utils.DimenUtils;
import com.aliucord.views.Divider;
import com.discord.stores.StoreMediaSettings;
import com.discord.stores.StoreStream;
import com.discord.stores.StoreVoiceChannel;
import com.discord.views.CheckedSetting;

@AliucordPlugin
public class FakeDeafen extends Plugin {

    public static final Logger logger = new Logger("FakeDeafen");

    static final String KEY_FAKE_DEAFEN = "fakeDeafen";
    static final String KEY_FAKE_MUTE   = "fakeMute";
    static final String KEY_AUTO_DEAFEN = "autoDeafen";
    static final String KEY_AUTO_MUTE   = "autoMute";

    @NonNull
    @Override
    public Manifest getManifest() {
        var m = new Manifest();
        m.authors     = new Manifest.Author[]{ new Manifest.Author("You", 0L) };
        m.description = "Fake deafen/mute — appear silent but keep hearing everyone";
        m.version     = "1.0.0";
        m.updateUrl   = null;
        return m;
    }

    @Override
    public void start(Context ctx) throws Throwable {

        // 1. Block real deafen call to audio engine
        tryPatch(
            "com.discord.rtcconnection.mediaengine.AppMediaEngine",
            "setSelfDeaf",
            new Class[]{ boolean.class },
            param -> {
                if (settings.getBool(KEY_FAKE_DEAFEN, false))
                    param.setResult(null);
            }
        );

        // 2. Block real mute call to audio engine
        tryPatch(
            "com.discord.rtcconnection.mediaengine.AppMediaEngine",
            "setSelfMute",
            new Class[]{ boolean.class },
            param -> {
                if (settings.getBool(KEY_FAKE_MUTE, false))
                    param.setResult(null);
            }
        );

        // 3. Make Discord UI show deafen icon
        patcher.patch(
            StoreMediaSettings.class,
            "isSelfDeafened",
            new Class[]{},
            new Hook(param -> {
                if (settings.getBool(KEY_FAKE_DEAFEN, false))
                    param.setResult(true);
            })
        );

        // 4. Make Discord UI show mute icon
        patcher.patch(
            StoreMediaSettings.class,
            "isSelfMuted",
            new Class[]{},
            new Hook(param -> {
                if (settings.getBool(KEY_FAKE_MUTE, false))
                    param.setResult(true);
            })
        );

        // 5. Auto-apply on voice join
        patcher.patch(
            StoreVoiceChannel.class,
            "joinVoiceChannel",
            new Class[]{ long.class, boolean.class, boolean.class },
            new Hook(param -> applyAutoStates())
        );

        logger.info("FakeDeafen started");
    }

    @Override
    public void stop(Context ctx) {
        patcher.unpatchAll();
    }

    @Override
    public boolean isSettingsPageEnabled() { return true; }

    @Override
    @SuppressLint("SetTextI18n")
    public View getSettingsPage(Context ctx) {
        var root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = DimenUtils.dpToPx(16);
        root.setPadding(p, p, p, p);

        root.addView(toggle(ctx,
            "Fake Deafen",
            "Appear deafened to others — you still hear everyone",
            KEY_FAKE_DEAFEN));

        root.addView(new Divider(ctx));

        root.addView(toggle(ctx,
            "Fake Mute",
            "Appear muted to others — your mic stays active locally",
            KEY_FAKE_MUTE));

        root.addView(new Divider(ctx));

        root.addView(toggle(ctx,
            "Auto Fake Deafen on Join",
            "Automatically fake-deafen when joining a voice channel",
            KEY_AUTO_DEAFEN));

        root.addView(new Divider(ctx));

        root.addView(toggle(ctx,
            "Auto Fake Mute on Join",
            "Automatically fake-mute when joining a voice channel",
            KEY_AUTO_MUTE));

        return root;
    }

    // ── helpers ──────────────────────────────────────────────────────

    private CheckedSetting toggle(Context ctx, String title, String sub, String key) {
        var t = new CheckedSetting(ctx, null);
        t.setType(CheckedSetting.ViewType.SWITCH);
        t.setTitle(title);
        t.setSubtext(sub);
        t.setChecked(settings.getBool(key, false));
        t.setOnCheckedListener(v -> settings.setBool(key, v));
        return t;
    }

    private void applyAutoStates() {
        try {
            var store = StoreStream.Companion.getMediaSettings();
            if (settings.getBool(KEY_AUTO_DEAFEN, false)) store.setDeaf(true);
            if (settings.getBool(KEY_AUTO_MUTE,   false)) store.setMute(true);
        } catch (Throwable t) {
            logger.error("applyAutoStates failed", t);
        }
    }

    private void tryPatch(String cls, String method, Class<?>[] sig,
                          com.aliucord.patcher.PinePatchFn fn) {
        try {
            patcher.patch(Class.forName(cls), method, sig, new Hook(fn));
        } catch (Throwable t) {
            logger.error("Cannot patch " + cls + "#" + method +
                         " (Discord may have renamed it — check mappings)", t);
        }
    }
}
