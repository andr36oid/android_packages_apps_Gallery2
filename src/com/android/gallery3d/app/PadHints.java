// SPDX-License-Identifier: Apache-2.0

package com.android.gallery3d.app;

import android.content.Context;
import android.view.KeyEvent;

import org.codeaurora.gallery.R;

import java.util.ArrayList;

/** andr36oid: the button hints of the gallery's pages, and the keys they share. */
final class PadHints {

    private PadHints() {
    }

    /** A, or the keys it sends: press the selected thing. */
    static boolean isA(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_BUTTON_A || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER;
    }

    /** The hints of a grid of albums or photos. */
    static CharSequence[] grid(AbstractGalleryActivity activity, boolean selecting) {
        final Context c = activity;
        final ArrayList<CharSequence> h = new ArrayList<>();
        if (selecting) {
            add(h, "A", c.getString(R.string.pad_hint_mark));
            add(h, "B", c.getString(R.string.pad_hint_done));
            add(h, "Y", c.getString(R.string.pad_hint_actions));
        } else {
            final boolean tabs = activity.hasPadTabs();
            add(h, "A", c.getString(R.string.pad_hint_open));
            add(h, "B", c.getString(tabs ? R.string.pad_hint_close : R.string.pad_hint_back));
            add(h, "X", c.getString(R.string.pad_hint_select));
            add(h, "Y", c.getString(R.string.pad_hint_menu));
            if (tabs) add(h, "L1 R1", c.getString(R.string.pad_hint_tabs));
        }
        return h.toArray(new CharSequence[0]);
    }

    static void add(ArrayList<CharSequence> hints, CharSequence key, CharSequence label) {
        hints.add(key);
        hints.add(label);
    }
}
