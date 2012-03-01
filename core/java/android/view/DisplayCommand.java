/*
* Copyright (C) 2012 Freescale Semiconductor, Inc. All Rights Reserved.
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

package android.view;
import android.util.Slog;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ServiceManager;
import android.os.RemoteException;

public class DisplayCommand {
    static final String TAG = "Display";
    public static final int OPERATE_CODE_ENABLE =  0x1000;
    public static final int OPERATE_CODE_DISABLE = 0x2000;
    public static final int OPERATE_CODE_CHANGE =  0x4000;

    public static final int OPERATE_CODE_CHANGE_RESOLUTION = 0x1;
    public static final int OPERATE_CODE_CHANGE_OVERSCAN = 0x2;
    public static final int OPERATE_CODE_CHANGE_MIRROR = 0x4;
    public static final int OPERATE_CODE_CHANGE_COLORDEPTH = 0x8;
    public static final int OPERATE_CODE_CHANGE_ROTATION = 0x10;

    IBinder mSurfaceFlinger = null;
    ConfigParam mCfgParam = new ConfigParam();

    public DisplayCommand() {
        IBinder surfaceFlinger = ServiceManager.getService("SurfaceFlinger");
        if (surfaceFlinger != null) {
            mSurfaceFlinger = surfaceFlinger;
        }
    }
 
    public static class ConfigParam {
        int displayId;
        int operateCode; //operate code: enable, change or disable display.
        //int width;
        //int height;
        int rotation;
        int overScan;
        int mirror;
        int colorDepth;
        String mode;

        public ConfigParam() {
            displayId = -1;
            operateCode = 0;
            //width = 0;
            //height = 0;
            mode = null;
            rotation = 0;
            overScan = 0;
            mirror = 0;
            colorDepth = 0;
        }

        public void writeToParcel(Parcel out) {
            out.writeInt(displayId);
            out.writeInt(operateCode);
            //out.writeInt(width);
            //out.writeInt(height);
            out.writeInt(rotation);
            out.writeInt(overScan);
            out.writeInt(mirror);
            out.writeInt(colorDepth);
            out.writeString(mode);
        }
    }

    private int transferParam() {
        int ret = 0;

        try {
            Parcel reply = Parcel.obtain();
            Parcel data = Parcel.obtain();
            data.writeInterfaceToken("android.ui.ISurfaceComposer");
            mCfgParam.writeToParcel(data);
            //CONFIG_DISPLAY = IBinder::FIRST_CALL_TRANSACTION + 100
            mSurfaceFlinger.transact(IBinder.FIRST_CALL_TRANSACTION + 100,
                                    data, reply, 0);

            ret = reply.readInt();
            reply.recycle();
            data.recycle();
        } catch(RemoteException ex) {
            Slog.e(TAG, "DisplayCommand catch exception!");
        }

        return ret;
    }

    public int enable(int displayId, String mode, int rotation,
                          int overScan, int mirror, int colorDepth) { 
        int ret = 0;

        if (mSurfaceFlinger == null) {
            Slog.e(TAG, "DisplayCommand: mSurfaceFlinger=null!");
            return -1;
        }

        mCfgParam.displayId = displayId;
        mCfgParam.operateCode = OPERATE_CODE_ENABLE | OPERATE_CODE_CHANGE_RESOLUTION | OPERATE_CODE_CHANGE_OVERSCAN |
                      OPERATE_CODE_CHANGE_MIRROR | OPERATE_CODE_CHANGE_COLORDEPTH | OPERATE_CODE_CHANGE_ROTATION;
        //mCfgParam.width = width;
        //mCfgParam.height = height;
        mCfgParam.mode = mode;
        mCfgParam.rotation = rotation;
        mCfgParam.overScan = overScan;
        mCfgParam.mirror = mirror;
        mCfgParam.colorDepth = colorDepth;

        ret = transferParam();

        return ret;
    }

    public int disable(int displayId) {
        int ret = 0;

        if (mSurfaceFlinger == null) {
            Slog.e(TAG, "DisplayCommand: mSurfaceFlinger=null!");
            return -1;
        }

        mCfgParam.displayId = displayId;
        mCfgParam.operateCode = OPERATE_CODE_DISABLE;

        ret = transferParam();
        return ret;
    }

    public int setResolution(int displayId, String mode) {
        int ret = 0;

        if (mSurfaceFlinger == null) {
            Slog.e(TAG, "DisplayCommand: mSurfaceFlinger=null!");
            return -1;
        }

        mCfgParam.displayId = displayId;
        mCfgParam.operateCode = OPERATE_CODE_CHANGE | OPERATE_CODE_CHANGE_RESOLUTION;
        //mCfgParam.width = width;
        //mCfgParam.height = height;
        mCfgParam.mode = mode;
        ret = transferParam();
        return ret;
    }

    public int setOverScan(int displayId, int ratio) {
        int ret = 0;

        if (mSurfaceFlinger == null) {
            Slog.e(TAG, "DisplayCommand: mSurfaceFlinger=null!");
            return -1;
        }

        mCfgParam.displayId = displayId;
        mCfgParam.operateCode = OPERATE_CODE_CHANGE | OPERATE_CODE_CHANGE_OVERSCAN;
        mCfgParam.overScan = ratio;

        ret = transferParam();
        return ret;
    }

    public int setMirror(int displayId, int mirror) {
        int ret = 0;

        if (mSurfaceFlinger == null) {
            Slog.e(TAG, "DisplayCommand: mSurfaceFlinger=null!");
            return -1;
        }

        mCfgParam.displayId = displayId;
        mCfgParam.operateCode = OPERATE_CODE_CHANGE | OPERATE_CODE_CHANGE_MIRROR;
        mCfgParam.mirror = mirror;

        ret = transferParam();
        return ret;
    }

    public int setColorDepth(int displayId, int depth) {
        int ret = 0;

        if (mSurfaceFlinger == null) {
            Slog.e(TAG, "DisplayCommand: mSurfaceFlinger=null!");
            return -1;
        }

        mCfgParam.displayId = displayId;
        mCfgParam.operateCode = OPERATE_CODE_CHANGE | OPERATE_CODE_CHANGE_COLORDEPTH;
        mCfgParam.colorDepth = depth;

        ret = transferParam();
        return ret;
    }

    public int setRotation(int displayId, int rotation) {
        int ret = 0;

        if (mSurfaceFlinger == null) {
            Slog.e(TAG, "DisplayCommand: mSurfaceFlinger=null!");
            return -1;
        }

        mCfgParam.displayId = displayId;
        mCfgParam.operateCode = OPERATE_CODE_CHANGE | OPERATE_CODE_CHANGE_ROTATION;
        mCfgParam.rotation = rotation;

        ret = transferParam();
        return ret;
    }

    public int broadcastEvent() {
        int ret = 0;

        //try {
        //    ;

        //} catch(RemoteException ex) {
        //    Slog.e(TAG, "DisplayCommand enable failed!");
        //}
        return ret;
    }

}

