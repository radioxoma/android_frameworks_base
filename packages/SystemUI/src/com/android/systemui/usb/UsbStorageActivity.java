/*
 * Copyright (C) 2007 Google Inc.
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

package com.android.systemui.usb;

import com.android.internal.R;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface.OnCancelListener;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.hardware.Usb;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.storage.IMountService;
import android.os.storage.StorageManager;
import android.os.storage.StorageEventListener;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.util.Log;

import java.util.List;

/**
 * This activity is shown to the user for him/her to enable USB mass storage
 * on-demand (that is, when the USB cable is connected). It uses the alert
 * dialog style. It will be launched from a notification.
 */
public class UsbStorageActivity extends Activity
        implements View.OnClickListener, OnCancelListener {
    private static final String TAG = "UsbStorageActivity";

    private Button mMountButton;
    private Button mUnmountButton;
    private ProgressBar mProgressBar;
    private TextView mBanner;
    private TextView mMessage;
    private ImageView mIcon;
    private TextView chargingIcon;
    private TextView usbIcon;
    private TextView usbIconLarger;
    private TextView usbMsg;

    private AlertDialog ad = null;
    private StorageManager mStorageManager = null;
    private static final int DLG_CONFIRM_KILL_STORAGE_USERS = 1;
    private static final int DLG_ERROR_SHARING = 2;
    static final boolean localLOGV = true;
    private boolean dialogIsThere = false;
    private boolean mWasUsbStorageInUse = false;

    // UI thread
    private Handler mUIHandler;

    // thread for working with the storage services, which can be slow
    private Handler mAsyncStorageHandler;

    /** Used to detect when the USB cable is unplugged, so we can call finish() */
    private BroadcastReceiver mUsbStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            System.out.println(" usb disconnect broadcast   intent.getAction()-->" + intent.getAction());
            if (intent.getAction().equals(Usb.ACTION_USB_STATE)) {
                handleUsbStateChanged(intent);
            } else if (intent.getAction().equals(Intent.ACTION_CLOSE_STATUSBAR_USB)) {
                Log.i(TAG, "receive ACTION_CLOSE_STATUSBAR_USB broadcast");
                System.out.println(" usb disconnect broadcast dialogIsThere-->" + dialogIsThere);
                if (dialogIsThere) {
                    dismissDialog(DLG_CONFIRM_KILL_STORAGE_USERS);
                    dialogIsThere = false;
                }
                finish();
            }
        }
    };

    private StorageEventListener mStorageListener = new StorageEventListener() {
        @Override
        public void onStorageStateChanged(String path, String oldState, String newState) {
            if (newState.equals(Environment.MEDIA_SHARED) &&
                    path.equals(Environment.getFlashStorageDirectory().getPath())) {
                switchDisplay(true);
            } else if (newState.equals(Environment.MEDIA_MOUNTED) &&
                    path.equals(Environment.getFlashStorageDirectory().getPath())) {
                switchDisplay(false);
            }
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null && savedInstanceState.containsKey("killstorageusers")) {
            showDialog(1);
            ad.onRestoreInstanceState(savedInstanceState.getBundle("killstorageusers"));
        }

        if (mStorageManager == null) {
            mStorageManager = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
            if (mStorageManager == null) {
                Log.w(TAG, "Failed to get StorageManager");
            }
        }
        
        mUIHandler = new Handler();

        HandlerThread thr = new HandlerThread("SystemUI UsbStorageActivity");
        thr.start();
        mAsyncStorageHandler = new Handler(thr.getLooper());

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setProgressBarIndeterminateVisibility(true);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        if (Environment.isExternalStorageRemovable()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }

        setTitle(getString(com.android.internal.R.string.usb_storage_activity_title));

        setContentView(com.android.internal.R.layout.usb_storage_activity);

        mIcon = (ImageView) findViewById(com.android.internal.R.id.icon);
        mBanner = (TextView) findViewById(com.android.internal.R.id.banner);
        mMessage = (TextView) findViewById(com.android.internal.R.id.message);

        mMountButton = (Button) findViewById(com.android.internal.R.id.mount_button);
        mMountButton.setOnClickListener(this);
        mUnmountButton = (Button) findViewById(com.android.internal.R.id.unmount_button);
        mUnmountButton.setOnClickListener(this);
        mProgressBar = (ProgressBar) findViewById(com.android.internal.R.id.progress);
        switchDisplay(false, true);
    }

    private void switchDisplay(final boolean usbStorageInUse) {
        switchDisplay(usbStorageInUse, false);
    }

    private void switchDisplay(final boolean usbStorageInUse, final boolean force) {
        if (usbStorageInUse == mWasUsbStorageInUse && !force) {
            return;
        }
        mWasUsbStorageInUse = usbStorageInUse;
        mUIHandler.post(new Runnable() {
            @Override
            public void run() {
                switchDisplayAsync(usbStorageInUse);
            }
        });
    }

    private void switchDisplayAsync(boolean usbStorageInUse) {
        if (usbStorageInUse) {
            mProgressBar.setVisibility(View.GONE);
            mUnmountButton.setEnabled(true);
            mUnmountButton.setVisibility(View.VISIBLE);
            mMountButton.setEnabled(false);
            mMountButton.setVisibility(View.GONE);
            mIcon.setImageResource(com.android.internal.R.drawable.usb_android_connected);
            mBanner.setText(com.android.internal.R.string.usb_storage_stop_title);
            mMessage.setText(com.android.internal.R.string.usb_storage_stop_message);
        } else {
            mProgressBar.setVisibility(View.GONE);
            mUnmountButton.setEnabled(false);
            mUnmountButton.setVisibility(View.GONE);
            mMountButton.setEnabled(true);
            mMountButton.setVisibility(View.VISIBLE);
            mIcon.setImageResource(com.android.internal.R.drawable.usb_android);
            mBanner.setText(com.android.internal.R.string.usb_storage_title);
            mMessage.setText(com.android.internal.R.string.usb_storage_message);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        mMountButton.setEnabled(true);
        mUnmountButton.setEnabled(true);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CLOSE_STATUSBAR_USB);
        filter.addAction(Usb.ACTION_USB_STATE);
        mStorageManager.registerListener(mStorageListener);
        registerReceiver(mUsbStateReceiver, filter);
        try {
            mAsyncStorageHandler.post(new Runnable() {
                @Override
                public void run() {
                    switchDisplay(mStorageManager.isUsbMassStorageEnabled());
                }
            });
        } catch (Exception ex) {
            Log.e(TAG, "Failed to read UMS enable state", ex);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        unregisterReceiver(mUsbStateReceiver);
        if (mStorageManager == null && mStorageListener != null) {
            mStorageManager.unregisterListener(mStorageListener);
        }
    }

    private void handleUsbStateChanged(Intent intent) {
        boolean connected = intent.getExtras().getBoolean(Usb.USB_CONNECTED);
        if (!connected) {
            // It was disconnected from the plug, so finish
            System.out.println("handleUsbStateChanged-dialogIsThere->" + dialogIsThere);
            if (dialogIsThere) {
                dismissDialog(DLG_CONFIRM_KILL_STORAGE_USERS);
                dialogIsThere = false;
            }
            finish();
        }
    }

    private IMountService getMountService() {
        IBinder service = ServiceManager.getService("mount");
        if (service != null) {
            return IMountService.Stub.asInterface(service);
        }
        return null;
    }

    @Override
    public Dialog onCreateDialog(int id, Bundle args) {
        switch (id) {
        case DLG_CONFIRM_KILL_STORAGE_USERS:
            dialogIsThere = true;
            ad = new AlertDialog.Builder(this)
                    .setTitle(R.string.dlg_confirm_kill_storage_users_title)
                    .setPositiveButton(R.string.dlg_ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dismissDialog(DLG_CONFIRM_KILL_STORAGE_USERS);
                            dialogIsThere = false;
                            switchUsbMassStorage(true);
                        }})
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dismissDialog(DLG_CONFIRM_KILL_STORAGE_USERS);
                            dialogIsThere = false;
                            finish();
                        }})
                    .setMessage(R.string.dlg_confirm_kill_storage_users_text)
                    .setOnCancelListener(this)
                    .create();
            return ad;
        case DLG_ERROR_SHARING:
            ad = new AlertDialog.Builder(this)
                    .setTitle(R.string.dlg_error_title)
                    .setNeutralButton(R.string.dlg_ok, null)
                    .setMessage(R.string.usb_storage_error_message)
                    .setOnCancelListener(this)
                    .create();
            return ad;
        }
        return null;
    }

    private void showDialogInner(int id) {
        removeDialog(id);
        showDialog(id);
    }

    private void switchUsbMassStorage(final boolean on) {
        // things to do on the UI thread
        mUIHandler.post(new Runnable() {
            @Override
            public void run() {
                mUnmountButton.setVisibility(View.GONE);
                mMountButton.setVisibility(View.GONE);

                mProgressBar.setVisibility(View.VISIBLE);
                // will be hidden once USB mass storage kicks in (or fails)
            }
        });
        
        // things to do elsewhere
        mAsyncStorageHandler.post(new Runnable() {
            @Override
            public void run() {
                if (on) {
                    mStorageManager.enableUsbMassStorage();
                } else {
                    mStorageManager.disableUsbMassStorage();
                }
            }
        });
    }

    private void checkStorageUsers() {
        mAsyncStorageHandler.post(new Runnable() {
            @Override
            public void run() {
                checkStorageUsersAsync();
            }
        });
    }

    private void checkStorageUsersAsync() {
        IMountService ims = getMountService();
        if (ims == null) {
            // Display error dialog
            showDialogInner(DLG_ERROR_SHARING);
        }
        String extStoragePath = Environment.getExternalStorageDirectory().toString();
        String flashStoragePath = Environment.getFlashStorageDirectory().toString();
        boolean showDialog = false;
        try {
            int[] stUsers = ims.getStorageUsers(extStoragePath);
            if (ims.getVolumeState(extStoragePath).equals(Environment.MEDIA_MOUNTED)) {
                if (stUsers != null && stUsers.length > 0) {
                    showDialog = true;
                }
            }
            stUsers = ims.getStorageUsers(flashStoragePath);
            if (stUsers != null && stUsers.length > 0) {
                showDialog = true;
            } else {
                // List of applications on sdcard.
                ActivityManager am = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
                List<ApplicationInfo> infoList = am.getRunningExternalApplications();
                if (infoList != null && infoList.size() > 0) {
                    showDialog = true;
                }
            }
        } catch (RemoteException e) {
            // Display error dialog
            showDialogInner(DLG_ERROR_SHARING);
        }
        if (showDialog) {
            // Display dialog to user
            showDialogInner(DLG_CONFIRM_KILL_STORAGE_USERS);
        } else {
            if (localLOGV) Log.i(TAG, "Enabling UMS");
            switchUsbMassStorage(true);
        }
    }

    public void onClick(View v) {
        if (v == mMountButton) {
           // Check for list of storage users and display dialog if needed.
            mMountButton.setEnabled(false);
            mUnmountButton.setEnabled(true);
            checkStorageUsers();
        } else if (v == mUnmountButton) {
            if (localLOGV) Log.i(TAG, "Disabling UMS");
            mMountButton.setEnabled(true);
            mUnmountButton.setEnabled(false);
            switchUsbMassStorage(false);
        }
    }

    public void onCancel(DialogInterface dialog) {
        if (dialogIsThere) {
            dismissDialog(DLG_CONFIRM_KILL_STORAGE_USERS);
            dialogIsThere = false;
        }
        finish();
    }

    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (ad != null && dialogIsThere) {
            state.putBundle("killstorageusers", ad.onSaveInstanceState());
            dismissDialog(1);
        }
    }
}
