package com.yike.timer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class Models {
    private Models() {}

    static final String[] BASE_MATTERS = {"学习", "娱乐", "日常", "休息", "其他"};
    static final int[] BASE_TIMES = {5, 10, 15, 20, 25, 30, 40, 45, 60, 120};

    static class Settings {
        int version = 1;
        boolean haptics = true;
        String reminder = "both";
        String repeat = "15";
        int repeatCustom = 20;
        String orientation = "landscape";
        boolean keepAwake = true;
        boolean pixelShift = true;
        String shiftInterval = "10";
        int shiftCustom = 30;
        int digitWeight = 1;
        boolean solidDigits = false;

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("version", version);
                o.put("haptics", haptics);
                o.put("reminder", reminder);
                o.put("repeat", repeat);
                o.put("repeatCustom", repeatCustom);
                o.put("orientation", orientation);
                o.put("keepAwake", keepAwake);
                o.put("pixelShift", pixelShift);
                o.put("shiftInterval", shiftInterval);
                o.put("shiftCustom", shiftCustom);
                o.put("digitWeight", digitWeight);
                o.put("solidDigits", solidDigits);
            } catch (JSONException ignored) {}
            return o;
        }

        static Settings from(JSONObject o) {
            Settings s = new Settings();
            if (o == null) return s;
            s.version = o.optInt("version", 0);
            s.haptics = o.optBoolean("haptics", s.haptics);
            s.reminder = o.optString("reminder", s.reminder);
            s.repeat = o.optString("repeat", s.repeat);
            s.repeatCustom = o.optInt("repeatCustom", s.repeatCustom);
            s.orientation = o.optString("orientation", s.orientation);
            s.keepAwake = o.optBoolean("keepAwake", s.keepAwake);
            s.pixelShift = o.optBoolean("pixelShift", s.pixelShift);
            s.shiftInterval = o.optString("shiftInterval", s.shiftInterval);
            s.shiftCustom = o.optInt("shiftCustom", s.shiftCustom);
            s.digitWeight = o.optInt("digitWeight", s.digitWeight);
            s.solidDigits = o.optBoolean("solidDigits", s.solidDigits);
            return s;
        }
    }

    static class Matter {
        String id;
        String name;

        Matter(String id, String name) {
            this.id = id;
            this.name = name;
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("name", name);
            return o;
        }

        static Matter from(JSONObject o) {
            return new Matter(o.optString("id"), o.optString("name"));
        }
    }

    static class TimeChip {
        String id;
        int minutes;

        TimeChip(String id, int minutes) {
            this.id = id;
            this.minutes = minutes;
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("minutes", minutes);
            return o;
        }

        static TimeChip from(JSONObject o) {
            return new TimeChip(o.optString("id"), o.optInt("minutes"));
        }
    }

    static class Segment {
        long start;
        long end = -1;

        Segment(long start, long end) {
            this.start = start;
            this.end = end;
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("start", start);
            o.put("end", end);
            return o;
        }

        static Segment from(JSONObject o) {
            return new Segment(o.optLong("start"), o.optLong("end", -1));
        }
    }

    static class Active {
        String matter = "";
        long totalMs = 0;
        long remainingMs = 0;
        long elapsedMs = 0;
        long startedAt = 0;
        long lastStartAt = 0;
        long endAt = 0;
        boolean running = false;
        boolean finished = false;
        String signature = "";
        long nextReminderAt = 0;
        List<Segment> segments = new ArrayList<>();

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("matter", matter);
                o.put("totalMs", totalMs);
                o.put("remainingMs", remainingMs);
                o.put("elapsedMs", elapsedMs);
                o.put("startedAt", startedAt);
                o.put("lastStartAt", lastStartAt);
                o.put("endAt", endAt);
                o.put("running", running);
                o.put("finished", finished);
                o.put("signature", signature);
                o.put("nextReminderAt", nextReminderAt);
                JSONArray arr = new JSONArray();
                for (Segment s : segments) arr.put(s.toJson());
                o.put("segments", arr);
            } catch (JSONException ignored) {}
            return o;
        }

        static Active from(JSONObject o) {
            Active a = new Active();
            a.matter = o.optString("matter");
            a.totalMs = o.optLong("totalMs");
            a.remainingMs = o.optLong("remainingMs");
            a.elapsedMs = o.optLong("elapsedMs");
            a.startedAt = o.optLong("startedAt");
            a.lastStartAt = o.optLong("lastStartAt");
            a.endAt = o.optLong("endAt");
            a.running = o.optBoolean("running");
            a.finished = o.optBoolean("finished");
            a.signature = o.optString("signature");
            a.nextReminderAt = o.optLong("nextReminderAt");
            JSONArray arr = o.optJSONArray("segments");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject s = arr.optJSONObject(i);
                    if (s != null) a.segments.add(Segment.from(s));
                }
            }
            return a;
        }
    }

    static class RecordItem {
        String id = "";
        String matter = "";
        long start = 0;
        long end = 0;
        long duration = 0;
        List<Segment> segments = new ArrayList<>();

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id);
                o.put("matter", matter);
                o.put("start", start);
                o.put("end", end);
                o.put("duration", duration);
                JSONArray arr = new JSONArray();
                for (Segment s : segments) arr.put(s.toJson());
                o.put("segments", arr);
            } catch (JSONException ignored) {}
            return o;
        }

        static RecordItem from(JSONObject o) {
            RecordItem r = new RecordItem();
            r.id = o.optString("id");
            r.matter = o.optString("matter");
            r.start = o.optLong("start");
            r.end = o.optLong("end");
            r.duration = o.optLong("duration");
            JSONArray arr = o.optJSONArray("segments");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject s = arr.optJSONObject(i);
                    if (s != null) r.segments.add(Segment.from(s));
                }
            }
            return r;
        }
    }
}
