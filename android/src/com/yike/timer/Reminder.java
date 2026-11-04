package com.yike.timer;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

import org.json.JSONObject;

/** Alarm-backed finish reminders that keep working outside the app. */
final class Reminder {
    static final String ACTION_REMIND = "com.yike.timer.action.REMIND";
    static final String EXTRA_CONFIRM = "confirm_reminder";

    private static final int NOTIFICATION_ID = 41021;
    private static final int ALARM_REQUEST = 41022;
    private static final int OPEN_REQUEST = 41023;
    private static final int CONFIRM_REQUEST = 41024;
    private static final int CLOCK_REQUEST = 41025;

    private static final String CHANNEL_SOUND = "reminder-sound";
    private static final String CHANNEL_VIBRATE = "reminder-vibrate";
    private static final String CHANNEL_BOTH = "reminder-both";
    private static final String KEY_ACTIVE = "yike-active-v1";
    private static final String KEY_LAST_FIRE = "yike-reminder-last-v1";

    static volatile long lastFireAt = 0;

    private Reminder() {}

    static void ensureChannels(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL_BOTH) != null) return;

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if (sound == null) sound = Settings.System.DEFAULT_NOTIFICATION_URI;
        long[] pattern = {0, 180, 80, 180};

        NotificationChannel soundCh = new NotificationChannel(CHANNEL_SOUND, "声音提醒",
                NotificationManager.IMPORTANCE_HIGH);
        soundCh.setDescription("倒计时结束时发出提示音");
        soundCh.setSound(sound, attrs);
        soundCh.enableVibration(false);
        soundCh.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        NotificationChannel vibrateCh = new NotificationChannel(CHANNEL_VIBRATE, "震动提醒",
                NotificationManager.IMPORTANCE_HIGH);
        vibrateCh.setDescription("倒计时结束时震动");
        vibrateCh.setSound(null, null);
        vibrateCh.enableVibration(true);
        vibrateCh.setVibrationPattern(pattern);
        vibrateCh.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        NotificationChannel bothCh = new NotificationChannel(CHANNEL_BOTH, "声音和震动提醒",
                NotificationManager.IMPORTANCE_HIGH);
        bothCh.setDescription("倒计时结束时发出提示音并震动");
        bothCh.setSound(sound, attrs);
        bothCh.enableVibration(true);
        bothCh.setVibrationPattern(pattern);
        bothCh.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        nm.createNotificationChannels(java.util.Arrays.asList(soundCh, vibrateCh, bothCh));
    }

    private static String channelFor(Models.Settings s) {
        if ("sound".equals(s.reminder)) return CHANNEL_SOUND;
        if ("vibrate".equals(s.reminder)) return CHANNEL_VIBRATE;
        return CHANNEL_BOTH;
    }

    static long repeatSeconds(Models.Settings s) {
        return "custom".equals(s.repeat) ? s.repeatCustom : parseInt(s.repeat, 15);
    }

    static void scheduleFinish(Context c, long when) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long at = Math.max(System.currentTimeMillis() + 100L, when);
        PendingIntent pi = alarmIntent(c);
        try {
            if (canExact(am)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, clockIntent(c)), pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    private static void scheduleRepeat(Context c, long when) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long at = Math.max(System.currentTimeMillis() + 100L, when);
        PendingIntent pi = alarmIntent(c);
        try {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, clockIntent(c)), pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    private static boolean canExact(AlarmManager am) {
        if (Build.VERSION.SDK_INT >= 31) return am.canScheduleExactAlarms();
        return true;
    }

    private static PendingIntent alarmIntent(Context c) {
        Intent i = new Intent(c, ReminderReceiver.class).setAction(ACTION_REMIND);
        return PendingIntent.getBroadcast(c, ALARM_REQUEST, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent clockIntent(Context c) {
        Intent show = new Intent(c, MainActivity.class);
        show.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, CLOCK_REQUEST, show,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Posts one reminder now and arms the next one. */
    static boolean fire(Context c) {
        ensureChannels(c);
        Store store = new Store(c);
        Models.Settings settings = Models.Settings.from(parse(store.read("yike-settings-v1", null)));
        JSONObject activeJson = parse(store.read(KEY_ACTIVE, null));
        if (activeJson == null) {
            cancelAll(c);
            return false;
        }
        Models.Active active = Models.Active.from(activeJson);
        if (active.matter == null || active.matter.isEmpty()) {
            cancelAll(c);
            return false;
        }
        long now = System.currentTimeMillis();
        long interval = Math.max(1, repeatSeconds(settings)) * 1000L;
        long last = parseLong(store.read(KEY_LAST_FIRE, "0"), 0);
        if (last > 0 && now - last < interval * 0.55) {
            scheduleRepeat(c, last + interval);
            return false;
        }
        if (!active.finished) {
            if (active.endAt > now + 800) {
                if (active.running) scheduleFinish(c, active.endAt);
                return false;
            }
            finishInStore(active, now);
        }
        boolean alerted = postNotification(c, settings, active);
        if (!alerted) fallbackAlert(c, settings);
        lastFireAt = now;
        long next = now + interval;
        active.nextReminderAt = next;
        store.write(KEY_ACTIVE, active.toJson().toString());
        store.write(KEY_LAST_FIRE, String.valueOf(now));
        scheduleRepeat(c, next);
        return true;
    }

    private static void finishInStore(Models.Active a, long now) {
        if (a.running) {
            a.elapsedMs += Math.max(0, a.endAt - a.lastStartAt);
            a.lastStartAt = a.endAt;
        } else {
            a.elapsedMs = Math.max(a.elapsedMs, a.totalMs);
        }
        if (!a.segments.isEmpty()) {
            Models.Segment last = a.segments.get(a.segments.size() - 1);
            if (last.end < 0) last.end = a.endAt > 0 ? a.endAt : now;
        }
        a.remainingMs = 0;
        a.running = false;
        a.finished = true;
        a.nextReminderAt = 0;
    }

    private static boolean postNotification(Context c, Models.Settings s, Models.Active a) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return false;
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        if (!nm.areNotificationsEnabled()) return false;
        String channelId = channelFor(s);
        NotificationChannel channel = nm.getNotificationChannel(channelId);
        if (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE) return false;

        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(c, OPEN_REQUEST, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent confirm = new Intent(c, MainActivity.class);
        confirm.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        confirm.putExtra(EXTRA_CONFIRM, true);
        PendingIntent confirmPi = PendingIntent.getActivity(c, CONFIRM_REQUEST, confirm,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String matter = a.matter == null || a.matter.isEmpty() ? "倒计时" : a.matter;
        String text = matter + " 已结束，点“确定”停止提醒";
        Notification n = new Notification.Builder(c, channelId)
                .setSmallIcon(R.drawable.ic_stat_reminder)
                .setColor(0xFFE2E2E2)
                .setContentTitle("时间到了")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(openPi)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(c, R.drawable.ic_stat_reminder), "确定", confirmPi).build())
                .build();
        try {
            nm.notify(NOTIFICATION_ID, n);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    private static short[] beepPcm;

    private static void fallbackAlert(Context c, Models.Settings s) {
        if (!"vibrate".equals(s.reminder)) beep();
        if (!"sound".equals(s.reminder)) vibrate(c);
    }

    private static void beep() {
        try {
            if (beepPcm == null) {
                int rate = 44100;
                int n = (int) (rate * 0.22);
                short[] pcm = new short[n];
                for (int i = 0; i < n; i++) {
                    double t = i / (double) rate;
                    double env = 0.12 * Math.pow(0.001 / 0.12, t / 0.22);
                    pcm[i] = (short) (Math.sin(2 * Math.PI * 740 * t) * env * 32767);
                }
                beepPcm = pcm;
            }
            AudioTrack track = new AudioTrack(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(44100)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    beepPcm.length * 2,
                    AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE);
            track.write(beepPcm, 0, beepPcm.length);
            track.play();
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(track::release, 500);
        } catch (Exception ignored) {}
    }

    private static void vibrate(Context c) {
        try {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            long[] pattern = {0, 180, 80, 180};
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                v.vibrate(pattern, -1);
            }
        } catch (Exception ignored) {}
    }

    static void cancelAlarm(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(alarmIntent(c));
    }

    static void cancelAll(Context c) {
        cancelAlarm(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIFICATION_ID);
        new Store(c).remove(KEY_LAST_FIRE);
        lastFireAt = 0;
    }

    /** Re-arms alarms after the process was restarted. */
    static void resume(Context c) {
        Store store = new Store(c);
        Models.Settings settings = Models.Settings.from(parse(store.read("yike-settings-v1", null)));
        JSONObject activeJson = parse(store.read(KEY_ACTIVE, null));
        if (activeJson == null) return;
        Models.Active active = Models.Active.from(activeJson);
        if (active.matter == null || active.matter.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (active.finished) {
            long interval = Math.max(1, repeatSeconds(settings)) * 1000L;
            long last = parseLong(store.read(KEY_LAST_FIRE, "0"), 0);
            long next = last > 0 && last + interval > now ? last + interval : now + interval;
            active.nextReminderAt = next;
            store.write(KEY_ACTIVE, active.toJson().toString());
            scheduleRepeat(c, next);
        } else if (active.running && active.endAt > now) {
            scheduleFinish(c, active.endAt);
        }
    }

    private static JSONObject parse(String raw) {
        if (raw == null) return null;
        try {
            return new JSONObject(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static long parseLong(String raw, long fallback) {
        try {
            return Long.parseLong(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
