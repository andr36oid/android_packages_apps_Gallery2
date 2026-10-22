/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.app;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import androidx.print.PrintHelper;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toolbar;

import org.codeaurora.gallery.R;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.ui.GLRootView;
import com.android.gallery3d.util.PanoramaViewHelper;
import com.android.gallery3d.util.ThreadPool;
import com.android.photos.data.GalleryBitmapPool;

import org.andr36oid.hints.HintBar;

import java.io.FileNotFoundException;
import java.util.ArrayList;

public abstract class AbstractGalleryActivity extends AbstractPermissionActivity
        implements GalleryContext {
    private static final String TAG = "AbstractGalleryActivity";
    private GLRootView mGLRootView;
    private StateManager mStateManager;
    private GalleryActionBar mActionBar;
    private OrientationManager mOrientationManager;
    private TransitionStore mTransitionStore = new TransitionStore();
    private PanoramaViewHelper mPanoramaViewHelper;
    private Toolbar mToolbar;
    public boolean isTopMenuShow = false;

    private AlertDialog mAlertDialog = null;
    private BroadcastReceiver mMountReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getCacheDir() != null) onStorageReady();
        }
    };
    private IntentFilter mMountFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);

    private static final String DEFAULT_PRINT_JOB_NAME = "print job";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mOrientationManager = new OrientationManager(this);
        getWindow().setBackgroundDrawable(null);
        mPanoramaViewHelper = new PanoramaViewHelper(this);
        mPanoramaViewHelper.onCreate();
        doBindBatchService();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        mGLRootView.lockRenderThread();
        try {
            super.onSaveInstanceState(outState);
            getStateManager().saveState(outState);
        } finally {
            mGLRootView.unlockRenderThread();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        mStateManager.onConfigurationChange(config);
        getGalleryActionBar().onConfigurationChanged();
        invalidateOptionsMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        return getStateManager().createOptionsMenu(menu);
    }

    @Override
    public Context getAndroidContext() {
        return this;
    }

    @Override
    public DataManager getDataManager() {
        return ((GalleryApp) getApplication()).getDataManager();
    }

    @Override
    public ThreadPool getThreadPool() {
        return ((GalleryApp) getApplication()).getThreadPool();
    }

    public synchronized StateManager getStateManager() {
        if (mStateManager == null) {
            mStateManager = new StateManager(this);
        }
        return mStateManager;
    }

    public GLRoot getGLRoot() {
        return mGLRootView;
    }

    public OrientationManager getOrientationManager() {
        return mOrientationManager;
    }

    @Override
    public void setContentView(int resId) {
        super.setContentView(resId);
        mGLRootView = (GLRootView) findViewById(R.id.gl_root_view);
        // andr36oid: the button hints along the bottom, see onPadKey
        mHintBar = HintBar.attach(this);
        updatePadHints();
    }

    protected void onStorageReady() {
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
            mAlertDialog = null;
            unregisterReceiver(mMountReceiver);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (getCacheDir() == null) {
            OnCancelListener onCancel = new OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    finish();
                }
            };
            OnClickListener onClick = new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            };
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(R.string.no_external_storage_title)
                    .setMessage(R.string.no_external_storage)
                    .setNegativeButton(android.R.string.cancel, onClick)
                    .setOnCancelListener(onCancel);
            if (ApiHelper.HAS_SET_ICON_ATTRIBUTE) {
                setAlertDialogIconAttribute(builder);
            } else {
                builder.setIcon(android.R.drawable.ic_dialog_alert);
            }
            mAlertDialog = builder.show();
            registerReceiver(mMountReceiver, mMountFilter);
        }
        mPanoramaViewHelper.onStart();
    }

    @TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
    private static void setAlertDialogIconAttribute(
            AlertDialog.Builder builder) {
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mAlertDialog != null) {
            unregisterReceiver(mMountReceiver);
            mAlertDialog.dismiss();
            mAlertDialog = null;
        }
        mPanoramaViewHelper.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If top menu shows, GLView is active,
        // so don't need to resume it.
        if (isTopMenuShow) {
            isTopMenuShow = false;
            return;
        }
        mGLRootView.lockRenderThread();
        try {
            getStateManager().resume();
            getDataManager().resume();
        } finally {
            mGLRootView.unlockRenderThread();
        }
        mGLRootView.onResume();
        mOrientationManager.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // If top menu shows, don't pause GLView,
        // so it can redraw when rotating.
        if (isTopMenuShow)
            return;
        mOrientationManager.pause();
        mGLRootView.onPause();
        mGLRootView.lockRenderThread();
        try {
            getStateManager().pause();
            getDataManager().pause();
        } finally {
            mGLRootView.unlockRenderThread();
        }
        GalleryBitmapPool.getInstance().clear();
        MediaItem.getBytesBufferPool().clear();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mGLRootView.lockRenderThread();
        try {
            getStateManager().destroy();
        } finally {
            mGLRootView.unlockRenderThread();
        }
        doUnbindBatchService();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        mGLRootView.lockRenderThread();
        try {
            getStateManager().notifyActivityResult(
                    requestCode, resultCode, data);
        } finally {
            mGLRootView.unlockRenderThread();
        }
    }

    @Override
    public void onBackPressed() {
        // send the back event to the top sub-state
        GLRoot root = getGLRoot();
        root.lockRenderThread();
        try {
            getStateManager().onBackPressed();
        } finally {
            root.unlockRenderThread();
        }
    }

    public GalleryActionBar getGalleryActionBar() {
        if (mActionBar == null) {
            mActionBar = new GalleryActionBar(this);
        }
        return mActionBar;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        GLRoot root = getGLRoot();
        root.lockRenderThread();
        try {
            return getStateManager().itemSelected(item);
        } finally {
            root.unlockRenderThread();
        }
    }

    public TransitionStore getTransitionStore() {
        return mTransitionStore;
    }

    protected boolean isFullscreen() {
        return (getWindow().getAttributes().flags
                & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0;
    }

    private BatchService mBatchService;
    private boolean mBatchServiceIsBound = false;
    private ServiceConnection mBatchServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mBatchService = ((BatchService.LocalBinder)service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mBatchService = null;
        }
    };

    private void doBindBatchService() {
        bindService(new Intent(this, BatchService.class), mBatchServiceConnection, Context.BIND_AUTO_CREATE);
        mBatchServiceIsBound = true;
    }

    private void doUnbindBatchService() {
        if (mBatchServiceIsBound) {
            // Detach our existing connection.
            unbindService(mBatchServiceConnection);
            mBatchServiceIsBound = false;
        }
    }

    public ThreadPool getBatchServiceThreadPoolIfAvailable() {
        if (mBatchServiceIsBound && mBatchService != null) {
            return mBatchService.getThreadPool();
        } else {
            throw new RuntimeException("Batch service unavailable");
        }
    }

    public void printSelectedImage(Uri uri) {
        if (uri == null) {
            return;
        }
        String printJobName = getLastPathSegment(uri);
        if (printJobName == null) {
            printJobName = DEFAULT_PRINT_JOB_NAME;
        }
        PrintHelper printer = new PrintHelper(this);
        try {
            printer.printBitmap(printJobName, uri);
        } catch (FileNotFoundException fnfe) {
            Log.e(TAG, "Error printing an image", fnfe);
        } catch (RuntimeException e) {
            Log.e(TAG,"Print failure,",e);
        }
    }

    private String getLastPathSegment(Uri uri) {
        if (uri == null) return null;
        String path = null;
        try {
            path = ImageLoader.getLocalPathFromUri(this, uri);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }

        Uri localUri;
        if (path != null) {
            localUri = Uri.parse(path);
        } else {
            path = uri.getPath();
            localUri = uri;
        }

        String lastPathSegment = localUri.getLastPathSegment();

        // uri.getLastPathSegment will return null if localUri contains
        // spacial characters such as ':', so use other way to get it.
        if (lastPathSegment == null) {
            // use substring to get last path segment.
            int indexOfLastPathSegment = path.lastIndexOf("/") + 1;
            lastPathSegment = path.substring(indexOfLastPathSegment, path.length());
        }
        return lastPathSegment;
    }

   public Toolbar getToolbar() {
       return mToolbar;
   }

    // andr36oid: the console has no touch screen. The pages get the controller's keys
    // (onPadKey) and say what the buttons do (getPadHints), shown along the bottom.

    private HintBar mHintBar;

    /** True when L1 and R1 switch between tabs (timeline, albums, videos). */
    public boolean hasPadTabs() {
        return false;
    }

    /** Goes to the next (1) or previous (-1) tab; false if there is none. */
    protected boolean switchPadTab(int direction) {
        return false;
    }

    /** The hint bar's text on the left when the page has none: where the user is. */
    protected CharSequence getPadLeading() {
        return mToolbar != null ? mToolbar.getTitle() : null;
    }

    /** The hints for the page on top, after it changed. */
    public void updatePadHints() {
        if (mHintBar == null) return;
        final StateManager states = getStateManager();
        final CharSequence[] hints = states.getStateCount() > 0
                ? states.getTopState().getPadHints() : null;
        mHintBar.set(hints != null ? hints : new CharSequence[0]);
        CharSequence leading = states.getStateCount() > 0
                ? states.getTopState().getPadLeading() : null;
        if (leading == null) leading = getPadLeading();
        mHintBar.setLeading(leading);
        // a full-screen photo: the hints show at a button press, then fade after a while
        mHintBar.removeCallbacks(mFadeHints);
        mHintBar.animate().cancel();
        mHintBar.setAlpha(1f);
        final boolean fades = states.getStateCount() > 0 && states.getTopState().fadesPadHints();
        // the photo gets the whole screen, the hints float over it
        mHintBar.setReserved(!fades);
        if (fades) {
            mHintBar.postDelayed(mFadeHints, PAD_HINTS_FADE_MS);
        }
    }

    private static final long PAD_HINTS_FADE_MS = 3000;

    private final Runnable mFadeHints = new Runnable() {
        @Override
        public void run() {
            if (mHintBar != null) mHintBar.animate().alpha(0f).setDuration(400).start();
        }
    };

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && getStateManager().getStateCount() > 0) {
            boolean used;
            final GLRoot root = getGLRoot();
            root.lockRenderThread();
            try {
                used = getStateManager().getTopState().onPadKey(event.getKeyCode(), event);
            } finally {
                root.unlockRenderThread();
            }
            if (!used && event.getRepeatCount() == 0) {
                switch (event.getKeyCode()) {
                    case KeyEvent.KEYCODE_BUTTON_Y:
                        used = showPadMenu();
                        break;
                    case KeyEvent.KEYCODE_BUTTON_L1:
                    case KeyEvent.KEYCODE_BUTTON_R1:
                        used = hasPadTabs() && switchPadTab(
                                event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_R1 ? 1 : -1);
                        break;
                }
            }
            if (used) {
                updatePadHints();
                return true;
            }
        }
        final boolean result = super.dispatchKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_UP) updatePadHints();
        return result;
    }

    /**
     * The page's menu as a list the D-pad can go through: the selection's actions while
     * some are selected, else the menu of the bar on top (even while it is hidden).
     */
    public boolean showPadMenu() {
        Menu menu = null;
        final StateManager states = getStateManager();
        if (states.getStateCount() > 0) menu = states.getTopState().getPadSelectionMenu();
        if (menu == null && mToolbar != null) menu = mToolbar.getMenu();
        return menu != null && showPadMenu(menu, null);
    }

    private boolean showPadMenu(final Menu menu, CharSequence title) {
        final ArrayList<MenuItem> items = new ArrayList<>();
        for (int i = 0; i < menu.size(); i++) {
            final MenuItem item = menu.getItem(i);
            if (item.isVisible() && item.isEnabled() && item.getTitle() != null) items.add(item);
        }
        if (items.isEmpty()) return false;
        final CharSequence[] titles = new CharSequence[items.size()];
        for (int i = 0; i < titles.length; i++) titles[i] = items.get(i).getTitle();
        final AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setItems(titles, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final MenuItem item = items.get(which);
                        if (item.hasSubMenu()) {
                            showPadMenu(item.getSubMenu(), item.getTitle());
                        } else {
                            menu.performIdentifierAction(item.getItemId(), 0);
                        }
                        updatePadHints();
                    }
                });
        if (title != null) b.setTitle(title);
        b.show();
        return true;
    }

   public void setToolbar(Toolbar toolbar) {
       mToolbar = toolbar ;
       // andr36oid: no header on the console, its actions are in the Y menu. The toolbar
       // stays for the menu and the selection mode; zero height lets the grids use the room
       if (toolbar != null) {
           final View bar = toolbar.getParent() instanceof View
                   ? (View) toolbar.getParent() : toolbar;
           bar.setVisibility(View.GONE);
           toolbar.getLayoutParams().height = 0;
       }
   }
}
