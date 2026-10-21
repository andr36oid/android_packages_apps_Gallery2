// SPDX-License-Identifier: Apache-2.0

package com.android.gallery3d.ui;

import android.graphics.Rect;
import android.view.KeyEvent;

import com.android.gallery3d.glrenderer.GLCanvas;

/**
 * andr36oid: a focus that the D-pad moves through a grid of slots (albums, photos), for
 * consoles without a touch screen. The grids are drawn with GL, so there are no Android
 * views to focus: the grid view keeps the focused index and draws the frame itself.
 */
public final class PadFocus {

    /** The slots of a grid; titles (the timeline's dates) can't take the focus. */
    public interface Grid {
        int slotCount();

        boolean isTitle(int index);

        /** Where the slot is in the content, before scrolling; null if unknown. */
        Rect slotRect(int index);
    }

    private static final int FRAME_COLOR = 0xffff8a00;
    private static final int FILL_COLOR = 0x33ffffff;
    private static final int FRAME_WIDTH = 4;

    private PadFocus() {
    }

    /** True for the keys that move the focus. */
    public static boolean isMove(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    /** The first slot that can take the focus at or after start, or -1. */
    public static int first(Grid grid, int start) {
        final int n = grid.slotCount();
        for (int i = Math.max(0, start); i < n; i++) {
            if (!grid.isTitle(i)) return i;
        }
        for (int i = Math.min(start, n) - 1; i >= 0; i--) {
            if (!grid.isTitle(i)) return i;
        }
        return -1;
    }

    /**
     * Where the focus goes from slot from with a D-pad key: left and right go to the slot
     * before or after it, up and down to the nearest slot in the row above or below.
     * Returns from if there is nowhere to go.
     */
    public static int move(Grid grid, int from, int keyCode) {
        final int n = grid.slotCount();
        if (from < 0 || from >= n) return first(grid, 0);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                for (int i = from - 1; i >= 0; i--) {
                    if (!grid.isTitle(i)) return i;
                }
                return from;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                for (int i = from + 1; i < n; i++) {
                    if (!grid.isTitle(i)) return i;
                }
                return from;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return nextRow(grid, from, 1);
            case KeyEvent.KEYCODE_DPAD_UP:
                return nextRow(grid, from, -1);
            default:
                return from;
        }
    }

    private static int nextRow(Grid grid, int from, int step) {
        final Rect cur = grid.slotRect(from);
        if (cur == null) return from;
        final int n = grid.slotCount();
        final int x = cur.centerX();
        int best = from;
        int bestDistance = Integer.MAX_VALUE;
        int rowTop = Integer.MIN_VALUE;
        for (int i = from + step; i >= 0 && i < n; i += step) {
            if (grid.isTitle(i)) {
                if (rowTop != Integer.MIN_VALUE) break;
                continue;
            }
            final Rect r = grid.slotRect(i);
            if (r == null) break;
            if (rowTop == Integer.MIN_VALUE) {
                if (r.top == cur.top) continue;
                rowTop = r.top;
            } else if (r.top != rowTop) {
                break;
            }
            final int distance = Math.abs(r.centerX() - x);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    /** Draws the focus frame around r, in the canvas's current (scrolled) coordinates. */
    public static void draw(GLCanvas canvas, Rect r) {
        final int w = r.width();
        final int h = r.height();
        final int t = FRAME_WIDTH;
        canvas.fillRect(r.left, r.top, w, h, FILL_COLOR);
        canvas.fillRect(r.left, r.top, w, t, FRAME_COLOR);
        canvas.fillRect(r.left, r.bottom - t, w, t, FRAME_COLOR);
        canvas.fillRect(r.left, r.top + t, t, h - 2 * t, FRAME_COLOR);
        canvas.fillRect(r.right - t, r.top + t, t, h - 2 * t, FRAME_COLOR);
    }
}
