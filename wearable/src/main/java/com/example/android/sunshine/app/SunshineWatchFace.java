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
import android.os.Bundle;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;

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

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }


    private class Engine extends CanvasWatchFaceService.Engine {
        boolean mRegisteredTimeZoneReceiver = false;

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

        String mMaxTemp = "25";
        String mMinTemp = "16";

        Bitmap mWeatherIconBitmap;

        SimpleDateFormat mDateFormatter;
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

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

            mWeatherIconBitmap = BitmapFactory.decodeResource(resources, R.drawable.art_clear);

            mCalendar = Calendar.getInstance();
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

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
            } else {
                unregisterReceiver();
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

            canvas.drawBitmap(mWeatherIconBitmap,
                    bounds.centerX() + (maxTempTextSize)/2 - 20,
                    mWeatherInfoYOffset + 15,
                    mWeatherIconPaint);

            canvas.drawText(mMaxTemp,
                    bounds.centerX() - (maxTempTextSize)/2,
                    mWeatherInfoYOffset,
                    mMaxTempPaint);

            canvas.drawText(mMinTemp,
                    bounds.centerX() + (maxTempTextSize)/2 + 20,
                    mWeatherInfoYOffset,
                    mMinTempPaint);
        }
    }
}
