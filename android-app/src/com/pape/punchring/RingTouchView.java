package com.pape.punchring;

import android.content.Context;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/** A compact transparent touch target confined to the punch-ring window. */
final class RingTouchView extends View {
    interface Listener {
        void onRingTap();
        void onRingLongPress();
        void onRingPressed(boolean pressed);
    }

    private final int touchSlop;
    private final Listener listener;
    private float downX;
    private float downY;
    private long downTime;
    private long lastTapTime;
    private boolean cancelled;
    private boolean longPressed;
    private final Runnable longPressAction;

    RingTouchView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        longPressAction = () -> {
            if (cancelled || !isPressed()) return;
            longPressed = true;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            this.listener.onRingLongPress();
        };
        setContentDescription(UiText.get(context, "셀피 카메라 열기", "Open selfie camera"));
        setClickable(true);
        setFocusable(false);
        setBackgroundColor(android.graphics.Color.TRANSPARENT);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                downTime = SystemClock.elapsedRealtime();
                cancelled = false;
                longPressed = false;
                setPressed(true);
                listener.onRingPressed(true);
                postDelayed(longPressAction, ViewConfiguration.getLongPressTimeout());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.hypot(event.getX() - downX, event.getY() - downY) > touchSlop * 2f) {
                    cancelled = true;
                    setPressed(false);
                    listener.onRingPressed(false);
                    removeCallbacks(longPressAction);
                }
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPressAction);
                setPressed(false);
                listener.onRingPressed(false);
                long now = SystemClock.elapsedRealtime();
                if (!cancelled && !longPressed && now - downTime <= 700L
                        && now - lastTapTime >= 800L) {
                    lastTapTime = now;
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPressAction);
                cancelled = true;
                setPressed(false);
                listener.onRingPressed(false);
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        listener.onRingTap();
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(longPressAction);
        super.onDetachedFromWindow();
    }
}
