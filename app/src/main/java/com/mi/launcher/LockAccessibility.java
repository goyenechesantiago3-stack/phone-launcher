package com.mi.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

public class LockAccessibility extends AccessibilityService {
    static LockAccessibility inst;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        inst = this;
    }

    @Override
    public boolean onUnbind(Intent i) {
        inst = null;
        return super.onUnbind(i);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) { }

    @Override
    public void onInterrupt() { }

    static boolean lock() {
        if (inst == null) return false;
        return inst.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
    }
}
