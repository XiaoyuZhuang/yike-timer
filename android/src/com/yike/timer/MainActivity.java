package com.yike.timer;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int COLOR_TEXT = 0xFFECECEC;
    private static final int COLOR_MUTED = 0xFF858585;
    private static final int COLOR_LINE = 0xFF303030;
    private static final int COLOR_SOFT_LINE = 0xFF252525;
    private static final int COLOR_DIM = 0xFFAAAAAA;
    private static final int COLOR_SUBTLE = 0xFF777777;

    private static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Random RANDOM = new Random();

    private Store store;

    private Models.Settings settings;
    private final List<Models.Matter> customMatters = new ArrayList<>();
    private final List<Models.TimeChip> customTimes = new ArrayList<>();
    private List<String> homeMatters = new ArrayList<>();
    private List<Integer> homeTimes = new ArrayList<>();
    private String signature = "";
    private String selectedMatter = null;
    private String selectedDate = "";
    private String historyFilter = "all";
    private final List<Models.RecordItem> records = new ArrayList<>();
    private Models.Active active = null;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable tickRunnable;
    private long lastSecond = -1;
    private long lastReminderUiAt = 0;
    private long lastHapticAt = 0;
    private long nextShiftAt = 0;

    private FrameLayout root;
    private LinearLayout mainColumn;
    private FrameLayout pageHost;
    private ScrollView homeScroll;
    private ScrollView recordsScroll;
    private ScrollView settingsScroll;
    private LinearLayout homeContent;
    private LinearLayout recordsContent;
    private LinearLayout settingsContent;
    private LinearLayout navBar;
    private final List<View> navItems = new ArrayList<>();
    private final List<Ui.IconView> navIcons = new ArrayList<>();
    private final List<TextView> navLabels = new ArrayList<>();
    private int insetTop = 0;
    private int insetBottom = 0;

    private TextView dateText;
    private TextView signatureText;
    private Ui.FlowLayout matterGrid;
    private Ui.GridWrapLayout timeGrid;

    private TextView recordsDate;
    private TextView dayLabel;
    private TextView dayTotal;
    private TextView historyTotal;
    private TextView filterView;
    private TextView timelineHead;
    private LinearLayout statsList;
    private LinearLayout timelineList;

    private Ui.SwitchView switchHaptics;
    private Ui.SwitchView switchKeepAwake;
    private Ui.SwitchView switchPixelShift;
    private Seg segReminder;
    private Seg segRepeat;
    private Seg segOrientation;
    private Seg segShift;
    private LinearLayout repeatCustomWrap;
    private EditText repeatCustomInput;
    private LinearLayout shiftCustomWrap;
    private EditText shiftCustomInput;
    private SeekBar digitWeightSeek;
    private TextView digitWeightValue;
    private TextView solidButton;
    private EditText bulkText;
    private View clearDataButton;

    private LinearLayout countdownRoot;
    private LinearLayout shifter;
    private TextView countdownMatter;
    private Ui.DigitView digits;
    private TextView countdownSignature;
    private TextView countdownStatus;
    private TextView reminderInfo;
    private LinearLayout timerControls;
    private LinearLayout reminderControls;
    private FrameLayout pauseButton;
    private Ui.IconView pauseIcon;
    private FrameLayout endButton;
    private Ui.IconView endIcon;

    private TextView toastView;
    private Runnable toastRunnable;
    private FrameLayout modalBackdrop;
    private Ui.MaxHeightScrollView modalScroll;
    private LinearLayout modalPanel;
    private TextView deleteTarget;

    private boolean timeDragging = false;
    private View timePressed;
    private float timeDownX, timeDownY;
    private boolean timeMoved = false;
    private Runnable timeLongPress;
    private View dragGhost;
    private String dragId;
    private int dragOffsetX, dragOffsetY;
    private boolean dragOverDelete = false;
    private String dragLastTarget = null;

    private boolean matterDragging = false;
    private View matterPressed;
    private float matterDownX, matterDownY;
    private boolean matterMoved = false;
    private Runnable matterLongPress;
    private String matterDragKey;
    private String matterDragLastTarget;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupWindow();
        store = new Store(this);
        loadState();
        buildUi();
        renderHome();
        renderSettings();
        renderRecords();
        showPage("home");
        restoreActive();
        Reminder.ensureChannels(this);
        handleIntent(getIntent());
        Reminder.resume(this);
        updateKeepAwake();
        applyInsetsPadding();
        requestNotificationPermission();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 410);
        }
    }

    private void handleIntent(Intent intent) {
        if (intent == null || !intent.getBooleanExtra(Reminder.EXTRA_CONFIRM, false)) return;
        intent.removeExtra(Reminder.EXTRA_CONFIRM);
        if (active != null && active.finished) {
            completeRecord(true);
        } else {
            Reminder.cancelAll(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void setupWindow() {
        Window w = getWindow();
        w.setStatusBarColor(Color.BLACK);
        w.setNavigationBarColor(Color.BLACK);
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        }
        w.getDecorView().setSystemUiVisibility(0);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    private void applyInsetsPadding() {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = 0;
            int bottom = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            insetTop = top;
            insetBottom = bottom;
            mainColumn.setPadding(0, top, 0, 0);
            navBar.setPadding(dp(24), dp(7), dp(24), dp(8) + bottom);
            navBar.setMinimumHeight(dp(70) + bottom);
            updateDeleteTargetMargin();
            if (modalBackdrop != null) {
                int ime = 0;
                if (Build.VERSION.SDK_INT >= 30) {
                    ime = insets.getInsets(WindowInsets.Type.ime()).bottom;
                }
                modalBackdrop.setPadding(0, 0, 0, ime);
                int available = getResources().getDisplayMetrics().heightPixels - ime;
                modalScroll.setMaxHeightPx((int) (available * 0.88f));
            }
            return insets;
        });
    }

    private int dp(float value) {
        return Ui.dp(this, value);
    }

    private float dpf(float value) {
        return Ui.dpf(this, value);
    }

    // ------------------------------------------------------------------ state

    private void loadState() {
        settings = Models.Settings.from(parseObject(store.read("yike-settings-v1", null)));
        if (settings.version < 1) {
            settings.pixelShift = true;
            settings.version = 1;
            persistSettings();
        }
        parseMatters(store.read("yike-matters-v1", null));
        parseTimes(store.read("yike-times-v1", null));
        homeMatters = parseStringArray(store.read("yike-home-matters-v1", null), Models.BASE_MATTERS);
        homeTimes = parseIntArray(store.read("yike-home-times-v1", null), Models.BASE_TIMES);
        signature = store.read("yike-signature-v1", "把眼前的五分钟过好。");
        selectedDate = localDate(System.currentTimeMillis());
        parseRecords(store.read("yike-records-v1", null));
        if (dropDemoRecords()) persistRecords();
    }

    private JSONObject parseObject(String raw) {
        if (raw == null) return null;
        try {
            return new JSONObject(raw);
        } catch (JSONException e) {
            return null;
        }
    }

    private void parseMatters(String raw) {
        customMatters.clear();
        if (raw == null) return;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) customMatters.add(Models.Matter.from(o));
            }
        } catch (JSONException ignored) {}
    }

    private void parseTimes(String raw) {
        customTimes.clear();
        if (raw == null) return;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) customTimes.add(Models.TimeChip.from(o));
            }
        } catch (JSONException ignored) {}
    }

    private List<String> parseStringArray(String raw, String[] fallback) {
        List<String> out = new ArrayList<>();
        if (raw != null) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    String v = arr.optString(i);
                    if (v != null && !v.isEmpty()) out.add(v);
                }
                return out;
            } catch (JSONException ignored) {}
        }
        Collections.addAll(out, fallback);
        return out;
    }

    private List<Integer> parseIntArray(String raw, int[] fallback) {
        List<Integer> out = new ArrayList<>();
        if (raw != null) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) out.add(arr.optInt(i));
                return out;
            } catch (JSONException ignored) {}
        }
        for (int v : fallback) out.add(v);
        return out;
    }

    private void parseRecords(String raw) {
        records.clear();
        if (raw == null) return;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) records.add(Models.RecordItem.from(o));
            }
        } catch (JSONException ignored) {}
    }

    private boolean dropDemoRecords() {
        boolean removed = false;
        for (int i = records.size() - 1; i >= 0; i--) {
            String id = records.get(i).id;
            if (id != null && id.startsWith("demo-")) {
                records.remove(i);
                removed = true;
            }
        }
        return removed;
    }

    private void restoreActive() {
        String raw = store.read("yike-active-v1", null);
        JSONObject o = parseObject(raw);
        if (o == null) return;
        Models.Active saved = Models.Active.from(o);
        if (saved.matter == null || saved.matter.isEmpty() || saved.totalMs <= 0) return;
        active = saved;
        enterCountdown();
    }

    private void persistSettings() {
        store.write("yike-settings-v1", settings.toJson().toString());
    }

    private void persistMatters() {
        JSONArray arr = new JSONArray();
        for (Models.Matter m : customMatters) {
            try {
                arr.put(m.toJson());
            } catch (JSONException ignored) {}
        }
        store.write("yike-matters-v1", arr.toString());
    }

    private void persistTimes() {
        JSONArray arr = new JSONArray();
        for (Models.TimeChip t : customTimes) {
            try {
                arr.put(t.toJson());
            } catch (JSONException ignored) {}
        }
        store.write("yike-times-v1", arr.toString());
    }

    private void persistHome() {
        JSONArray matters = new JSONArray();
        for (String s : homeMatters) matters.put(s);
        JSONArray times = new JSONArray();
        for (Integer i : homeTimes) times.put((int) i);
        store.write("yike-home-matters-v1", matters.toString());
        store.write("yike-home-times-v1", times.toString());
    }

    private void persistSignature() {
        store.write("yike-signature-v1", signature);
    }

    private void persistRecords() {
        JSONArray arr = new JSONArray();
        for (Models.RecordItem r : records) arr.put(r.toJson());
        store.write("yike-records-v1", arr.toString());
    }

    private void saveActive() {
        if (active == null) store.remove("yike-active-v1");
        else store.write("yike-active-v1", active.toJson().toString());
    }

    // ------------------------------------------------------------------- ui

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        mainColumn = new LinearLayout(this);
        mainColumn.setOrientation(LinearLayout.VERTICAL);
        mainColumn.setBackgroundColor(Color.BLACK);
        root.addView(mainColumn, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        pageHost = new FrameLayout(this);
        mainColumn.addView(pageHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        buildHomePage();
        buildRecordsPage();
        buildSettingsPage();
        pageHost.addView(homeScroll);
        pageHost.addView(recordsScroll);
        pageHost.addView(settingsScroll);

        View navDivider = new View(this);
        navDivider.setBackgroundColor(0xFF222222);
        mainColumn.addView(navDivider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        buildNav();

        buildCountdown();
        buildOverlays();

        setContentView(root);
    }

    private ScrollView newPageScroll() {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        s.setBackgroundColor(Color.BLACK);
        s.setVerticalScrollBarEnabled(false);
        s.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return s;
    }

    private LinearLayout newPageContent() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(20), dp(18), dp(20), dp(24));
        l.setBackgroundColor(Color.BLACK);
        return l;
    }

    private void buildHomePage() {
        homeScroll = newPageScroll();
        homeContent = newPageContent();
        homeScroll.addView(homeContent, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setMinimumHeight(dp(40));
        LinearLayout.LayoutParams headLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headLp.bottomMargin = dp(22);
        homeContent.addView(head, headLp);

        TextView title = Ui.tv(this, "一刻", 20, COLOR_TEXT);
        title.setTypeface(MEDIUM);
        title.setLetterSpacing(0.08f);
        head.addView(title);

        dateText = Ui.tv(this, "", 12, COLOR_MUTED);
        dateText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        head.addView(dateText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout sigRow = new LinearLayout(this);
        sigRow.setOrientation(LinearLayout.HORIZONTAL);
        sigRow.setGravity(Gravity.CENTER_VERTICAL);
        sigRow.setMinimumHeight(dp(58));
        sigRow.setPadding(0, dp(10), 0, dp(10));
        sigRow.setBackground(new Ui.LineDrawable(this, Color.BLACK, COLOR_SOFT_LINE, 1));
        sigRow.setClickable(true);
        sigRow.setOnClickListener(v -> {
            hapticTap();
            openSignatureEditor();
        });
        LinearLayout.LayoutParams sigLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sigLp.bottomMargin = dp(24);
        homeContent.addView(sigRow, sigLp);

        LinearLayout sigCopy = new LinearLayout(this);
        sigCopy.setOrientation(LinearLayout.VERTICAL);
        sigRow.addView(sigCopy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView sigLabel = Ui.tv(this, "个性签名", 11, 0xFF707070);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.bottomMargin = dp(5);
        sigCopy.addView(sigLabel, sl);

        signatureText = Ui.tv(this, "", 13, 0xFFC9C9C9);
        signatureText.setSingleLine(true);
        signatureText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        sigCopy.addView(signatureText);

        TextView edit = Ui.tv(this, "编辑", 12, COLOR_DIM);
        edit.setGravity(Gravity.CENTER);
        edit.setMinWidth(dp(44));
        edit.setMinHeight(dp(44));
        edit.setPadding(dp(4), 0, dp(4), 0);
        sigRow.addView(edit);

        matterGrid = new Ui.FlowLayout(this, 9, 9);
        homeContent.addView(matterGrid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        installMatterGridTouch();

        TextView matterHint = Ui.tv(this, "长按拖动标签可排序或删除，再次点击可编辑", 11, 0xFF666666);
        matterHint.setLineSpacing(0, 1.5f);
        LinearLayout.LayoutParams mhlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mhlp.topMargin = dp(10);
        homeContent.addView(matterHint, mhlp);

        timeGrid = new Ui.GridWrapLayout(this, 4, 9, 9);
        LinearLayout.LayoutParams tg = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tg.topMargin = dp(30);
        homeContent.addView(timeGrid, tg);
        installTimeGridTouch();
    }

    private TextView choiceButton(String text, boolean selected, float textSize) {
        TextView b = Ui.tv(this, text, textSize, selected ? 0xFF050505 : 0xFFD3D3D3);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(42));
        b.setPadding(dp(14), dp(6), dp(14), dp(6));
        b.setSingleLine(true);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.setMaxWidth(dp(240));
        b.setBackground(Ui.box(this, selected ? 0xFFE6E6E6 : Color.BLACK,
                selected ? 0xFFE6E6E6 : COLOR_LINE, 1, 4));
        return b;
    }

    private void setChoiceChipSelected(TextView chip, boolean selected) {
        chip.setTextColor(selected ? 0xFF050505 : 0xFFD3D3D3);
        chip.setBackground(Ui.box(this, selected ? 0xFFE6E6E6 : Color.BLACK,
                selected ? 0xFFE6E6E6 : COLOR_LINE, 1, 4));
    }

    private View matterChip(Models.Matter m) {
        TextView chip = choiceButton(m.name, m.name.equals(selectedMatter), 14);
        chip.setTag(new MatterTag(m.name, m.id, false));
        return chip;
    }

    private void renderHome() {
        dateText.setText(localDate(System.currentTimeMillis()).replace("-", "."));
        signatureText.setText(signature == null || signature.isEmpty() ? "点击设置一句个性签名" : signature);

        matterGrid.removeAllViews();
        for (String name : homeMatters) {
            TextView b = choiceButton(name, name.equals(selectedMatter), 14);
            b.setTag(new MatterTag(name, null, false));
            matterGrid.addView(b);
        }
        for (Models.Matter m : customMatters) {
            matterGrid.addView(matterChip(m));
        }
        TextView addMatter = choiceButton("添加", false, 14);
        addMatter.setTag(new MatterTag(null, null, true));
        matterGrid.addView(addMatter);

        renderTimeGrid();
    }

    private void renderTimeGrid() {
        timeGrid.removeAllViews();
        for (int minutes : homeTimes) {
            TextView b = timeChip(minutes, false, null);
            b.setTag(new TimeTag(minutes, null, false));
            timeGrid.addView(b);
        }
        for (Models.TimeChip t : customTimes) {
            TextView b = timeChip(t.minutes, true, t.id);
            b.setTag(new TimeTag(t.minutes, t.id, false));
            timeGrid.addView(b);
        }
        TextView add = timeChip(0, false, null);
        add.setText("自定义");
        add.setTag(new TimeTag(0, null, true));
        timeGrid.addView(add);
    }

    private TextView timeChip(int minutes, boolean custom, String id) {
        TextView b = Ui.tv(this, custom ? minutes + "分钟" : (id == null && minutes > 0 ? minutes + "分钟" : ""), 13, 0xFFD3D3D3);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(54));
        b.setPadding(dp(3), dp(8), dp(3), dp(8));
        b.setSingleLine(true);
        b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.setBackground(Ui.box(this, Color.BLACK, COLOR_LINE, 1, 4));
        return b;
    }

    private void buildNav() {
        navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setBackgroundColor(Color.BLACK);
        navBar.setPadding(dp(24), dp(7), dp(24), dp(8));
        navBar.setMinimumHeight(dp(70));
        mainColumn.addView(navBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        String[] labels = {"首页", "记录", "设置"};
        String[] pages = {"home", "records", "settings"};
        for (int i = 0; i < 3; i++) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setMinimumHeight(dp(52));
            item.setClickable(true);
            final String page = pages[i];
            item.setOnClickListener(v -> {
                hapticTap();
                showPage(page);
            });

            Ui.IconView icon = new Ui.IconView(this, i);
            item.addView(icon, new LinearLayout.LayoutParams(dp(19), dp(19)));

            TextView label = Ui.tv(this, labels[i], 11, 0xFF696969);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            llp.topMargin = dp(5);
            item.addView(label, llp);

            navBar.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            navItems.add(item);
            navIcons.add(icon);
            navLabels.add(label);
        }
    }

    private void showPage(String page) {
        homeScroll.setVisibility("home".equals(page) ? View.VISIBLE : View.GONE);
        recordsScroll.setVisibility("records".equals(page) ? View.VISIBLE : View.GONE);
        settingsScroll.setVisibility("settings".equals(page) ? View.VISIBLE : View.GONE);
        String[] pages = {"home", "records", "settings"};
        for (int i = 0; i < 3; i++) {
            boolean on = pages[i].equals(page);
            int color = on ? 0xFFEEEEEE : 0xFF696969;
            navIcons.get(i).setIconColor(color);
            navLabels.get(i).setTextColor(color);
        }
        if ("records".equals(page)) renderRecords();
        if ("settings".equals(page)) {
            renderSettings();
            refreshBulkText();
        }
    }

    // --------------------------------------------------------------- records

    private void buildRecordsPage() {
        recordsScroll = newPageScroll();
        recordsContent = newPageContent();
        recordsScroll.addView(recordsContent, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setMinimumHeight(dp(40));
        LinearLayout.LayoutParams headLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headLp.bottomMargin = dp(22);
        recordsContent.addView(head, headLp);

        TextView title = Ui.tv(this, "记录", 20, COLOR_TEXT);
        title.setTypeface(MEDIUM);
        title.setLetterSpacing(0.08f);
        head.addView(title);

        View spacer = new View(this);
        head.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        recordsDate = Ui.tv(this, "", 12, COLOR_DIM);
        recordsDate.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        recordsDate.setClickable(true);
        recordsDate.setOnClickListener(v -> {
            hapticTap();
            openDatePicker();
        });
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.rightMargin = dp(8);
        head.addView(recordsDate, dlp);

        TextView today = Ui.tv(this, "今天", 12, 0xFFBBBBBB);
        today.setGravity(Gravity.CENTER);
        today.setMinHeight(dp(38));
        today.setPadding(dp(12), 0, dp(12), 0);
        today.setBackground(Ui.box(this, Color.BLACK, 0xFF333333, 1, 4));
        today.setOnClickListener(v -> {
            hapticTap();
            selectedDate = localDate(System.currentTimeMillis());
            renderRecords();
        });
        head.addView(today);

        LinearLayout total = new LinearLayout(this);
        total.setOrientation(LinearLayout.HORIZONTAL);
        total.setGravity(Gravity.CENTER_VERTICAL);
        total.setPadding(0, dp(2), 0, dp(18));
        total.setBackground(new Ui.LineDrawable(this, Color.BLACK, 0xFF292929, 1));
        recordsContent.addView(total, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout totalMain = new LinearLayout(this);
        totalMain.setOrientation(LinearLayout.VERTICAL);
        total.addView(totalMain, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        dayLabel = Ui.tv(this, "", 12, COLOR_SUBTLE);
        totalMain.addView(dayLabel);

        dayTotal = Ui.tv(this, "0 分钟", 30, COLOR_TEXT);
        dayTotal.setLetterSpacing(-0.03f);
        LinearLayout.LayoutParams dtlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dtlp.topMargin = dp(7);
        totalMain.addView(dayTotal, dtlp);

        historyTotal = Ui.tv(this, "", 11, 0xFF888888);
        historyTotal.setLineSpacing(0, 1.5f);
        LinearLayout.LayoutParams htlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        htlp.topMargin = dp(7);
        totalMain.addView(historyTotal, htlp);

        filterView = Ui.tv(this, "全部事项", 12, COLOR_DIM);
        filterView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        filterView.setMaxWidth(dp(118));
        filterView.setSingleLine(true);
        filterView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        filterView.setClickable(true);
        filterView.setOnClickListener(v -> {
            hapticTap();
            openFilterDialog();
        });
        total.addView(filterView);

        statsList = new LinearLayout(this);
        statsList.setOrientation(LinearLayout.VERTICAL);
        statsList.setPadding(0, dp(7), 0, dp(10));
        statsList.setBackground(new Ui.LineDrawable(this, Color.BLACK, 0xFF292929, 1));
        recordsContent.addView(statsList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout timelineHeader = new LinearLayout(this);
        timelineHeader.setOrientation(LinearLayout.HORIZONTAL);
        timelineHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams thlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        thlp.topMargin = dp(18);
        thlp.bottomMargin = dp(5);
        recordsContent.addView(timelineHeader, thlp);

        timelineHead = Ui.tv(this, "时间轴", 12, COLOR_SUBTLE);
        timelineHeader.addView(timelineHead,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView addRecord = Ui.tv(this, "+", 22, 0xFFCFCFCF);
        addRecord.setGravity(Gravity.CENTER);
        addRecord.setMinWidth(dp(44));
        addRecord.setMinHeight(dp(36));
        addRecord.setContentDescription("添加记录");
        addRecord.setOnClickListener(v -> {
            hapticTap();
            openAddRecordDialog();
        });
        timelineHeader.addView(addRecord, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        timelineList = new LinearLayout(this);
        timelineList.setOrientation(LinearLayout.VERTICAL);
        recordsContent.addView(timelineList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void openDatePicker() {
        Calendar c = Calendar.getInstance();
        String[] parts = selectedDate.split("-");
        try {
            c.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
        } catch (Exception ignored) {}
        DatePickerDialog dialog = new DatePickerDialog(this, android.R.style.Theme_Material_Dialog_Alert,
                (view, year, month, day) -> {
                    selectedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
                    renderRecords();
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private void openFilterDialog() {
        LinearLayout content = beginModal();
        addDialogTitle(content, "按事项筛选记录");
        List<String> names = allMatterNames();
        List<String> values = new ArrayList<>();
        values.add("all");
        values.addAll(names);
        List<String> labels = new ArrayList<>();
        labels.add("全部事项");
        labels.addAll(names);
        for (int i = 0; i < values.size(); i++) {
            final String value = values.get(i);
            TextView row = Ui.tv(this, labels.get(i), 13, 0xFFDDDDDD);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinHeight(dp(44));
            row.setPadding(dp(2), 0, dp(2), 0);
            row.setBackground(new Ui.LineDrawable(this, Color.BLACK, 0xFF252525, 1));
            row.setClickable(true);
            row.setOnClickListener(v -> {
                hapticTap();
                historyFilter = value;
                closeModal();
                renderRecords();
            });
            content.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private List<String> allMatterNames() {
        Set<String> out = new LinkedHashSet<>();
        out.addAll(homeMatters);
        for (Models.Matter m : customMatters) out.add(m.name);
        for (Models.RecordItem r : records) out.add(r.matter);
        return new ArrayList<>(out);
    }

    private void openAddRecordDialog() {
        LinearLayout content = beginModal();
        addDialogTitle(content, "添加记录");
        addDialogCopy(content, "补记一条漏掉的记录，日期为 " + selectedDate + "。");

        List<String> names = new ArrayList<>();
        for (String name : allMatterNames()) {
            if (name != null && !name.trim().isEmpty()) names.add(name);
        }
        if (names.isEmpty()) names.add("其他");
        String initialMatter = !"all".equals(historyFilter) && names.contains(historyFilter)
                ? historyFilter
                : (names.contains(selectedMatter) ? selectedMatter : names.get(0));
        final String[] chosenMatter = {initialMatter};

        addDialogSection(content, "标签");
        final Ui.FlowLayout labelFlow = new Ui.FlowLayout(this, 8, 8);
        LinearLayout.LayoutParams lflp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lflp.topMargin = dp(8);
        content.addView(labelFlow, lflp);
        final List<TextView> labelChips = new ArrayList<>();
        for (final String name : names) {
            TextView chip = choiceButton(name, name.equals(chosenMatter[0]), 13);
            labelChips.add(chip);
            chip.setOnClickListener(v -> {
                hapticTap();
                chosenMatter[0] = name;
                for (int i = 0; i < labelChips.size(); i++) {
                    setChoiceChipSelected(labelChips.get(i), names.get(i).equals(name));
                }
            });
            labelFlow.addView(chip);
        }

        List<Integer> durations = new ArrayList<>();
        for (Integer minutes : homeTimes) {
            if (minutes != null && minutes > 0 && !durations.contains(minutes)) durations.add(minutes);
        }
        for (Models.TimeChip t : customTimes) {
            if (t.minutes > 0 && !durations.contains(t.minutes)) durations.add(t.minutes);
        }
        if (durations.isEmpty()) {
            for (int minutes : Models.BASE_TIMES) durations.add(minutes);
        }
        final int[] chosenMinutes = {durations.get(0)};
        final long[] startMs = {defaultManualStart(chosenMinutes[0])};
        final boolean[] startTouched = {false};

        View startGap = new View(this);
        content.addView(startGap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(10)));
        final TextView startValue = recordTimeValue(clockTime(startMs[0]));
        final TextView summary = Ui.tv(this, "", 12, 0xFF999999);
        final Runnable updateSummary = () -> summary.setText(
                clockTime(startMs[0]) + "–" + clockTime(startMs[0] + chosenMinutes[0] * 60000L)
                        + " · " + chosenMinutes[0] + " 分钟");
        addRecordTimeRow(content, "开始时间", startValue,
                () -> pickManualRecordStart(startMs, startValue, () -> {
                    startTouched[0] = true;
                    updateSummary.run();
                }));

        addDialogSection(content, "时长");
        final Ui.FlowLayout durationFlow = new Ui.FlowLayout(this, 8, 8);
        LinearLayout.LayoutParams dflp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dflp.topMargin = dp(8);
        content.addView(durationFlow, dflp);
        final List<TextView> durationChips = new ArrayList<>();
        for (final int minutes : durations) {
            TextView chip = choiceButton(minutes + " 分钟", minutes == chosenMinutes[0], 13);
            durationChips.add(chip);
            chip.setOnClickListener(v -> {
                hapticTap();
                chosenMinutes[0] = minutes;
                for (int i = 0; i < durationChips.size(); i++) {
                    setChoiceChipSelected(durationChips.get(i), durations.get(i) == minutes);
                }
                if (!startTouched[0] && selectedDate.equals(localDate(System.currentTimeMillis()))) {
                    startMs[0] = defaultManualStart(minutes);
                    startValue.setText(clockTime(startMs[0]));
                }
                updateSummary.run();
            });
            durationFlow.addView(chip);
        }

        updateSummary.run();
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(12);
        content.addView(summary, slp);

        LinearLayout actions = addDialogActions(content);
        TextView cancel = dialogButton("取消", 0);
        cancel.setOnClickListener(v -> closeModal());
        actions.addView(cancel);
        TextView save = dialogButton("添加", 1);
        save.setOnClickListener(v -> {
            long duration = chosenMinutes[0] * 60000L;
            Models.RecordItem r = new Models.RecordItem();
            r.id = "record-" + System.currentTimeMillis();
            r.matter = chosenMatter[0];
            r.start = startMs[0];
            r.end = startMs[0] + duration;
            r.duration = duration;
            r.segments.add(new Models.Segment(r.start, r.end));
            records.add(r);
            persistRecords();
            if (!"all".equals(historyFilter) && !historyFilter.equals(r.matter)) historyFilter = "all";
            closeModal();
            renderRecords();
            toast("记录已添加");
        });
        actions.addView(save);
    }

    private void addDialogSection(LinearLayout content, String text) {
        TextView t = Ui.tv(this, text, 11, 0xFF777777);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(16);
        content.addView(t, p);
    }

    private long defaultManualStart(int minutes) {
        long[] bounds = dayBounds(selectedDate);
        long now = System.currentTimeMillis();
        long start;
        if (selectedDate.equals(localDate(now))) {
            start = now - minutes * 60000L;
            if (start < bounds[0]) start = bounds[0];
        } else {
            start = bounds[0] + 12 * 3600000L;
        }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(start);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private void pickManualRecordStart(long[] startMs, TextView target, Runnable onChange) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startMs[0]);
        TimePickerDialog dialog = new TimePickerDialog(this, android.R.style.Theme_Material_Dialog_Alert,
                (view, hourOfDay, minute) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.setTimeInMillis(startMs[0]);
                    picked.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    picked.set(Calendar.MINUTE, minute);
                    picked.set(Calendar.SECOND, 0);
                    picked.set(Calendar.MILLISECOND, 0);
                    startMs[0] = picked.getTimeInMillis();
                    target.setText(clockTime(startMs[0]));
                    onChange.run();
                }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true);
        dialog.show();
    }

    private void renderRecords() {
        Set<String> names = new LinkedHashSet<>(allMatterNames());
        if (!"all".equals(historyFilter) && !names.contains(historyFilter)) historyFilter = "all";
        recordsDate.setText(selectedDate);
        String today = localDate(System.currentTimeMillis());
        dayLabel.setText((today.equals(selectedDate) ? "今天" : selectedDate) + "累计");
        filterView.setText("all".equals(historyFilter) ? "全部事项" : historyFilter);

        long[] bounds = dayBounds(selectedDate);
        List<ClippedRecord> dayRecords = new ArrayList<>();
        for (Models.RecordItem r : records) {
            long clipStart = Math.max(bounds[0], r.start);
            long clipEnd = Math.min(bounds[1], r.end);
            if (clipEnd <= clipStart) continue;
            long clippedDuration;
            if (r.segments != null && !r.segments.isEmpty()) {
                clippedDuration = 0;
                for (Models.Segment s : r.segments) {
                    long segEnd = s.end < 0 ? s.start : s.end;
                    clippedDuration += Math.max(0, Math.min(bounds[1], segEnd) - Math.max(bounds[0], s.start));
                }
            } else {
                long originalSpan = r.end - r.start;
                clippedDuration = originalSpan > 0
                        ? Math.min(clipEnd - clipStart, (long) (r.duration * ((double) (clipEnd - clipStart) / originalSpan)))
                        : 0;
            }
            if (!"all".equals(historyFilter) && !r.matter.equals(historyFilter)) continue;
            dayRecords.add(new ClippedRecord(r, clipStart, clipEnd, clippedDuration));
        }
        Collections.sort(dayRecords, (a, b) -> Long.compare(a.clipStart, b.clipStart));

        long daySum = 0;
        for (ClippedRecord c : dayRecords) daySum += c.clippedDuration;
        dayTotal.setText(formatDuration(daySum));

        long historical = 0;
        for (Models.RecordItem r : records) {
            if ("all".equals(historyFilter) || r.matter.equals(historyFilter)) historical += r.duration;
        }
        historyTotal.setText(("all".equals(historyFilter) ? "所有事项" : historyFilter)
                + "历史累计 " + formatDuration(historical));

        Map<String, Long> totals = new HashMap<>();
        for (ClippedRecord c : dayRecords) {
            totals.put(c.record.matter, totals.containsKey(c.record.matter)
                    ? totals.get(c.record.matter) + c.clippedDuration : c.clippedDuration);
        }
        statsList.removeAllViews();
        if (totals.isEmpty()) {
            TextView empty = Ui.tv(this, "这一天没有记录", 13, 0xFF666666);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(36), 0, dp(36));
            statsList.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            List<Map.Entry<String, Long>> entries = new ArrayList<>(totals.entrySet());
            entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
            long longest = 1;
            for (Map.Entry<String, Long> e : entries) longest = Math.max(longest, e.getValue());
            // 时长文本统一占同样宽度，像素柱才能拥有相同的右边界。
            Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            valuePaint.setTextSize(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP, 12, getResources().getDisplayMetrics()));
            int valueWidth = dp(48);
            for (Map.Entry<String, Long> e : entries) {
                valueWidth = Math.max(valueWidth,
                        (int) Math.ceil(valuePaint.measureText(formatDuration(e.getValue()))) + dp(2));
            }
            for (Map.Entry<String, Long> e : entries) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(dp(36));

                TextView name = Ui.tv(this, e.getKey(), 13, COLOR_TEXT);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                LinearLayout metric = new LinearLayout(this);
                metric.setOrientation(LinearLayout.HORIZONTAL);
                metric.setGravity(Gravity.CENTER_VERTICAL);
                int cells = Math.max(1, Math.round(e.getValue() / (float) longest * 10));
                Ui.PixelChart chart = new Ui.PixelChart(this);
                chart.setCells(cells);
                metric.addView(chart);

                TextView value = Ui.tv(this, formatDuration(e.getValue()), 12, 0xFFA9A9A9);
                value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                        valueWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
                vlp.leftMargin = dp(9);
                metric.addView(value, vlp);
                row.addView(metric);
                statsList.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }

        timelineList.removeAllViews();
        if (!dayRecords.isEmpty()) {
            for (int i = 0; i < dayRecords.size(); i++) {
                ClippedRecord c = dayRecords.get(i);
                LinearLayout item = new LinearLayout(this);
                item.setOrientation(LinearLayout.HORIZONTAL);
                item.setGravity(Gravity.CENTER_VERTICAL);
                item.setMinimumHeight(dp(42));
                item.setPadding(0, dp(9), 0, dp(9));
                item.setBackground(i == dayRecords.size() - 1 ? null
                        : new Ui.LineDrawable(this, Color.BLACK, 0xFF1F1F1F, 1));
                final Models.RecordItem record = c.record;
                item.setOnLongClickListener(v -> {
                    hapticTap();
                    openRecordEditor(record);
                    return true;
                });

                TextView matter = Ui.tv(this, c.record.matter, 13, COLOR_TEXT);
                matter.setSingleLine(true);
                matter.setEllipsize(android.text.TextUtils.TruncateAt.END);
                matter.setMaxWidth(dp(82));
                item.addView(matter);

                TextView range = Ui.tv(this, clockTime(c.clipStart) + "–" + clockTime(c.clipEnd), 11, 0xFF6F6F6F);
                range.setSingleLine(true);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                rlp.leftMargin = dp(8);
                item.addView(range, rlp);

                TextView duration = Ui.tv(this, formatDuration(c.clippedDuration), 12, 0xFFBBBBBB);
                item.addView(duration);
                timelineList.addView(item, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }
    }

    private void openRecordEditor(Models.RecordItem r) {
        LinearLayout content = beginModal();
        addDialogTitle(content, "记录操作");
        addDialogCopy(content, r.matter + " · " + clockTime(r.start) + "–" + clockTime(r.end)
                + "，共 " + formatDuration(r.duration));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = dp(16);
        content.addView(actions, alp);

        TextView editLabel = dialogButton("编辑标签", 0);
        editLabel.setOnClickListener(v -> {
            closeModal();
            openRecordLabelEditor(r);
        });
        addStackedRecordButton(actions, editLabel, 0);

        TextView editTime = dialogButton("编辑时间", 0);
        editTime.setOnClickListener(v -> {
            closeModal();
            openRecordTimeEditor(r);
        });
        addStackedRecordButton(actions, editTime, 8);

        TextView delete = dialogButton("删除这条记录", 2);
        delete.setOnClickListener(v -> confirmDeleteRecord(r));
        addStackedRecordButton(actions, delete, 8);

        TextView cancel = dialogButton("取消", 0);
        cancel.setOnClickListener(v -> closeModal());
        addStackedRecordButton(actions, cancel, 8);
    }

    private void addStackedRecordButton(LinearLayout parent, View button, int topMarginDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topMarginDp);
        parent.addView(button, p);
    }

    private void openRecordLabelEditor(Models.RecordItem r) {
        LinearLayout content = beginModal();
        addDialogTitle(content, "编辑标签");
        addDialogCopy(content, "只修改这条记录的标签，起止时间保持不变。");
        EditText input = dialogInput(InputType.TYPE_CLASS_TEXT, "输入标签名称", 12, false);
        input.setText(r.matter);
        input.setSelection(input.getText().length());
        content.addView(input);

        LinearLayout actions = addDialogActions(content);
        TextView back = dialogButton("返回", 0);
        back.setOnClickListener(v -> {
            closeModal();
            openRecordEditor(r);
        });
        actions.addView(back);
        TextView save = dialogButton("保存", 1);
        save.setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                toast("标签不能为空");
                return;
            }
            r.matter = name;
            persistRecords();
            closeModal();
            renderRecords();
            toast("标签已更新");
        });
        actions.addView(save);
        focusInput(input);
    }

    private void openRecordTimeEditor(Models.RecordItem r) {
        LinearLayout content = beginModal();
        addDialogTitle(content, "编辑时间");
        addDialogCopy(content, "调整开始和结束时刻，日期保持不变；保存后按连续时长重新计算。");

        long[] range = {r.start, r.end};
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setBackground(new Ui.LineDrawable(this, Color.BLACK, COLOR_SOFT_LINE, 1));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(16);
        content.addView(rows, rlp);

        TextView startValue = recordTimeValue(clockTime(range[0]));
        TextView endValue = recordTimeValue(clockTime(range[1]));
        addRecordTimeRow(rows, "开始时间", startValue, () -> pickRecordTime(range, 0, startValue));
        addRecordTimeRow(rows, "结束时间", endValue, () -> pickRecordTime(range, 1, endValue));

        LinearLayout actions = addDialogActions(content);
        TextView back = dialogButton("返回", 0);
        back.setOnClickListener(v -> {
            closeModal();
            openRecordEditor(r);
        });
        actions.addView(back);
        TextView save = dialogButton("保存", 1);
        save.setOnClickListener(v -> {
            if (range[1] <= range[0]) {
                toast("结束时间需要晚于开始时间");
                return;
            }
            r.start = range[0];
            r.end = range[1];
            r.duration = range[1] - range[0];
            r.segments.clear();
            r.segments.add(new Models.Segment(r.start, r.end));
            persistRecords();
            closeModal();
            renderRecords();
            toast("时间已更新");
        });
        actions.addView(save);
    }

    private TextView recordTimeValue(String text) {
        TextView t = Ui.tv(this, text, 15, COLOR_TEXT);
        t.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        t.setMinWidth(dp(64));
        return t;
    }

    private void addRecordTimeRow(LinearLayout parent, String label, TextView value, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(50));
        row.setBackground(new Ui.LineDrawable(this, Color.BLACK, COLOR_SOFT_LINE, 1));
        row.addView(Ui.tv(this, label, 14, COLOR_TEXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(value);
        row.setClickable(true);
        row.setOnClickListener(v -> {
            hapticTap();
            onClick.run();
        });
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void pickRecordTime(long[] range, int index, TextView target) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(range[index]);
        TimePickerDialog dialog = new TimePickerDialog(this, android.R.style.Theme_Material_Dialog_Alert,
                (view, hourOfDay, minute) -> {
                    Calendar picked = Calendar.getInstance();
                    picked.setTimeInMillis(range[index]);
                    picked.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    picked.set(Calendar.MINUTE, minute);
                    picked.set(Calendar.SECOND, 0);
                    picked.set(Calendar.MILLISECOND, 0);
                    range[index] = picked.getTimeInMillis();
                    target.setText(clockTime(range[index]));
                }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true);
        dialog.show();
    }

    private void confirmDeleteRecord(Models.RecordItem r) {
        closeModal();
        LinearLayout content = beginModal();
        addDialogTitle(content, "删除这条记录？");
        addDialogCopy(content, r.matter + " · " + clockTime(r.start) + "–" + clockTime(r.end)
                + "，删除后无法恢复。");
        LinearLayout actions = addDialogActions(content);
        TextView cancel = dialogButton("取消", 0);
        cancel.setOnClickListener(v -> {
            closeModal();
            openRecordEditor(r);
        });
        actions.addView(cancel);
        TextView confirm = dialogButton("删除", 2);
        confirm.setOnClickListener(v -> {
            records.remove(r);
            persistRecords();
            closeModal();
            renderRecords();
            toast("已删除记录");
        });
        actions.addView(confirm);
    }

    private static class ClippedRecord {
        final Models.RecordItem record;
        final long clipStart;
        final long clipEnd;
        final long clippedDuration;

        ClippedRecord(Models.RecordItem record, long clipStart, long clipEnd, long clippedDuration) {
            this.record = record;
            this.clipStart = clipStart;
            this.clipEnd = clipEnd;
            this.clippedDuration = clippedDuration;
        }
    }

    // -------------------------------------------------------------- settings

    private void buildSettingsPage() {
        settingsScroll = newPageScroll();
        settingsContent = newPageContent();
        settingsScroll.addView(settingsContent, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setMinimumHeight(dp(40));
        LinearLayout.LayoutParams headLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headLp.bottomMargin = dp(22);
        settingsContent.addView(head, headLp);
        TextView title = Ui.tv(this, "设置", 20, COLOR_TEXT);
        title.setTypeface(MEDIUM);
        title.setLetterSpacing(0.08f);
        head.addView(title);

        LinearLayout groupHaptics = settingsGroup();
        LinearLayout hapticsRow = new LinearLayout(this);
        hapticsRow.setOrientation(LinearLayout.HORIZONTAL);
        hapticsRow.setGravity(Gravity.CENTER_VERTICAL);
        hapticsRow.setMinimumHeight(dp(54));
        hapticsRow.addView(Ui.tv(this, "点击震动反馈", 14, COLOR_TEXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        switchHaptics = new Ui.SwitchView(this);
        attachTapHaptic(switchHaptics);
        switchHaptics.setOnChecked(checked -> {
            settings.haptics = checked;
            persistSettings();
            if (checked) vibrate(14);
        });
        hapticsRow.addView(switchHaptics, new LinearLayout.LayoutParams(dp(46), dp(28)));
        groupHaptics.addView(hapticsRow);
        settingsContent.addView(groupHaptics);

        LinearLayout groupReminder = settingsGroup();
        segReminder = buildSeg(new String[]{"sound", "vibrate", "both"},
                new String[]{"声音", "震动", "声音加震动"},
                new int[]{Ui.IconView.BELL, Ui.IconView.VIBRATE_PHONE, Ui.IconView.BELL_VIBRATE},
                value -> {
                    settings.reminder = value;
                    persistSettings();
                    renderSettings();
                });
        groupReminder.addView(inlineRow("结束提醒方式", segReminder.view, 0));

        segRepeat = buildSeg(new String[]{"15", "30", "60", "custom"},
                new String[]{"15秒", "30秒", "60秒", "自定义"}, value -> {
                    settings.repeat = value;
                    persistSettings();
                    renderSettings();
                });
        groupReminder.addView(inlineRow("重复提醒间隔", segRepeat.view, 14));

        repeatCustomWrap = new LinearLayout(this);
        repeatCustomWrap.setOrientation(LinearLayout.HORIZONTAL);
        repeatCustomWrap.setGravity(Gravity.CENTER_VERTICAL);
        repeatCustomWrap.setVisibility(View.GONE);
        LinearLayout.LayoutParams rclp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rclp.topMargin = dp(10);
        groupReminder.addView(repeatCustomWrap, rclp);
        repeatCustomWrap.addView(Ui.tv(this, "每", 12, 0xFF888888));
        repeatCustomInput = numberInput();
        LinearLayout.LayoutParams rcilp = new LinearLayout.LayoutParams(dp(90), dp(42));
        rcilp.leftMargin = dp(10);
        rcilp.rightMargin = dp(10);
        repeatCustomWrap.addView(repeatCustomInput, rcilp);
        repeatCustomWrap.addView(Ui.tv(this, "秒重复一次", 12, 0xFF888888));
        repeatCustomInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) commitRepeatCustom();
        });
        repeatCustomInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hapticTap();
                commitRepeatCustom();
                return true;
            }
            return false;
        });
        settingsContent.addView(groupReminder);

        LinearLayout groupOrientation = settingsGroup();
        segOrientation = buildSeg(new String[]{"landscape", "portrait", "follow"},
                new String[]{"横屏", "竖屏", "跟随系统"},
                new int[]{Ui.IconView.ORIENT_LANDSCAPE, Ui.IconView.ORIENT_PORTRAIT, Ui.IconView.ORIENT_ROTATE},
                value -> {
                    settings.orientation = value;
                    persistSettings();
                    renderSettings();
                });
        groupOrientation.addView(inlineRow("倒计时显示方向", segOrientation.view, 0));

        LinearLayout switchRow = new LinearLayout(this);
        switchRow.setOrientation(LinearLayout.HORIZONTAL);
        switchRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams switchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        switchLp.topMargin = dp(14);
        groupOrientation.addView(switchRow, switchLp);

        LinearLayout awakeCol = new LinearLayout(this);
        awakeCol.setOrientation(LinearLayout.HORIZONTAL);
        awakeCol.setGravity(Gravity.CENTER_VERTICAL);
        awakeCol.setMinimumHeight(dp(48));
        switchRow.addView(awakeCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        awakeCol.addView(Ui.tv(this, "屏幕常亮", 14, COLOR_TEXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        switchKeepAwake = new Ui.SwitchView(this);
        attachTapHaptic(switchKeepAwake);
        switchKeepAwake.setOnChecked(checked -> {
            settings.keepAwake = checked;
            persistSettings();
            updateKeepAwake();
        });
        awakeCol.addView(switchKeepAwake, new LinearLayout.LayoutParams(dp(46), dp(28)));

        LinearLayout shiftCol = new LinearLayout(this);
        shiftCol.setOrientation(LinearLayout.HORIZONTAL);
        shiftCol.setGravity(Gravity.CENTER_VERTICAL);
        shiftCol.setMinimumHeight(dp(48));
        LinearLayout.LayoutParams shiftColLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        shiftColLp.leftMargin = dp(18);
        switchRow.addView(shiftCol, shiftColLp);
        shiftCol.addView(Ui.tv(this, "防烧屏位移", 14, COLOR_TEXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        switchPixelShift = new Ui.SwitchView(this);
        attachTapHaptic(switchPixelShift);
        switchPixelShift.setOnChecked(checked -> {
            settings.pixelShift = checked;
            persistSettings();
        });
        shiftCol.addView(switchPixelShift, new LinearLayout.LayoutParams(dp(46), dp(28)));

        segShift = buildSeg(new String[]{"10", "20", "custom"},
                new String[]{"10秒", "20秒", "自定义"}, value -> {
                    settings.shiftInterval = value;
                    persistSettings();
                    renderSettings();
                });
        groupOrientation.addView(inlineRow("位移间隔", segShift.view, 14));

        shiftCustomWrap = new LinearLayout(this);
        shiftCustomWrap.setOrientation(LinearLayout.HORIZONTAL);
        shiftCustomWrap.setGravity(Gravity.CENTER_VERTICAL);
        shiftCustomWrap.setVisibility(View.GONE);
        LinearLayout.LayoutParams sclp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sclp.topMargin = dp(10);
        groupOrientation.addView(shiftCustomWrap, sclp);
        shiftCustomWrap.addView(Ui.tv(this, "每", 12, 0xFF888888));
        shiftCustomInput = numberInput();
        LinearLayout.LayoutParams scilp = new LinearLayout.LayoutParams(dp(90), dp(42));
        scilp.leftMargin = dp(10);
        scilp.rightMargin = dp(10);
        shiftCustomWrap.addView(shiftCustomInput, scilp);
        shiftCustomWrap.addView(Ui.tv(this, "秒轻微移动", 12, 0xFF888888));
        shiftCustomInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) commitShiftCustom();
        });
        shiftCustomInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hapticTap();
                commitShiftCustom();
                return true;
            }
            return false;
        });
        settingsContent.addView(groupOrientation);

        LinearLayout groupWeight = settingsGroup();
        LinearLayout weightRow = new LinearLayout(this);
        weightRow.setOrientation(LinearLayout.HORIZONTAL);
        weightRow.setGravity(Gravity.CENTER_VERTICAL);
        weightRow.setMinimumHeight(dp(42));
        weightRow.addView(Ui.tv(this, "数字粗细", 14, COLOR_TEXT));

        digitWeightSeek = new SeekBar(this);
        digitWeightSeek.setMax(4);
        digitWeightSeek.setMinimumHeight(dp(32));
        digitWeightSeek.setProgressTintList(ColorStateList.valueOf(0xFFDDDDDD));
        digitWeightSeek.setThumbTintList(ColorStateList.valueOf(0xFFDDDDDD));
        digitWeightSeek.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF333333));
        digitWeightSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                settings.digitWeight = progress + 1;
                hapticTick();
                digitWeightValue.setText(new String[]{"最细", "偏细", "适中", "偏粗", "最粗"}[progress]);
                applyDigitSettings();
                persistSettings();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        seekLp.leftMargin = dp(10);
        weightRow.addView(digitWeightSeek, seekLp);

        digitWeightValue = Ui.tv(this, "", 12, COLOR_DIM);
        digitWeightValue.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        digitWeightValue.setMinWidth(dp(38));
        LinearLayout.LayoutParams weightValueLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        weightValueLp.leftMargin = dp(6);
        weightRow.addView(digitWeightValue, weightValueLp);

        solidButton = compactButton("实体", 0);
        solidButton.setContentDescription("纯实体数字");
        solidButton.setOnClickListener(v -> {
            settings.solidDigits = !settings.solidDigits;
            persistSettings();
            renderSettings();
        });
        LinearLayout.LayoutParams solidLp = new LinearLayout.LayoutParams(dp(46), dp(32));
        solidLp.leftMargin = dp(10);
        weightRow.addView(solidButton, solidLp);
        groupWeight.addView(weightRow);
        settingsContent.addView(groupWeight);

        LinearLayout groupBulk = settingsGroup();
        LinearLayout bulkHead = new LinearLayout(this);
        bulkHead.setOrientation(LinearLayout.HORIZONTAL);
        bulkHead.setGravity(Gravity.CENTER_VERTICAL);
        bulkHead.setMinimumHeight(dp(36));
        bulkHead.addView(Ui.tv(this, "纯文本快捷设置", 14, COLOR_TEXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView restoreDefaults = compactButton("默认模板", 0);
        restoreDefaults.setOnClickListener(v -> restoreDefaultTemplate());
        bulkHead.addView(restoreDefaults);
        TextView apply = compactButton("应用", 1);
        apply.setOnClickListener(v -> applyBulkText());
        LinearLayout.LayoutParams applyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        applyLp.leftMargin = dp(7);
        bulkHead.addView(apply, applyLp);
        groupBulk.addView(bulkHead);

        TextView bulkDesc = Ui.tv(this, "输入“标签”和“时间”两行，空格、逗号或顿号分隔；应用后替换首页按钮，历史记录不受影响。", 11, 0xFF6F6F6F);
        bulkDesc.setLineSpacing(0, 1.5f);
        LinearLayout.LayoutParams bdlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bdlp.topMargin = dp(5);
        groupBulk.addView(bulkDesc, bdlp);

        bulkText = new EditText(this);
        bulkText.setHint("标签 学习 娱乐 日常 休息 其他\n时间 5 10 15 20 25 30 40 45 60 120");
        bulkText.setHintTextColor(0xFF555555);
        bulkText.setTextColor(0xFFDDDDDD);
        bulkText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        bulkText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        bulkText.setGravity(Gravity.TOP | Gravity.START);
        bulkText.setMinHeight(dp(92));
        bulkText.setPadding(dp(12), dp(10), dp(12), dp(10));
        bulkText.setBackground(Ui.box(this, Color.BLACK, 0xFF333333, 1, 4));
        bulkText.setLineSpacing(dpf(5), 1.0f);
        LinearLayout.LayoutParams btlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btlp.topMargin = dp(10);
        groupBulk.addView(bulkText, btlp);
        settingsContent.addView(groupBulk);

        LinearLayout groupClear = settingsGroup();
        LinearLayout clearRow = new LinearLayout(this);
        clearRow.setOrientation(LinearLayout.HORIZONTAL);
        clearRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView clearDesc = Ui.tv(this, "删除记录、个性签名、自定义按钮和全部设置，无法恢复。", 11, 0xFF6F6F6F);
        clearDesc.setLineSpacing(0, 1.4f);
        clearRow.addView(clearDesc, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        clearDataButton = compactButton("清除所有数据", 2);
        clearDataButton.setOnClickListener(v -> requestClearAllData());
        LinearLayout.LayoutParams cblp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cblp.leftMargin = dp(10);
        clearRow.addView(clearDataButton, cblp);
        groupClear.addView(clearRow);
        settingsContent.addView(groupClear);
    }

    private LinearLayout settingsGroup() {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setPadding(0, dp(2), 0, dp(20));
        g.setBackground(new Ui.LineDrawable(this, Color.BLACK, COLOR_SOFT_LINE, 1));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(18);
        g.setLayoutParams(p);
        return g;
    }

    private LinearLayout inlineRow(String title, View control, int topMarginDp) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(42));
        row.addView(Ui.tv(this, title, 14, COLOR_TEXT));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clp.leftMargin = dp(10);
        row.addView(control, clp);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(topMarginDp);
        row.setLayoutParams(rlp);
        return row;
    }

    private TextView compactButton(String label, int style) {
        TextView b = Ui.tv(this, label, 12, 0xFFBBBBBB);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setMinHeight(dp(32));
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setClickable(true);
        styleCompactButton(b, style == 1, style == 2);
        attachTapHaptic(b);
        return b;
    }

    private void styleCompactButton(TextView b, boolean on, boolean danger) {
        b.setTextColor(on ? 0xFF050505 : danger ? 0xFFC7C7C7 : 0xFFBBBBBB);
        int fill = on ? 0xFFE3E3E3 : Color.BLACK;
        int stroke = on ? 0xFFE3E3E3 : danger ? 0xFF555555 : 0xFF3A3A3A;
        b.setBackground(Ui.box(this, fill, stroke, 1, 4));
    }

    private void restoreDefaultTemplate() {
        homeMatters = new ArrayList<>();
        Collections.addAll(homeMatters, Models.BASE_MATTERS);
        homeTimes = new ArrayList<>();
        for (int v : Models.BASE_TIMES) homeTimes.add(v);
        customMatters.clear();
        customTimes.clear();
        if (selectedMatter != null && !homeMatters.contains(selectedMatter)) selectedMatter = null;
        persistHome();
        persistMatters();
        persistTimes();
        refreshBulkText();
        renderHome();
        renderRecords();
        toast("已恢复默认模板");
    }

    private EditText numberInput() {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        e.setTextColor(0xFFDDDDDD);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        e.setSingleLine(true);
        e.setGravity(Gravity.CENTER_VERTICAL);
        e.setPadding(dp(10), 0, dp(10), 0);
        e.setMinHeight(dp(42));
        e.setBackground(Ui.box(this, Color.BLACK, 0xFF333333, 1, 4));
        return e;
    }

    private void commitRepeatCustom() {
        int value = clampInt(parseIntSafe(repeatCustomInput.getText().toString(), 20), 1, 3600);
        settings.repeatCustom = value;
        repeatCustomInput.setText(String.valueOf(value));
        persistSettings();
    }

    private void commitShiftCustom() {
        int value = clampInt(parseIntSafe(shiftCustomInput.getText().toString(), 30), 1, 300);
        settings.shiftCustom = value;
        shiftCustomInput.setText(String.valueOf(value));
        persistSettings();
    }

    private interface StrCallback {
        void on(String value);
    }

    private static class Seg {
        String[] values;
        LinearLayout view;
        final Map<String, View> buttons = new HashMap<>();
        final Map<String, TextView> textViews = new HashMap<>();
        final Map<String, Ui.IconView> iconViews = new HashMap<>();
        StrCallback callback;
        String selected;

        void setSelected(String value) {
            selected = value;
            for (int i = 0; i < values.length; i++) {
                View b = buttons.get(values[i]);
                if (b == null) continue;
                boolean on = values[i].equals(value);
                b.setBackground(Ui.box(b.getContext(), on ? 0xFFDDDDDD : Color.BLACK,
                        on ? 0xFFDDDDDD : COLOR_LINE, 1, 4));
                TextView t = textViews.get(values[i]);
                if (t != null) t.setTextColor(on ? 0xFF050505 : 0xFF999999);
                Ui.IconView icon = iconViews.get(values[i]);
                if (icon != null) icon.setIconColor(on ? 0xFF0A0A0A : 0xFF9A9A9A);
            }
        }
    }

    private Seg buildSeg(String[] values, String[] labels, StrCallback callback) {
        return buildSeg(values, labels, null, callback);
    }

    private Seg buildSeg(String[] values, String[] labels, int[] icons, StrCallback callback) {
        Seg seg = new Seg();
        seg.values = values;
        seg.callback = callback;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(12);
        row.setLayoutParams(rlp);
        seg.view = row;
        for (int i = 0; i < values.length; i++) {
            final String value = values[i];
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.HORIZONTAL);
            cell.setGravity(Gravity.CENTER);
            cell.setMinimumHeight(dp(42));
            cell.setPadding(dp(6), dp(8), dp(6), dp(8));
            cell.setBackground(Ui.box(this, Color.BLACK, COLOR_LINE, 1, 4));
            cell.setClickable(true);
            cell.setContentDescription(labels[i]);
            cell.setOnClickListener(v -> {
                hapticTap();
                if (seg.callback != null) seg.callback.on(value);
            });
            if (icons != null && icons[i] >= 0) {
                Ui.IconView icon = new Ui.IconView(this, icons[i]);
                cell.addView(icon, new LinearLayout.LayoutParams(dp(21), dp(21)));
                seg.iconViews.put(value, icon);
            } else {
                TextView t = Ui.tv(this, labels[i], 12, 0xFF999999);
                t.setGravity(Gravity.CENTER);
                t.setSingleLine(true);
                cell.addView(t);
                seg.textViews.put(value, t);
            }
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) p.leftMargin = dp(7);
            row.addView(cell, p);
            seg.buttons.put(value, cell);
        }
        return seg;
    }

    private void renderSettings() {
        switchHaptics.setChecked(settings.haptics);
        switchKeepAwake.setChecked(settings.keepAwake);
        switchPixelShift.setChecked(settings.pixelShift);
        segReminder.setSelected(settings.reminder);
        segRepeat.setSelected(settings.repeat);
        segOrientation.setSelected(settings.orientation);
        segShift.setSelected(settings.shiftInterval);
        repeatCustomWrap.setVisibility("custom".equals(settings.repeat) ? View.VISIBLE : View.GONE);
        shiftCustomWrap.setVisibility("custom".equals(settings.shiftInterval) ? View.VISIBLE : View.GONE);
        if (!repeatCustomInput.hasFocus()) repeatCustomInput.setText(String.valueOf(settings.repeatCustom));
        if (!shiftCustomInput.hasFocus()) shiftCustomInput.setText(String.valueOf(settings.shiftCustom));
        digitWeightSeek.setProgress(settings.digitWeight - 1);
        digitWeightValue.setText(new String[]{"最细", "偏细", "适中", "偏粗", "最粗"}[settings.digitWeight - 1]);
        styleCompactButton(solidButton, settings.solidDigits, false);
        applyDigitSettings();
    }

    private void refreshBulkText() {
        List<String> labels = new ArrayList<>(homeMatters);
        for (Models.Matter m : customMatters) labels.add(m.name);
        List<Integer> times = new ArrayList<>(homeTimes);
        for (Models.TimeChip t : customTimes) times.add(t.minutes);
        StringBuilder sb = new StringBuilder("标签 ");
        for (int i = 0; i < labels.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(labels.get(i));
        }
        sb.append("\n时间 ");
        for (int i = 0; i < times.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(times.get(i));
        }
        bulkText.setText(sb.toString());
    }

    private void applyBulkText() {
        String[] lines = bulkText.getText().toString().split("\\r?\\n");
        List<String> labels = new ArrayList<>();
        List<Integer> times = new ArrayList<>();
        boolean foundLabels = false;
        boolean foundTimes = false;
        Pattern labelPattern = Pattern.compile("^(?:标签|labels?)\\s*[:：]?\\s*(.*)$", Pattern.CASE_INSENSITIVE);
        Pattern timePattern = Pattern.compile("^(?:时间|times?)\\s*[:：]?\\s*(.*)$", Pattern.CASE_INSENSITIVE);
        Pattern numberPattern = Pattern.compile("\\d+(?:\\.\\d+)?");
        for (String raw : lines) {
            String line = raw.trim().replaceFirst("^[-*•]\\s*", "");
            if (line.isEmpty()) continue;
            Matcher lm = labelPattern.matcher(line);
            if (lm.matches()) {
                foundLabels = true;
                labels.addAll(splitTokens(lm.group(1)));
            }
            Matcher tm = timePattern.matcher(line);
            if (tm.matches()) {
                foundTimes = true;
                for (String token : splitTokens(tm.group(1))) {
                    Matcher nm = numberPattern.matcher(token);
                    int value = 0;
                    if (nm.find()) value = Math.round(Float.parseFloat(nm.group()));
                    if (value >= 1 && value <= 999) times.add(value);
                }
            }
        }
        labels = new ArrayList<>(new LinkedHashSet<>(labels));
        times = new ArrayList<>(new LinkedHashSet<>(times));
        if (!foundLabels && !foundTimes) {
            toast("未识别到“标签”或“时间”行");
            return;
        }
        if (foundLabels && labels.isEmpty()) {
            toast("标签行没有有效内容");
            return;
        }
        if (foundTimes && times.isEmpty()) {
            toast("时间行没有有效数字");
            return;
        }
        if (foundLabels) homeMatters = labels;
        if (foundTimes) homeTimes = times;
        customMatters.clear();
        customTimes.clear();
        if (selectedMatter != null && !homeMatters.contains(selectedMatter)) selectedMatter = null;
        persistHome();
        persistMatters();
        persistTimes();
        refreshBulkText();
        renderHome();
        renderRecords();
        toast("首页按钮已更新");
    }

    private List<String> splitTokens(String value) {
        List<String> out = new ArrayList<>();
        for (String token : value.split("[\\s,，、;；]+")) {
            String t = token.trim().replaceAll("^[\"'“”]+|[\"'“”]+$", "");
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private void applyDigitSettings() {
        if (digits == null) return;
        Configuration cfg = getResources().getConfiguration();
        boolean portrait = cfg.orientation == Configuration.ORIENTATION_PORTRAIT;
        digits.setSolid(settings.solidDigits);
        digits.setMetrics(portrait, cfg.screenWidthDp, settings.digitWeight);
        ViewGroup.LayoutParams lp = digits.getLayoutParams();
        if (lp != null) {
            int h = dp(portrait ? 220 : 190);
            if (lp.height != h) {
                lp.height = h;
                digits.setLayoutParams(lp);
            }
        }
        if (countdownSignature != null) {
            ViewGroup.LayoutParams slp = countdownSignature.getLayoutParams();
            if (slp != null) {
                int w = (int) (cfg.screenWidthDp * 0.85f * getResources().getDisplayMetrics().density);
                if (slp.width != w) {
                    slp.width = w;
                    countdownSignature.setLayoutParams(slp);
                }
            }
        }
    }

    // ------------------------------------------------------------- countdown

    private void buildCountdown() {
        countdownRoot = new LinearLayout(this);
        countdownRoot.setOrientation(LinearLayout.VERTICAL);
        countdownRoot.setBackgroundColor(Color.BLACK);
        countdownRoot.setVisibility(View.GONE);
        root.addView(countdownRoot, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout stage = new FrameLayout(this);
        stage.setPadding(dp(38), dp(24), dp(38), dp(12));
        countdownRoot.addView(stage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        shifter = new LinearLayout(this);
        shifter.setOrientation(LinearLayout.VERTICAL);
        shifter.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams shlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shlp.gravity = Gravity.CENTER;
        stage.addView(shifter, shlp);

        countdownMatter = Ui.tv(this, "", 13, COLOR_DIM);
        countdownMatter.setGravity(Gravity.CENTER);
        countdownMatter.setLetterSpacing(0.12f);
        shifter.addView(countdownMatter, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        digits = new Ui.DigitView(this);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(190));
        dlp.topMargin = dp(8);
        dlp.bottomMargin = dp(2);
        shifter.addView(digits, dlp);

        countdownSignature = Ui.tv(this, "", 12, 0xFF8C8C8C);
        countdownSignature.setGravity(Gravity.CENTER);
        countdownSignature.setLineSpacing(0, 1.7f);
        countdownSignature.setPadding(dp(24), 0, dp(24), 0);
        shifter.addView(countdownSignature, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        countdownStatus = Ui.tv(this, "", 11, 0xFF666666);
        countdownStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cslp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cslp.topMargin = dp(5);
        shifter.addView(countdownStatus, cslp);

        reminderInfo = Ui.tv(this, "时间到了", 13, 0xFFC8C8C8);
        reminderInfo.setGravity(Gravity.CENTER);
        reminderInfo.setVisibility(View.GONE);
        LinearLayout.LayoutParams rilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rilp.topMargin = dp(8);
        shifter.addView(reminderInfo, rilp);

        timerControls = new LinearLayout(this);
        timerControls.setOrientation(LinearLayout.HORIZONTAL);
        timerControls.setGravity(Gravity.CENTER);
        timerControls.setPadding(dp(28), dp(8), dp(28), dp(20));
        countdownRoot.addView(timerControls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        pauseButton = new FrameLayout(this);
        pauseButton.setClickable(true);
        pauseButton.setContentDescription("暂停");
        pauseIcon = new Ui.IconView(this, Ui.IconView.PAUSE);
        pauseIcon.setIconColor(0xFFB9B9B9);
        FrameLayout.LayoutParams pilp = new FrameLayout.LayoutParams(dp(22), dp(22));
        pilp.gravity = Gravity.CENTER;
        pauseButton.addView(pauseIcon, pilp);
        LinearLayout.LayoutParams pblp = new LinearLayout.LayoutParams(dp(52), dp(44));
        pblp.leftMargin = dp(10);
        pblp.rightMargin = dp(10);
        timerControls.addView(pauseButton, pblp);
        pauseButton.setOnClickListener(v -> {
            hapticTap();
            togglePause();
        });

        endButton = new FrameLayout(this);
        endButton.setClickable(true);
        endButton.setContentDescription("结束");
        endIcon = new Ui.IconView(this, Ui.IconView.END);
        endIcon.setIconColor(0xFFB9B9B9);
        FrameLayout.LayoutParams eilp = new FrameLayout.LayoutParams(dp(22), dp(22));
        eilp.gravity = Gravity.CENTER;
        endButton.addView(endIcon, eilp);
        LinearLayout.LayoutParams eblp = new LinearLayout.LayoutParams(dp(52), dp(44));
        eblp.leftMargin = dp(10);
        eblp.rightMargin = dp(10);
        timerControls.addView(endButton, eblp);
        endButton.setOnClickListener(v -> {
            hapticTap();
            requestEarlyEnd();
        });

        reminderControls = new LinearLayout(this);
        reminderControls.setOrientation(LinearLayout.HORIZONTAL);
        reminderControls.setGravity(Gravity.CENTER);
        reminderControls.setPadding(dp(28), dp(8), dp(28), dp(20));
        reminderControls.setVisibility(View.GONE);
        countdownRoot.addView(reminderControls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView confirm = Ui.tv(this, "确定", 16, 0xFF050505);
        confirm.setGravity(Gravity.CENTER);
        confirm.setMinWidth(dp(116));
        confirm.setMinHeight(dp(44));
        confirm.setPadding(dp(20), 0, dp(20), 0);
        confirm.setBackground(Ui.box(this, 0xFFE3E3E3, 0xFFE3E3E3, 1, 4));
        confirm.setClickable(true);
        attachTapHaptic(confirm);
        confirm.setOnClickListener(v -> completeRecord(true));
        reminderControls.addView(confirm);
    }

    private void enterCountdown() {
        if (active == null) return;
        uiApplyOrientation();
        applyDigitSettings();
        mainColumn.setVisibility(View.GONE);
        countdownRoot.setVisibility(View.VISIBLE);
        countdownMatter.setText(active.matter);
        countdownSignature.setText(active.signature == null ? "" : active.signature);
        resetPixelShift();
        nextShiftAt = System.currentTimeMillis() + shiftSeconds() * 1000L;
        lastSecond = -1;
        lastReminderUiAt = 0;
        updateTimerDisplay();
        if (active.finished) updateReminderInfo();
        hideSystemBars();
        updateKeepAwake();
        startTicking();
        tick();
    }

    private void startTicking() {
        stopTicking();
        tickRunnable = new Runnable() {
            @Override
            public void run() {
                tick();
                if (active != null) handler.postDelayed(this, 200);
            }
        };
        handler.postDelayed(tickRunnable, 200);
    }

    private void stopTicking() {
        if (tickRunnable != null) {
            handler.removeCallbacks(tickRunnable);
            tickRunnable = null;
        }
    }

    private void tick() {
        if (active == null) return;
        long now = System.currentTimeMillis();
        if (active.running) {
            active.remainingMs = Math.max(0, active.endAt - now);
            if (active.remainingMs <= 0) {
                finishTimer(now);
                return;
            }
        }
        long displaySecond = (active.remainingMs + 999) / 1000;
        if (displaySecond != lastSecond) {
            updateTimerDisplay();
            lastSecond = displaySecond;
        }
        if (active.finished && Reminder.lastFireAt != lastReminderUiAt) {
            lastReminderUiAt = Reminder.lastFireAt;
            if (lastReminderUiAt > 0) updateReminderInfo();
        }
        if (settings.pixelShift && !active.finished && now >= nextShiftAt) {
            int x = RANDOM.nextInt(9) - 4;
            int y = RANDOM.nextInt(7) - 3;
            shifter.setTranslationX(dp(x));
            shifter.setTranslationY(dp(y));
            pauseIcon.setTranslationX(dp(x));
            pauseIcon.setTranslationY(dp(y));
            endIcon.setTranslationX(dp(x));
            endIcon.setTranslationY(dp(y));
            nextShiftAt = now + shiftSeconds() * 1000L;
        }
    }

    private void updateTimerDisplay() {
        if (active == null) return;
        String text = formatTimer(active.remainingMs);
        digits.setState(text, isPortraitNow(), getResources().getConfiguration().screenWidthDp, settings.digitWeight);
        countdownStatus.setText(active.finished ? "等待确认，不再计时时长" : active.running ? "" : "已暂停");
        pauseIcon.setKind(active.running ? Ui.IconView.PAUSE : Ui.IconView.PLAY);
        pauseButton.setContentDescription(active.running ? "暂停" : "继续");
        timerControls.setVisibility(active.finished ? View.GONE : View.VISIBLE);
        reminderControls.setVisibility(active.finished ? View.VISIBLE : View.GONE);
        reminderInfo.setVisibility(active.finished ? View.VISIBLE : View.GONE);
        if (active.finished && reminderInfo.getText().length() == 0) reminderInfo.setText("时间到了");
    }

    private boolean isPortraitNow() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    private void togglePause() {
        if (active == null || active.finished) return;
        long now = System.currentTimeMillis();
        if (active.running) {
            captureElapsed(now);
            active.running = false;
        } else {
            active.running = true;
            active.lastStartAt = now;
            active.endAt = now + active.remainingMs;
            active.segments.add(new Models.Segment(now, -1));
        }
        lastSecond = -1;
        updateTimerDisplay();
        saveActive();
        if (active.running) Reminder.scheduleFinish(this, active.endAt);
        else Reminder.cancelAlarm(this);
    }

    private void captureElapsed(long now) {
        if (active == null || !active.running) return;
        long countedUntil = Math.min(now, active.endAt);
        active.elapsedMs += Math.max(0, countedUntil - active.lastStartAt);
        active.lastStartAt = countedUntil;
        active.remainingMs = Math.max(0, active.endAt - now);
        if (!active.segments.isEmpty()) {
            active.segments.get(active.segments.size() - 1).end = countedUntil;
        }
    }

    private void finishTimer(long now) {
        if (active == null || active.finished) return;
        active.elapsedMs += Math.max(0, active.endAt - active.lastStartAt);
        active.lastStartAt = active.endAt;
        if (!active.segments.isEmpty()) active.segments.get(active.segments.size() - 1).end = active.endAt;
        active.remainingMs = 0;
        active.running = false;
        active.finished = true;
        active.nextReminderAt = now;
        lastSecond = -1;
        saveActive();
        Reminder.fire(this);
        updateTimerDisplay();
        updateReminderInfo();
    }

    private void updateReminderInfo() {
        if (active == null || !active.finished) return;
        long shown = Reminder.lastFireAt > 0 ? Reminder.lastFireAt : System.currentTimeMillis();
        reminderInfo.setText("时间到了 · " + clockTime(shown, true) + " 已提醒");
        countdownStatus.setText("每 " + repeatSeconds() + " 秒提醒，点击“确定”停止");
    }

    private void requestEarlyEnd() {
        if (active == null || active.finished) return;
        long projected = active.elapsedMs + (active.running
                ? Math.max(0, Math.min(System.currentTimeMillis(), active.endAt) - active.lastStartAt) : 0);
        LinearLayout content = beginModal();
        addDialogTitle(content, "提前结束？");
        addDialogCopy(content, "“" + active.matter + "”已实际计时 " + formatDuration(projected) + "。结束后不能再继续本次倒计时。");
        LinearLayout actions = addDialogActions(content);
        TextView cancel = dialogButton("继续计时", 1);
        cancel.setOnClickListener(v -> closeModal());
        actions.addView(cancel);
        TextView confirm = dialogButton("确认结束", 2);
        confirm.setOnClickListener(v -> askSaveRecord());
        actions.addView(confirm);
    }

    private void askSaveRecord() {
        if (active == null) return;
        captureElapsed(System.currentTimeMillis());
        active.running = false;
        LinearLayout content = beginModal();
        addDialogTitle(content, "保存这段记录？");
        addDialogCopy(content, "实际计时 " + formatDuration(active.elapsedMs) + "，暂停时间未计入。");
        LinearLayout actions = addDialogActions(content);
        TextView discard = dialogButton("丢弃", 2);
        discard.setOnClickListener(v -> completeRecord(false));
        actions.addView(discard);
        TextView save = dialogButton("保存", 1);
        save.setOnClickListener(v -> completeRecord(true));
        actions.addView(save);
    }

    private void completeRecord(boolean save) {
        if (active == null) return;
        if (save) {
            long end = active.finished ? active.endAt : System.currentTimeMillis();
            Models.RecordItem r = new Models.RecordItem();
            r.id = "record-" + System.currentTimeMillis();
            r.matter = active.matter;
            r.start = active.startedAt;
            r.end = end;
            r.duration = Math.max(0, active.elapsedMs);
            r.segments = new ArrayList<>(active.segments);
            if (r.segments.isEmpty()) r.segments.add(new Models.Segment(active.startedAt, end));
            records.add(r);
            persistRecords();
        }
        exitCountdown();
        toast(save ? "记录已保存" : "本次记录已丢弃");
    }

    private void exitCountdown() {
        stopTicking();
        Reminder.cancelAll(this);
        lastReminderUiAt = 0;
        active = null;
        saveActive();
        releaseKeepAwake();
        closeModal();
        countdownRoot.setVisibility(View.GONE);
        mainColumn.setVisibility(View.VISIBLE);
        resetPixelShift();
        showSystemBars();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        renderHome();
        renderRecords();
    }

    private void uiApplyOrientation() {
        if ("portrait".equals(settings.orientation)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else if ("follow".equals(settings.orientation)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    private long repeatSeconds() {
        return "custom".equals(settings.repeat) ? settings.repeatCustom : parseIntSafe(settings.repeat, 15);
    }

    private long shiftSeconds() {
        return "custom".equals(settings.shiftInterval) ? settings.shiftCustom : parseIntSafe(settings.shiftInterval, 10);
    }

    private void resetPixelShift() {
        if (shifter != null) {
            shifter.setTranslationX(0);
            shifter.setTranslationY(0);
        }
        if (pauseIcon != null) {
            pauseIcon.setTranslationX(0);
            pauseIcon.setTranslationY(0);
        }
        if (endIcon != null) {
            endIcon.setTranslationX(0);
            endIcon.setTranslationY(0);
        }
    }

    private void updateKeepAwake() {
        boolean activeVisible = active != null && countdownRoot != null && countdownRoot.getVisibility() == View.VISIBLE;
        if (settings.keepAwake && activeVisible) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void releaseKeepAwake() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    private void showSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) c.show(WindowInsets.Type.systemBars());
        }
        getWindow().getDecorView().setSystemUiVisibility(0);
    }

    // ---------------------------------------------------------------- modals

    private void buildOverlays() {
        deleteTarget = new TextView(this);
        deleteTarget.setText("拖到这里删除");
        deleteTarget.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        deleteTarget.setTextColor(COLOR_DIM);
        deleteTarget.setGravity(Gravity.CENTER);
        deleteTarget.setMinWidth(dp(142));
        deleteTarget.setMinHeight(dp(44));
        deleteTarget.setPadding(dp(18), dp(12), dp(18), dp(12));
        deleteTarget.setVisibility(View.GONE);
        deleteTarget.setBackground(dashedTargetBg(false));
        FrameLayout.LayoutParams dtlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dtlp.bottomMargin = dp(86) + insetBottom;
        root.addView(deleteTarget, dtlp);
        updateDeleteTargetMargin();

        toastView = new TextView(this);
        toastView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        toastView.setTextColor(0xFFEEEEEE);
        toastView.setGravity(Gravity.CENTER);
        toastView.setPadding(dp(16), dp(11), dp(16), dp(11));
        toastView.setSingleLine(true);
        toastView.setBackground(Ui.box(this, 0xFF171717, 0xFF393939, 1, 4));
        toastView.setVisibility(View.GONE);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        tlp.bottomMargin = dp(90) + insetBottom;
        root.addView(toastView, tlp);

        modalBackdrop = new FrameLayout(this);
        modalBackdrop.setBackgroundColor(0xD1000000);
        modalBackdrop.setVisibility(View.GONE);
        modalBackdrop.setClickable(true);
        root.addView(modalBackdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        modalScroll = new Ui.MaxHeightScrollView(this);
        modalScroll.setFillViewport(false);
        modalScroll.setClipToPadding(false);
        modalScroll.setMaxHeightPx((int) (getResources().getDisplayMetrics().heightPixels * 0.88f));
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        modalBackdrop.addView(modalScroll, mlp);

        modalPanel = new LinearLayout(this);
        modalPanel.setOrientation(LinearLayout.VERTICAL);
        modalPanel.setPadding(dp(22), dp(22), dp(22), dp(22));
        modalPanel.setBackground(Ui.box(this, Color.BLACK, 0xFF3A3A3A, 1, 6));
        modalScroll.addView(modalPanel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private GradientDrawable dashedTargetBg(boolean over) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(over ? 0xFFE5E5E5 : Color.BLACK);
        d.setCornerRadius(dpf(4));
        d.setStroke(Math.max(1, dp(1)), over ? 0xFFE5E5E5 : 0xFF555555, dp(4), dp(3));
        return d;
    }

    private void updateDeleteTargetMargin() {
        if (deleteTarget == null) return;
        ViewGroup.LayoutParams raw = deleteTarget.getLayoutParams();
        if (raw instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) raw).bottomMargin = dp(86) + insetBottom;
            deleteTarget.setLayoutParams(raw);
        }
    }

    private LinearLayout beginModal() {
        int width = Math.min(dp(340), getResources().getDisplayMetrics().widthPixels - dp(48));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) modalScroll.getLayoutParams();
        if (lp.width != width) {
            lp.width = width;
            modalScroll.setLayoutParams(lp);
        }
        modalPanel.removeAllViews();
        modalBackdrop.setVisibility(View.VISIBLE);
        return modalPanel;
    }

    private void closeModal() {
        if (modalBackdrop == null) return;
        modalBackdrop.setVisibility(View.GONE);
        modalPanel.removeAllViews();
        View focus = getCurrentFocus();
        if (focus instanceof EditText) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            focus.clearFocus();
        }
    }

    private void addDialogTitle(LinearLayout content, String title) {
        TextView t = Ui.tv(this, title, 17, COLOR_TEXT);
        t.setTypeface(MEDIUM);
        content.addView(t);
    }

    private void addDialogCopy(LinearLayout content, String text) {
        TextView t = Ui.tv(this, text, 12, 0xFF888888);
        t.setLineSpacing(0, 1.7f);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(10);
        content.addView(t, p);
    }

    private EditText dialogInput(int inputType, String hint, int maxLength, boolean multiline) {
        EditText e = new EditText(this);
        e.setInputType(inputType);
        e.setHint(hint);
        e.setHintTextColor(0xFF555555);
        e.setTextColor(0xFFEEEEEE);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setBackground(Ui.box(this, Color.BLACK, 0xFF444444, 1, 4));
        if (maxLength > 0) e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        if (multiline) {
            e.setGravity(Gravity.TOP | Gravity.START);
            e.setMinHeight(dp(90));
        } else {
            e.setSingleLine(true);
            e.setMinHeight(dp(46));
            e.setGravity(Gravity.CENTER_VERTICAL);
        }
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(18);
        e.setLayoutParams(p);
        return e;
    }

    private LinearLayout addDialogActions(LinearLayout content) {
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(20);
        content.addView(actions, p);
        return actions;
    }

    private TextView dialogButton(String label, int style) {
        TextView b = Ui.tv(this, label, 16, style == 1 ? 0xFF050505 : style == 2 ? 0xFFC7C7C7 : 0xFFBBBBBB);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(42));
        b.setPadding(dp(15), 0, dp(15), 0);
        b.setClickable(true);
        int fill = style == 1 ? 0xFFE3E3E3 : Color.BLACK;
        int stroke = style == 1 ? 0xFFE3E3E3 : style == 2 ? 0xFF555555 : 0xFF3A3A3A;
        b.setBackground(Ui.box(this, fill, stroke, 1, 4));
        attachTapHaptic(b);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.leftMargin = dp(9);
        b.setLayoutParams(p);
        return b;
    }

    private void openSignatureEditor() {
        LinearLayout content = beginModal();
        addDialogTitle(content, "个性签名");
        addDialogCopy(content, "这句话会显示在倒计时数字下方，留空则不显示。");
        EditText input = dialogInput(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                "写一句给自己的话", 40, true);
        input.setText(signature);
        content.addView(input);
        TextView hint = Ui.tv(this, "最多 40 个字", 11, 0xFF666666);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(7);
        content.addView(hint, hlp);
        LinearLayout actions = addDialogActions(content);
        TextView cancel = dialogButton("取消", 0);
        cancel.setOnClickListener(v -> closeModal());
        actions.addView(cancel);
        TextView save = dialogButton("保存", 1);
        save.setOnClickListener(v -> {
            signature = input.getText().toString().trim();
            persistSignature();
            closeModal();
            renderHome();
        });
        actions.addView(save);
        focusInput(input);
    }

    private void openMatterManager() {
        LinearLayout content = beginModal();
        addDialogTitle(content, "添加标签");
        addDialogCopy(content, "可以连续添加多个，添加完成后点关闭。新标签会显示在首页。");
        EditText input = dialogInput(InputType.TYPE_CLASS_TEXT, "输入标签名称", 12, false);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        content.addView(input);

        final TextView addedCount = Ui.tv(this, "", 11, 0xFF777777);
        addedCount.setVisibility(View.GONE);
        LinearLayout.LayoutParams aclp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        aclp.topMargin = dp(14);
        content.addView(addedCount, aclp);

        final Ui.FlowLayout addedFlow = new Ui.FlowLayout(this, 7, 7);
        addedFlow.setVisibility(View.GONE);
        LinearLayout.LayoutParams aflp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        aflp.topMargin = dp(8);
        content.addView(addedFlow, aflp);

        final int[] added = {0};
        LinearLayout actions = addDialogActions(content);
        TextView close = dialogButton("关闭", 0);
        close.setOnClickListener(v -> {
            closeModal();
            renderHome();
        });
        actions.addView(close);
        TextView add = dialogButton("添加", 1);
        Runnable addMatter = () -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                toast("请输入标签名称");
                return;
            }
            if (isMatterNameTaken(name, null)) {
                toast("标签已存在");
                return;
            }
            customMatters.add(new Models.Matter("matter-" + System.currentTimeMillis(), name));
            persistMatters();
            renderHome();
            added[0]++;
            addedCount.setVisibility(View.VISIBLE);
            addedCount.setText("本次已添加 " + added[0] + " 个标签");
            addedFlow.setVisibility(View.VISIBLE);
            TextView chip = choiceButton(name, false, 12);
            chip.setClickable(false);
            addedFlow.addView(chip);
            input.setText("");
            focusInput(input);
        };
        add.setOnClickListener(v -> addMatter.run());
        actions.addView(add);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addMatter.run();
                return true;
            }
            return false;
        });
        focusInput(input);
    }

    private void openMatterEditor(String id) {
        Models.Matter target = null;
        for (Models.Matter m : customMatters) {
            if (m.id.equals(id)) {
                target = m;
                break;
            }
        }
        if (target == null) return;
        Models.Matter matter = target;
        LinearLayout content = beginModal();
        addDialogTitle(content, "修改标签");
        addDialogCopy(content, "只修改首页显示的名称，历史记录中的旧名称保持不变。");
        EditText input = dialogInput(InputType.TYPE_CLASS_TEXT, "输入标签名称", 12, false);
        input.setText(matter.name);
        input.setSelection(input.getText().length());
        content.addView(input);
        LinearLayout actions = addDialogActions(content);
        TextView cancel = dialogButton("取消", 0);
        cancel.setOnClickListener(v -> closeModal());
        actions.addView(cancel);
        TextView save = dialogButton("保存", 1);
        save.setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                toast("请输入标签名称");
                return;
            }
            if (!name.equals(matter.name) && isMatterNameTaken(name, matter.id)) {
                toast("标签已存在");
                return;
            }
            if (matter.name.equals(selectedMatter)) selectedMatter = name;
            matter.name = name;
            persistMatters();
            closeModal();
            renderHome();
            toast("标签已更新");
        });
        actions.addView(save);
        focusInput(input);
    }

    private boolean isMatterNameTaken(String name, String exceptId) {
        for (String existing : homeMatters) {
            if (existing.equals(name)) return true;
        }
        for (Models.Matter m : customMatters) {
            if (!m.id.equals(exceptId) && m.name.equals(name)) return true;
        }
        return false;
    }

    private void openTimeManager() {
        LinearLayout content = beginModal();
        addDialogTitle(content, "自定义时长");
        addDialogCopy(content, "添加后会显示在首页。长按首页的时长按钮可拖动排序或删除。");
        EditText input = dialogInput(InputType.TYPE_CLASS_NUMBER, "输入分钟数", 4, false);
        content.addView(input);
        LinearLayout actions = addDialogActions(content);
        TextView cancel = dialogButton("取消", 0);
        cancel.setOnClickListener(v -> closeModal());
        actions.addView(cancel);
        TextView add = dialogButton("添加", 1);
        Runnable addTime = () -> {
            String raw = input.getText().toString().trim();
            int minutes = Math.round(parseIntSafe(raw, 0));
            if (minutes < 1 || minutes > 999) {
                toast("请输入 1–999 分钟");
                return;
            }
            if (homeTimes.contains(minutes)) {
                toast("这个时长已存在");
                return;
            }
            for (Models.TimeChip t : customTimes) {
                if (t.minutes == minutes) {
                    toast("这个时长已存在");
                    return;
                }
            }
            customTimes.add(new Models.TimeChip("time-" + System.currentTimeMillis(), minutes));
            persistTimes();
            closeModal();
            renderHome();
        };
        add.setOnClickListener(v -> addTime.run());
        actions.addView(add);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTime.run();
                return true;
            }
            return false;
        });
        focusInput(input);
    }

    private void requestClearAllData() {
        LinearLayout content = beginModal();
        addDialogTitle(content, "清除所有数据？");
        addDialogCopy(content, "记录、个性签名、自定义按钮和全部设置都会被删除，无法恢复。");
        LinearLayout actions = addDialogActions(content);
        TextView cancel = dialogButton("取消", 0);
        cancel.setOnClickListener(v -> closeModal());
        actions.addView(cancel);
        TextView confirm = dialogButton("全部清除", 2);
        confirm.setOnClickListener(v -> clearAllData());
        actions.addView(confirm);
    }

    private void clearAllData() {
        stopTicking();
        Reminder.cancelAll(this);
        active = null;
        releaseKeepAwake();
        records.clear();
        customMatters.clear();
        customTimes.clear();
        homeMatters = new ArrayList<>();
        Collections.addAll(homeMatters, Models.BASE_MATTERS);
        homeTimes = new ArrayList<>();
        for (int v : Models.BASE_TIMES) homeTimes.add(v);
        signature = "";
        settings = new Models.Settings();
        selectedMatter = null;
        selectedDate = localDate(System.currentTimeMillis());
        historyFilter = "all";
        persistRecords();
        persistMatters();
        persistTimes();
        persistHome();
        persistSignature();
        persistSettings();
        saveActive();
        countdownRoot.setVisibility(View.GONE);
        mainColumn.setVisibility(View.VISIBLE);
        resetPixelShift();
        showSystemBars();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        closeModal();
        renderHome();
        renderSettings();
        refreshBulkText();
        renderRecords();
        toast("所有数据已清除");
    }

    private void focusInput(EditText input) {
        input.post(() -> {
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void toast(String message) {
        if (toastView == null) return;
        if (toastRunnable != null) handler.removeCallbacks(toastRunnable);
        toastView.setText(message);
        toastView.setVisibility(View.VISIBLE);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) toastView.getLayoutParams();
        lp.bottomMargin = (countdownRoot.getVisibility() == View.VISIBLE ? dp(84) : dp(90)) + insetBottom;
        toastView.setLayoutParams(lp);
        toastRunnable = () -> toastView.setVisibility(View.GONE);
        handler.postDelayed(toastRunnable, 1800);
    }

    // ------------------------------------------------------------ time drag

    private static class TimeTag {
        final int minutes;
        final String id;
        final boolean add;

        TimeTag(int minutes, String id, boolean add) {
            this.minutes = minutes;
            this.id = id;
            this.add = add;
        }

        String key() {
            return id == null ? "b:" + minutes : id;
        }
    }

    private static class MatterTag {
        final String name;
        final String id;
        final boolean add;

        MatterTag(String name, String id, boolean add) {
            this.name = name;
            this.id = id;
            this.add = add;
        }

        String key() {
            return id == null ? "n:" + name : "id:" + id;
        }
    }

    private void installMatterGridTouch() {
        matterGrid.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    matterDownX = e.getRawX();
                    matterDownY = e.getRawY();
                    matterPressed = findMatterChild(matterDownX, matterDownY);
                    matterMoved = false;
                    matterDragging = false;
                    cancelMatterLongPress();
                    if (matterPressed != null) {
                        MatterTag tag = matterPressed.getTag() instanceof MatterTag
                                ? (MatterTag) matterPressed.getTag() : null;
                        if (tag != null && !tag.add) {
                            int[] loc = new int[2];
                            matterPressed.getLocationOnScreen(loc);
                            dragOffsetX = (int) (matterDownX - loc[0]);
                            dragOffsetY = (int) (matterDownY - loc[1]);
                            matterLongPress = () -> beginMatterDrag(matterPressed, matterDownX, matterDownY);
                            v.postDelayed(matterLongPress, 520);
                        }
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (matterDragging) {
                        moveMatterDrag(e.getRawX(), e.getRawY());
                        return true;
                    }
                    if (!matterMoved && Math.hypot(e.getRawX() - matterDownX, e.getRawY() - matterDownY) > dp(8)) {
                        matterMoved = true;
                        cancelMatterLongPress();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    cancelMatterLongPress();
                    if (matterDragging) {
                        finishMatterDrag(dragOverDelete);
                        matterMoved = true;
                    } else if (!matterMoved && matterPressed != null) {
                        MatterTag tag = matterPressed.getTag() instanceof MatterTag
                                ? (MatterTag) matterPressed.getTag() : null;
                        if (tag != null) handleMatterTap(tag);
                    }
                    resetMatterGesture();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    cancelMatterLongPress();
                    if (matterDragging) finishMatterDrag(false);
                    resetMatterGesture();
                    return true;
                default:
                    return true;
            }
        });
    }

    private View findMatterChild(float rawX, float rawY) {
        int[] loc = new int[2];
        for (int i = 0; i < matterGrid.getChildCount(); i++) {
            View child = matterGrid.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            child.getLocationOnScreen(loc);
            if (rawX >= loc[0] && rawX <= loc[0] + child.getWidth()
                    && rawY >= loc[1] && rawY <= loc[1] + child.getHeight()) return child;
        }
        return null;
    }

    private void cancelMatterLongPress() {
        if (matterLongPress != null) {
            matterGrid.removeCallbacks(matterLongPress);
            matterLongPress = null;
        }
    }

    private void handleMatterTap(MatterTag tag) {
        if (tag.add) {
            hapticTap();
            openMatterManager();
            return;
        }
        if (tag.id != null && tag.name.equals(selectedMatter)) {
            hapticTap();
            openMatterEditor(tag.id);
            return;
        }
        selectedMatter = tag.name;
        hapticTap();
        renderHome();
    }

    private void beginMatterDrag(View chip, float rawX, float rawY) {
        MatterTag tag = chip.getTag() instanceof MatterTag ? (MatterTag) chip.getTag() : null;
        if (tag == null || tag.add || matterDragging) return;
        matterDragging = true;
        matterDragKey = tag.key();
        matterDragLastTarget = null;
        chip.setAlpha(0.25f);
        matterPressed = chip;
        vibrate(14);
        if (homeScroll != null) homeScroll.requestDisallowInterceptTouchEvent(true);

        TextView ghost = new TextView(this);
        ghost.setText(tag.name);
        ghost.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        ghost.setTextColor(0xFFEEEEEE);
        ghost.setGravity(Gravity.CENTER);
        ghost.setBackground(Ui.box(this, 0xFF111111, 0xFF999999, 1, 4));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(chip.getWidth(), chip.getHeight());
        lp.gravity = Gravity.TOP | Gravity.START;
        root.addView(ghost, lp);
        int[] rl = new int[2];
        root.getLocationOnScreen(rl);
        ghost.setX(rawX - dragOffsetX - rl[0]);
        ghost.setY(rawY - dragOffsetY - rl[1]);
        dragGhost = ghost;
        dragOverDelete = false;
        deleteTarget.setVisibility(View.VISIBLE);
        deleteTarget.setBackground(dashedTargetBg(false));
        deleteTarget.setTextColor(COLOR_DIM);
    }

    private void moveMatterDrag(float rawX, float rawY) {
        if (dragGhost == null) return;
        int[] rl = new int[2];
        root.getLocationOnScreen(rl);
        dragGhost.setX(rawX - dragOffsetX - rl[0]);
        dragGhost.setY(rawY - dragOffsetY - rl[1]);

        int[] tl = new int[2];
        deleteTarget.getLocationOnScreen(tl);
        boolean over = rawX >= tl[0] && rawX <= tl[0] + deleteTarget.getWidth()
                && rawY >= tl[1] && rawY <= tl[1] + deleteTarget.getHeight();
        if (over != dragOverDelete) {
            dragOverDelete = over;
            deleteTarget.setBackground(dashedTargetBg(over));
            deleteTarget.setTextColor(over ? 0xFF050505 : COLOR_DIM);
        }
        if (over) return;

        View hit = findMatterChild(rawX, rawY);
        if (hit != null && hit.getTag() instanceof MatterTag) {
            MatterTag t = (MatterTag) hit.getTag();
            if (!t.add && !t.key().equals(matterDragKey)) {
                reorderMatterChip(matterDragKey, t.key());
            }
        }
    }

    private List<String> matterOrderKeys() {
        List<String> keys = new ArrayList<>();
        for (String name : homeMatters) keys.add("n:" + name);
        for (Models.Matter m : customMatters) keys.add("id:" + m.id);
        return keys;
    }

    private void reorderMatterChip(String key, String targetKey) {
        if (key == null || targetKey == null || key.equals(targetKey)) return;
        if (targetKey.equals(matterDragLastTarget)) return;
        List<String> keys = matterOrderKeys();
        int from = keys.indexOf(key);
        int targetBefore = keys.indexOf(targetKey);
        if (from < 0 || targetBefore < 0) return;
        keys.remove(from);
        int to = keys.indexOf(targetKey);
        if (from < targetBefore) to += 1;
        keys.add(Math.min(to, keys.size()), key);

        List<Models.Matter> oldCustom = new ArrayList<>(customMatters);
        List<String> newHome = new ArrayList<>();
        List<Models.Matter> newCustom = new ArrayList<>();
        for (String k : keys) {
            if (k.startsWith("n:")) {
                newHome.add(k.substring(2));
            } else {
                String id = k.substring(3);
                for (Models.Matter m : oldCustom) {
                    if (m.id.equals(id)) {
                        newCustom.add(m);
                        break;
                    }
                }
            }
        }
        homeMatters = newHome;
        customMatters.clear();
        customMatters.addAll(newCustom);
        matterDragLastTarget = targetKey;
        persistHome();
        persistMatters();
        renderHome();
    }

    private void finishMatterDrag(boolean remove) {
        if (dragGhost != null) root.removeView(dragGhost);
        dragGhost = null;
        if (matterPressed != null) matterPressed.setAlpha(1f);
        deleteTarget.setVisibility(View.GONE);
        String key = matterDragKey;
        if (remove && key != null) {
            String removedName = null;
            if (key.startsWith("n:")) {
                removedName = key.substring(2);
                homeMatters.remove(removedName);
            } else {
                String id = key.substring(3);
                for (int i = 0; i < customMatters.size(); i++) {
                    if (customMatters.get(i).id.equals(id)) {
                        removedName = customMatters.remove(i).name;
                        break;
                    }
                }
            }
            if (removedName != null && removedName.equals(selectedMatter)) {
                boolean still = homeMatters.contains(removedName);
                if (!still) {
                    for (Models.Matter m : customMatters) {
                        if (m.name.equals(removedName)) {
                            still = true;
                            break;
                        }
                    }
                }
                if (!still) selectedMatter = null;
            }
            persistHome();
            persistMatters();
            renderHome();
            toast("已删除");
        } else {
            persistHome();
            persistMatters();
        }
        if (homeScroll != null) homeScroll.requestDisallowInterceptTouchEvent(false);
        matterDragging = false;
        matterDragKey = null;
        matterDragLastTarget = null;
        dragOverDelete = false;
        matterMoved = true;
    }

    private void resetMatterGesture() {
        matterPressed = null;
        matterMoved = false;
        matterDragging = false;
        matterDragKey = null;
        matterDragLastTarget = null;
        if (homeScroll != null) homeScroll.requestDisallowInterceptTouchEvent(false);
    }

    private void installTimeGridTouch() {
        timeGrid.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    timeDownX = e.getRawX();
                    timeDownY = e.getRawY();
                    timePressed = findTimeChild(e.getRawX(), e.getRawY());
                    timeMoved = false;
                    timeDragging = false;
                    cancelTimeLongPress();
                    if (timePressed != null) {
                        TimeTag tag = timePressed.getTag() instanceof TimeTag ? (TimeTag) timePressed.getTag() : null;
                        if (tag != null && !tag.add) {
                            int[] loc = new int[2];
                            timePressed.getLocationOnScreen(loc);
                            dragOffsetX = (int) (e.getRawX() - loc[0]);
                            dragOffsetY = (int) (e.getRawY() - loc[1]);
                            timeLongPress = () -> beginChipDrag(timePressed, e.getRawX(), e.getRawY());
                            v.postDelayed(timeLongPress, 520);
                        }
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (timeDragging) {
                        moveChipDrag(e.getRawX(), e.getRawY());
                        return true;
                    }
                    if (!timeMoved && Math.hypot(e.getRawX() - timeDownX, e.getRawY() - timeDownY) > dp(8)) {
                        timeMoved = true;
                        cancelTimeLongPress();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    cancelTimeLongPress();
                    if (timeDragging) {
                        finishChipDrag(dragOverDelete);
                        timeMoved = true;
                    } else if (!timeMoved && timePressed != null) {
                        TimeTag tag = timePressed.getTag() instanceof TimeTag ? (TimeTag) timePressed.getTag() : null;
                        if (tag != null) handleTimeTap(tag);
                    }
                    resetTimeGesture();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    cancelTimeLongPress();
                    if (timeDragging) finishChipDrag(false);
                    resetTimeGesture();
                    return true;
                default:
                    return true;
            }
        });
    }

    private void resetTimeGesture() {
        timePressed = null;
        timeMoved = false;
        timeDragging = false;
        dragGhost = null;
        dragId = null;
        dragLastTarget = null;
        dragOverDelete = false;
    }

    private void cancelTimeLongPress() {
        if (timeLongPress != null) {
            timeGrid.removeCallbacks(timeLongPress);
            timeLongPress = null;
        }
    }

    private void handleTimeTap(TimeTag tag) {
        if (tag.add) {
            vibrate(12);
            openTimeManager();
            return;
        }
        if (selectedMatter == null) {
            toast("请先选择事项");
            return;
        }
        vibrate(12);
        startTimer(tag.minutes);
    }

    private View findTimeChild(float rawX, float rawY) {
        int[] loc = new int[2];
        for (int i = 0; i < timeGrid.getChildCount(); i++) {
            View child = timeGrid.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            child.getLocationOnScreen(loc);
            if (rawX >= loc[0] && rawX <= loc[0] + child.getWidth()
                    && rawY >= loc[1] && rawY <= loc[1] + child.getHeight()) return child;
        }
        return null;
    }

    private void beginChipDrag(View chip, float rawX, float rawY) {
        TimeTag tag = chip.getTag() instanceof TimeTag ? (TimeTag) chip.getTag() : null;
        if (tag == null || tag.add || timeDragging) return;
        timeDragging = true;
        dragId = tag.key();
        dragLastTarget = null;
        chip.setAlpha(0.25f);
        timePressed = chip;
        vibrate(14);
        if (homeScroll != null) homeScroll.requestDisallowInterceptTouchEvent(true);

        TextView ghost = new TextView(this);
        ghost.setText(tag.minutes + "分钟");
        ghost.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        ghost.setTextColor(0xFFEEEEEE);
        ghost.setGravity(Gravity.CENTER);
        ghost.setBackground(Ui.box(this, 0xFF111111, 0xFF999999, 1, 4));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(chip.getWidth(), chip.getHeight());
        lp.gravity = Gravity.TOP | Gravity.START;
        root.addView(ghost, lp);
        int[] rl = new int[2];
        root.getLocationOnScreen(rl);
        ghost.setX(rawX - dragOffsetX - rl[0]);
        ghost.setY(rawY - dragOffsetY - rl[1]);
        dragGhost = ghost;
        dragOverDelete = false;
        deleteTarget.setVisibility(View.VISIBLE);
        deleteTarget.setBackground(dashedTargetBg(false));
        deleteTarget.setTextColor(COLOR_DIM);
    }

    private void moveChipDrag(float rawX, float rawY) {
        if (dragGhost == null) return;
        int[] rl = new int[2];
        root.getLocationOnScreen(rl);
        dragGhost.setX(rawX - dragOffsetX - rl[0]);
        dragGhost.setY(rawY - dragOffsetY - rl[1]);

        int[] tl = new int[2];
        deleteTarget.getLocationOnScreen(tl);
        boolean over = rawX >= tl[0] && rawX <= tl[0] + deleteTarget.getWidth()
                && rawY >= tl[1] && rawY <= tl[1] + deleteTarget.getHeight();
        if (over != dragOverDelete) {
            dragOverDelete = over;
            deleteTarget.setBackground(dashedTargetBg(over));
            deleteTarget.setTextColor(over ? 0xFF050505 : COLOR_DIM);
        }
        if (over) return;

        View hit = findTimeChild(rawX, rawY);
        if (hit != null && hit.getTag() instanceof TimeTag) {
            TimeTag t = (TimeTag) hit.getTag();
            if (!t.add && !t.key().equals(dragId)) reorderTimeChip(dragId, t.key());
        }
    }

    private List<String> timeOrderKeys() {
        List<String> keys = new ArrayList<>();
        for (int m : homeTimes) keys.add("b:" + m);
        for (Models.TimeChip t : customTimes) keys.add(t.id);
        return keys;
    }

    private void reorderTimeChip(String key, String targetKey) {
        if (key == null || targetKey == null || key.equals(targetKey)) return;
        if (targetKey.equals(dragLastTarget)) return;
        List<String> keys = timeOrderKeys();
        int from = keys.indexOf(key);
        int targetBefore = keys.indexOf(targetKey);
        if (from < 0 || targetBefore < 0) return;
        keys.remove(from);
        int to = keys.indexOf(targetKey);
        if (from < targetBefore) to += 1;
        keys.add(Math.min(to, keys.size()), key);

        List<Models.TimeChip> oldCustom = new ArrayList<>(customTimes);
        List<Integer> newHome = new ArrayList<>();
        List<Models.TimeChip> newCustom = new ArrayList<>();
        for (String k : keys) {
            if (k.startsWith("b:")) {
                try {
                    newHome.add(Integer.parseInt(k.substring(2)));
                } catch (Exception ignored) {}
            } else {
                for (Models.TimeChip t : oldCustom) {
                    if (t.id.equals(k)) {
                        newCustom.add(t);
                        break;
                    }
                }
            }
        }
        homeTimes = newHome;
        customTimes.clear();
        customTimes.addAll(newCustom);
        dragLastTarget = targetKey;
        persistHome();
        persistTimes();
        renderTimeGrid();
    }

    private void finishChipDrag(boolean remove) {
        if (dragGhost != null) root.removeView(dragGhost);
        dragGhost = null;
        if (timePressed != null) timePressed.setAlpha(1f);
        deleteTarget.setVisibility(View.GONE);
        String id = dragId;
        if (remove && id != null) {
            if (id.startsWith("b:")) {
                try {
                    homeTimes.remove(Integer.valueOf(Integer.parseInt(id.substring(2))));
                } catch (Exception ignored) {}
            } else {
                for (int i = 0; i < customTimes.size(); i++) {
                    if (customTimes.get(i).id.equals(id)) {
                        customTimes.remove(i);
                        break;
                    }
                }
            }
            persistHome();
            persistTimes();
            renderTimeGrid();
            toast("已删除");
        } else {
            persistHome();
            persistTimes();
        }
        if (homeScroll != null) homeScroll.requestDisallowInterceptTouchEvent(false);
        timeDragging = false;
        dragId = null;
        dragLastTarget = null;
        dragOverDelete = false;
        timeMoved = true;
    }

    // ----------------------------------------------------------------- timer

    private void startTimer(int minutes) {
        if (selectedMatter == null) {
            toast("请先选择事项");
            return;
        }
        long totalMs = minutes * 60000L;
        if (totalMs <= 0) {
            toast("请输入有效时间");
            return;
        }
        long now = System.currentTimeMillis();
        Models.Active a = new Models.Active();
        a.matter = selectedMatter;
        a.totalMs = totalMs;
        a.remainingMs = totalMs;
        a.elapsedMs = 0;
        a.startedAt = now;
        a.lastStartAt = now;
        a.endAt = now + totalMs;
        a.running = true;
        a.finished = false;
        a.signature = signature;
        a.nextReminderAt = 0;
        a.segments.add(new Models.Segment(now, -1));
        active = a;
        closeModal();
        enterCountdown();
        saveActive();
        Reminder.cancelAll(this);
        Reminder.scheduleFinish(this, a.endAt);
    }

    // ----------------------------------------------------------------- haptics

    private void hapticTap() {
        vibrate(16);
    }

    private void hapticTick() {
        long now = System.currentTimeMillis();
        if (now - lastHapticAt < 55) return;
        lastHapticAt = now;
        vibrate(9);
    }

    private void attachTapHaptic(View v) {
        v.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) hapticTap();
            return false;
        });
    }

    private void vibrate(int ms) {
        if (!settings.haptics) return;
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(ms);
            }
        } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------- utilities

    private static int parseIntSafe(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String pad(int value) {
        return String.format(Locale.US, "%02d", value);
    }

    private static String localDate(long timestamp) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(timestamp);
        return String.format(Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private static long[] dayBounds(String date) {
        String[] parts = date.split("-");
        Calendar c = Calendar.getInstance();
        try {
            c.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]), 0, 0, 0);
            c.set(Calendar.MILLISECOND, 0);
        } catch (Exception e) {
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
        }
        long start = c.getTimeInMillis();
        return new long[]{start, start + 86400000L};
    }

    private static String clockTime(long timestamp) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(timestamp);
        return pad(c.get(Calendar.HOUR_OF_DAY)) + ":" + pad(c.get(Calendar.MINUTE));
    }

    private static String clockTime(long timestamp, boolean seconds) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(timestamp);
        return pad(c.get(Calendar.HOUR_OF_DAY)) + ":" + pad(c.get(Calendar.MINUTE))
                + (seconds ? ":" + pad(c.get(Calendar.SECOND)) : "");
    }

    private static String formatDuration(long ms) {
        long totalSeconds = Math.max(0, Math.round(ms / 1000.0));
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) return minutes > 0 ? hours + " 小时 " + minutes + " 分钟" : hours + " 小时";
        if (minutes > 0) {
            return (seconds > 0 && totalSeconds < 600) ? minutes + " 分 " + seconds + " 秒" : minutes + " 分钟";
        }
        return seconds + " 秒";
    }

    private static String formatTimer(long ms) {
        long totalSeconds = Math.max(0, (ms + 999) / 1000);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return hours > 0 ? pad((int) hours) + ":" + pad((int) minutes) + ":" + pad((int) seconds)
                : pad((int) minutes) + ":" + pad((int) seconds);
    }

    // ------------------------------------------------------------- lifecycle

    @Override
    protected void onPause() {
        super.onPause();
        saveActive();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Reminder.resume(this);
        if (active != null) {
            updateKeepAwake();
            tick();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyDigitSettings();
        if (active != null) {
            tick();
        }
    }

    @Override
    public void onBackPressed() {
        if (modalBackdrop != null && modalBackdrop.getVisibility() == View.VISIBLE) {
            closeModal();
            return;
        }
        if (active != null && countdownRoot.getVisibility() == View.VISIBLE) {
            if (!active.finished) requestEarlyEnd();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        stopTicking();
        super.onDestroy();
    }
}
