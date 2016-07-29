/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final String TAG = SunshineWatchFace.class.getSimpleName();

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }


    private class Engine extends CanvasWatchFaceService.Engine implements
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener,
            DataApi.DataListener{
        boolean mRegisteredTimeZoneReceiver = false;

        final String WEATHER_DATA_PATH = "/weather-data";
        final String ICON_KEY = "icon";
        final String HIGH_TEMP_KEY = "high";
        final String LOW_TEMP_KEY = "low";

        Paint mBackgroundPaint;
        Paint mTimePaint;
        Paint mDatePaint;
        Paint mLinePaint;
        Paint mMaxTempPaint;
        Paint mMinTempPaint;
        Paint mWeatherIconPaint;

        boolean mAmbient;
        Calendar mCalendar;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        float mYTimeOffset;
        float mYDateOffset;
        float mYLineOffset;
        float mWeatherInfoYOffset;

        String mMaxTemp = "";
        String mMinTemp = "";

        Bitmap mWeatherIconBitmap = null;

        SimpleDateFormat mDateFormatter;

        final long TIMEOUT_MS = 100;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            mDateFormatter = new SimpleDateFormat("EEE, MMM dd yyyy");

            Resources resources = SunshineWatchFace.this.getResources();
            mYTimeOffset = resources.getDimension(R.dimen.time_y_offset);
            mYDateOffset = resources.getDimension(R.dimen.date_y_offset);
            mYLineOffset = resources.getDimension(R.dimen.line_y_offset);
            mWeatherInfoYOffset = resources.getDimension(R.dimen.weather_info_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTimePaint = createTextPaint(resources.getColor(R.color.white_color));
            mDatePaint = createTextPaint(resources.getColor(R.color.light_blue));
            mMaxTempPaint = createTextPaint(resources.getColor(R.color.white_color));
            mMinTempPaint = createTextPaint(resources.getColor(R.color.light_blue));

            mTimePaint.setTextSize(resources.getDimension(R.dimen.time_text_size));
            mDatePaint.setTextSize(resources.getDimension(R.dimen.date_text_size));
            mMaxTempPaint.setTextSize(resources.getDimension(R.dimen.temp_text_size));
            mMinTempPaint.setTextSize(resources.getDimension(R.dimen.temp_text_size));

            mLinePaint = new Paint();
            mLinePaint.setColor(resources.getColor(R.color.light_blue));

            mWeatherIconPaint = new Paint();

            mCalendar = Calendar.getInstance();
        }


        @Override
        public void onConnected(@Nullable Bundle bundle) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnected: " + bundle);
            }
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
        }

        @Override
        public void onConnectionSuspended(int i) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionSuspended: " + i);
            }
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEvents    ) {
            Log.d(TAG, "onDataChanged is called");

            for (DataEvent event : dataEvents) {
                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    String path = event.getDataItem().getUri().getPath();
                    if (WEATHER_DATA_PATH.equals(path)) {
                        DataMap dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();

                        mMaxTemp = dataMap.getString(HIGH_TEMP_KEY);
                        mMinTemp = dataMap.getString(LOW_TEMP_KEY);
                        Log.d(TAG, "dataItem path:" + WEATHER_DATA_PATH);

                        Asset iconAsset = dataMap.getAsset(ICON_KEY);
                        new LoadBitmapAsyncTask().execute(iconAsset);

                    }else {
                        Log.d(TAG, "Unrecognized path: " + path);
                    }
                }
            }
        }


        private class LoadBitmapAsyncTask extends AsyncTask<Asset, Void, Bitmap> {

            @Override
            protected Bitmap doInBackground(Asset... params) {

                if (params.length > 0) {

                    Asset asset = params[0];

                    InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                            mGoogleApiClient, asset).await().getInputStream();

                    if (assetInputStream == null) {
                        Log.w(TAG, "Requested an unknown Asset.");
                        return null;
                    }
                    return BitmapFactory.decodeStream(assetInputStream);

                } else {
                    Log.e(TAG, "Asset must be non-null");
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {

                if (bitmap != null) {
                    Log.d(TAG, "Setting weather icon");
                    mWeatherIconBitmap = bitmap;
                }

                invalidate();
            }
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionFailed: " + connectionResult);
            }
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                mGoogleApiClient.connect();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
            } else {
                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }


        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTimePaint.setAntiAlias(!inAmbientMode);
                    mDatePaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }
        }


        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mCalendar.setTimeInMillis(System.currentTimeMillis());

            String timeText = String.format("%02d:%02d", mCalendar.get(Calendar.HOUR), mCalendar.get(Calendar.MINUTE));
            String dateText = mDateFormatter.format(mCalendar.getTime());

            canvas.drawText(timeText,
                    bounds.centerX() - (mTimePaint.measureText(timeText))/2,
                    mYTimeOffset,
                    mTimePaint);

            canvas.drawText(dateText,
                    bounds.centerX() - (mDatePaint.measureText(dateText))/2,
                    mYDateOffset,
                    mDatePaint);

            canvas.drawLine(bounds.centerX()-25,
                    mYLineOffset,
                    bounds.centerX()+25,
                    mYLineOffset,
                    mLinePaint);

            float maxTempTextSize = mDatePaint.measureText(mMaxTemp);

            if(mWeatherIconBitmap!=null) {
                canvas.drawBitmap(mWeatherIconBitmap,
                        bounds.centerX() + (maxTempTextSize) / 2 - 100,
                        mWeatherInfoYOffset - 45,
                        mWeatherIconPaint);
            }

            canvas.drawText(mMaxTemp,
                    bounds.centerX() - (maxTempTextSize)/2,
                    mWeatherInfoYOffset,
                    mMaxTempPaint);

            canvas.drawText(mMinTemp,
                    bounds.centerX() + (maxTempTextSize)/2 + 30,
                    mWeatherInfoYOffset,
                    mMinTempPaint);
        }
    }
}
