package com.pape.punchring;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

/** Only the short transition uses an in-memory mesh; the open panel is live UI. */
final class GeniePanelLayout extends FrameLayout {
    private final GenieTransition transition = new GenieTransition();
    private Bitmap snapshot;
    private View panel;
    private ValueAnimator animator;
    private float progress;
    private float anchorX;
    private float anchorY;
    private float sourceRadius;
    private int bubbleColor;
    private float panelLeft;
    private float panelTop;
    private float panelWidth;
    private float panelHeight;
    private boolean closing;

    GeniePanelLayout(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
    }

    void bind(View view, float x, float y, float radius) {
        panel = view;
        anchorX = x;
        anchorY = y;
        sourceRadius = radius;
        boolean light = AppSettings.prefs(getContext()).getBoolean(AppSettings.AUTO_CONTRAST, true)
            && AppSettings.prefs(getContext()).getBoolean(AppSettings.BACKGROUND_IS_LIGHT, false);
        bubbleColor = light ? Color.BLACK : Color.WHITE;
    }

    void open() { animateTo(1f, null); }

    void close(Runnable completion) {
        if (closing) return;
        closing = true;
        animateTo(0f, completion);
    }

    private void animateTo(float target, Runnable completion) {
        if (animator != null) {
            animator.removeAllListeners();
            animator.cancel();
        }
        if (!ValueAnimator.areAnimatorsEnabled() || !capture()) {
            progress = target;
            releaseSnapshot();
            invalidate();
            if (completion != null) completion.run();
            return;
        }
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(Math.max(100L, Math.round((target == 1f ? StatusIconMotion.ENTER_MS : StatusIconMotion.EXIT_MS)
            * Math.abs(target - progress))));
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(value -> {
            progress = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                animator = null;
                releaseSnapshot();
                invalidate();
                if (completion != null) completion.run();
            }
        });
        animator.start();
    }

    private boolean capture() {
        if (snapshot != null) return true;
        if (panel == null || panel.getWidth() == 0 || panel.getHeight() == 0) return false;
        panelLeft = panel.getLeft();
        panelTop = panel.getTop();
        panelWidth = panel.getWidth();
        panelHeight = panel.getHeight();
        try {
            snapshot = Bitmap.createBitmap(panel.getWidth(), panel.getHeight(), Bitmap.Config.ARGB_8888);
            panel.draw(new Canvas(snapshot));
            return true;
        } catch (OutOfMemoryError error) {
            releaseSnapshot();
            return false;
        }
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        canvas.drawColor(Color.argb(Math.round(108f * RingStateMotion.ease(progress)), 0, 0, 0));
        if (progress >= 1f) {
            super.dispatchDraw(canvas);
        } else if (progress > 0f && snapshot != null) {
            transition.draw(canvas, snapshot, progress, anchorX, anchorY, sourceRadius,
                panelLeft, panelTop, panelWidth, panelHeight, bubbleColor);
        }
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (progress < 1f || closing) return true;
        return super.dispatchTouchEvent(event);
    }

    void dispose() {
        if (animator != null) {
            animator.removeAllListeners();
            animator.cancel();
            animator = null;
        }
        releaseSnapshot();
    }

    private void releaseSnapshot() {
        if (snapshot != null) {
            snapshot.recycle();
            snapshot = null;
        }
    }

    @Override protected void onDetachedFromWindow() {
        dispose();
        super.onDetachedFromWindow();
    }
}
