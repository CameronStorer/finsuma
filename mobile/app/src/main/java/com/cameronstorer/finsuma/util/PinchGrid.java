package com.cameronstorer.finsuma.util;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cameronstorer.finsuma.R;
import com.cameronstorer.finsuma.helper.recyclerview.GridItemDecoration;

import java.util.function.IntConsumer;

/**
 * Finsuma: pinch to resize a grid (pinch out = bigger tiles = fewer columns). The column count
 * is remembered for each section ("albums", "playlists", ...).
 */
public final class PinchGrid {
    private static final int MIN_SPAN = 1;
    private static final int MAX_SPAN = 8;

    private PinchGrid() {}

    public static int span(Context context, String section, int defaultSpan) {
        int saved = context.getSharedPreferences("finsuma_ui", Context.MODE_PRIVATE).getInt("span_" + section, 0);
        return saved == 0 ? defaultSpan : Math.max(MIN_SPAN, Math.min(MAX_SPAN, saved));
    }

    /** Tile width for [span] columns, the same rule TileSizeManager uses. */
    public static int tilePx(Context context, int span) {
        return Math.round(context.getResources().getDisplayMetrics().widthPixels / (float) span) - 10;
    }

    /** Grid whose tiles stretch to the column width (album, artist and genre catalogues). */
    public static void setupCatalogue(RecyclerView rv, String section, int defaultSpan, int spacing) {
        Context context = rv.getContext();
        int span = span(context, section, defaultSpan);
        rv.setLayoutManager(new GridLayoutManager(context, span));
        replaceDecoration(rv, span, spacing);
        attach(rv, section, span, newSpan -> {
            ((GridLayoutManager) rv.getLayoutManager()).setSpanCount(newSpan);
            replaceDecoration(rv, newSpan, spacing);
            rv.requestLayout();
        });
    }

    /** Grid of fixed-size cover cards; [onSize] gets the cover size in px (call before setAdapter). */
    public static void setupCards(RecyclerView rv, String section, int defaultSpan, IntConsumer onSize) {
        Context context = rv.getContext();
        int span = span(context, section, defaultSpan);
        rv.setLayoutManager(new GridLayoutManager(context, span));
        onSize.accept(tilePx(context, span));
        attach(rv, section, span, newSpan -> {
            ((GridLayoutManager) rv.getLayoutManager()).setSpanCount(newSpan);
            onSize.accept(tilePx(context, newSpan));
        });
    }

    private static void replaceDecoration(RecyclerView rv, int span, int spacing) {
        while (rv.getItemDecorationCount() > 0) rv.removeItemDecorationAt(0);
        rv.addItemDecoration(new GridItemDecoration(span, spacing, false));
    }

    private static void attach(RecyclerView rv, String section, int startSpan, IntConsumer onSpan) {
        Object old = rv.getTag(R.id.finsuma_pinch_listener);
        if (old instanceof RecyclerView.OnItemTouchListener) {
            rv.removeOnItemTouchListener((RecyclerView.OnItemTouchListener) old);
        }
        Context context = rv.getContext();
        int[] span = {startSpan};
        float[] scale = {1f};

        ScaleGestureDetector detector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(@NonNull ScaleGestureDetector d) {
                scale[0] = 1f;
                return true;
            }

            @Override
            public boolean onScale(@NonNull ScaleGestureDetector d) {
                scale[0] *= d.getScaleFactor();
                int next = span[0];
                if (scale[0] > 1.25f) next--;       // pinch out: bigger tiles
                else if (scale[0] < 0.8f) next++;   // pinch in: smaller tiles
                else return false;
                scale[0] = 1f;
                next = Math.max(MIN_SPAN, Math.min(MAX_SPAN, next));
                if (next != span[0]) {
                    span[0] = next;
                    context.getSharedPreferences("finsuma_ui", Context.MODE_PRIVATE).edit().putInt("span_" + section, next).apply();
                    onSpan.accept(next);
                }
                return true;
            }
        });

        RecyclerView.OnItemTouchListener listener = new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView view, @NonNull MotionEvent e) {
                detector.onTouchEvent(e);
                return detector.isInProgress(); // stop scrolling while pinching
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView view, @NonNull MotionEvent e) {
                detector.onTouchEvent(e);
            }
        };
        rv.addOnItemTouchListener(listener);
        rv.setTag(R.id.finsuma_pinch_listener, listener);
    }
}
