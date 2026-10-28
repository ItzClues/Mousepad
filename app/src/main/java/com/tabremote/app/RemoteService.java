package com.tabremote.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class RemoteService extends AccessibilityService {
    public static final int PORT = 8080;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private WebServer server;
    private WindowManager wm;
    private View cursor;
    private WindowManager.LayoutParams lp;
    private float x, y;
    private int sw, sh;
    private boolean scrolling;
    private float pendingScroll;

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() {}

    @Override protected void onServiceConnected() {
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        sw = dm.widthPixels; sh = dm.heightPixels;
        x = sw / 2f; y = sh / 2f;
        addCursor();
        try {
            InputStream in = getAssets().open("controller.html");
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[4096]; int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            server = new WebServer(PORT, bo.toByteArray(), msg -> ui.post(() -> handle(msg)));
            server.start();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override public boolean onUnbind(android.content.Intent i) {
        if (server != null) server.stop();
        if (cursor != null) try { wm.removeView(cursor); } catch (Exception ignored) {}
        return super.onUnbind(i);
    }

    private void addCursor() {
        cursor = new View(this) {
            final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
            { fill.setColor(Color.WHITE); line.setColor(Color.BLACK); line.setStyle(Paint.Style.STROKE); line.setStrokeWidth(3); }
            @Override protected void onDraw(Canvas c) {
                Path p = new Path(); p.moveTo(2, 2); p.lineTo(2, 44); p.lineTo(14, 33); p.lineTo(26, 52); p.lineTo(34, 47); p.lineTo(22, 29); p.lineTo(38, 29); p.close();
                c.drawPath(p, fill); c.drawPath(p, line);
            }
        };
        lp = new WindowManager.LayoutParams(60, 60, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = (int) x; lp.y = (int) y;
        wm.addView(cursor, lp);
    }

    private void moveCursor(float dx, float dy) {
        x = Math.max(0, Math.min(sw - 1, x + dx));
        y = Math.max(0, Math.min(sh - 1, y + dy));
        lp.x = (int) x; lp.y = (int) y;
        try { wm.updateViewLayout(cursor, lp); } catch (Exception ignored) {}
    }

    private void handle(String m) {
        try {
            String[] p = m.split(" ", 2);
            String arg = p.length > 1 ? p[1] : "";
            switch (p[0]) {
                case "m": { String[] a = arg.split(" "); moveCursor(Float.parseFloat(a[0]), Float.parseFloat(a[1])); break; }
                case "c": tap(50); break;
                case "l": tap(800); break;
                case "s": pendingScroll += Float.parseFloat(arg); doScroll(); break;
                case "b": performGlobalAction(GLOBAL_ACTION_BACK); break;
                case "h": performGlobalAction(GLOBAL_ACTION_HOME); break;
                case "r": performGlobalAction(GLOBAL_ACTION_RECENTS); break;
                case "n": performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS); break;
                case "t": typeText(arg); break;
                case "k": if (arg.equals("bs")) backspace(); else if (arg.equals("enter")) enter(); break;
            }
        } catch (Exception ignored) {}
    }

    private void tap(long ms) {
        Path path = new Path(); path.moveTo(x, y);
        dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, 0, ms)).build(), null, null);
    }

    private void doScroll() {
        if (scrolling || Math.abs(pendingScroll) < 8) return;
        float d = Math.max(-sh * 0.4f, Math.min(sh * 0.4f, pendingScroll));
        pendingScroll = 0;
        Path path = new Path(); path.moveTo(x, y); path.lineTo(x, Math.max(1, Math.min(sh - 1, y + d)));
        scrolling = true;
        boolean ok = dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, 0, 150)).build(),
                new GestureResultCallback() {
                    @Override public void onCompleted(GestureDescription g) { scrolling = false; doScroll(); }
                    @Override public void onCancelled(GestureDescription g) { scrolling = false; }
                }, null);
        if (!ok) scrolling = false;
    }

    private AccessibilityNodeInfo focused() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        AccessibilityNodeInfo f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        return f;
    }

    private String currentText(AccessibilityNodeInfo n) {
        CharSequence t = n.getText();
        if (t == null) return "";
        if (Build.VERSION.SDK_INT >= 26 && n.isShowingHintText()) return "";
        return t.toString();
    }

    private void setText(AccessibilityNodeInfo n, String s) {
        Bundle b = new Bundle();
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, s);
        n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
    }

    private void typeText(String s) {
        AccessibilityNodeInfo n = focused();
        if (n != null) setText(n, currentText(n) + s);
    }

    private void backspace() {
        AccessibilityNodeInfo n = focused();
        if (n == null) return;
        String t = currentText(n);
        if (!t.isEmpty()) setText(n, t.substring(0, t.length() - 1));
    }

    private void enter() {
        AccessibilityNodeInfo n = focused();
        if (n == null) return;
        if (Build.VERSION.SDK_INT >= 30) { n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId()); return; }
        if (n.isMultiLine()) setText(n, currentText(n) + "\n");
    }
}
