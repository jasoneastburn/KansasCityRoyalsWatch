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

package com.jasoneastburn.kansascityroyalswatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.graphics.Palette;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class RoyalsWatchFace extends CanvasWatchFaceService {
    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;
    private static final float HAND_END_CAP_RADIUS = 4.0f;
    private static final float SHADOW_RADIUS = 6.0f;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<RoyalsWatchFace.Engine> mWeakReference;

        public EngineHandler(RoyalsWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            RoyalsWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mHandPaint;
        Paint mHandPaintAmbient;
        Paint mHourPaint;
        Paint mMinutePaint;
        Paint mSecondPaint;
        Paint mTickPaint;
        Bitmap mBackgroundBitmap;
        Bitmap mBackgroundBitmapAmbient;
        boolean mAmbient;
        int mWatchHandColor = Color.WHITE;
        int mWatchHandShadowColor = Color.BLACK;
        Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        int mTapCount;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(RoyalsWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            Resources resources = RoyalsWatchFace.this.getResources();

            Drawable backgroundDrawable = resources.getDrawable(R.drawable.kcroyals, null);
            mBackgroundBitmap = ((BitmapDrawable) backgroundDrawable).getBitmap();

            Drawable backgroundDrawableAmbient = resources.getDrawable(R.drawable.kcroyalsbw, null);
            mBackgroundBitmapAmbient = ((BitmapDrawable) backgroundDrawableAmbient).getBitmap();

            //mBackgroundPaint = new Paint();
            //mBackgroundPaint.setColor(resources.getColor(R.color.background));

  /*          Palette.generateAsync(mBackgroundBitmap,
                    new Palette.PaletteAsyncListener() {
                        @Override
                        public void onGenerated(Palette palette) {
                            if (null != palette){
                                Log.d("onGenerated", palette.toString());
                                mWatchHandColor = palette.getVibrantColor(Color.WHITE);
                                mWatchHandShadowColor =
                                        palette.getDarkMutedColor(Color.BLACK);
                                setWatchHandColor();
                            }
                        }
                    });
*/
            mHandPaint = new Paint();
            mHandPaint.setColor(resources.getColor(R.color.analog_hands));
            mHandPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mHandPaint.setAntiAlias(true);
            mHandPaint.setStrokeCap(Paint.Cap.ROUND);

            mHourPaint = new Paint();
            mHourPaint.setColor(resources.getColor(R.color.analog_hands_ambient));
            mHourPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mHourPaint.setAntiAlias(true);
            mHourPaint.setStrokeCap(Paint.Cap.ROUND);
            mHourPaint.setShadowLayer(HAND_END_CAP_RADIUS,0,0, R.color.black);
            mHourPaint.setStyle(Paint.Style.STROKE);

            mMinutePaint = new Paint();
            mMinutePaint.setColor(resources.getColor(R.color.analog_hands_ambient));
            mMinutePaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mMinutePaint.setAntiAlias(true);
            mMinutePaint.setStrokeCap(Paint.Cap.ROUND);
            mMinutePaint.setShadowLayer(HAND_END_CAP_RADIUS,0,0, R.color.black);
            mMinutePaint.setStyle(Paint.Style.STROKE);

            mSecondPaint = new Paint();
            mSecondPaint.setColor(resources.getColor(R.color.analog_hands));
            mSecondPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mSecondPaint.setAntiAlias(true);

            mHandPaintAmbient = new Paint();
            mHandPaintAmbient.setColor(resources.getColor(R.color.analog_hands_ambient));
            mHandPaintAmbient.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mHandPaintAmbient.setAntiAlias(true);
            mHandPaintAmbient.setStrokeCap(Paint.Cap.ROUND);


            mTickPaint = new Paint();
            mTickPaint.setColor(resources.getColor(R.color.analog_hands_ambient));
            mTickPaint.setStrokeWidth(2.f);
            mTickPaint.setAntiAlias(true);

            mTime = new Time();
        }

        private void setWatchHandColor(){
            if (mAmbient){
                mHandPaint.setColor(Color.WHITE);
                mHandPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, Color.BLACK);
            } else {
                mHandPaint.setColor(mWatchHandColor);
                mHandPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
            }
        }


        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
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
                    mHandPaint.setAntiAlias(!inAmbientMode);
                    mSecondPaint.setAntiAlias(!inAmbientMode);
                    mMinutePaint.setAntiAlias(!inAmbientMode);
                    mHourPaint.setAntiAlias(!inAmbientMode);
                }
                //setWatchHandColor();
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

       /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.

        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = RoyalsWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            R.color.background : R.color.background2));
                    break;
            }
            invalidate();
        }
**/
        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mTime.setToNow();

            // Draw the background.
            canvas.drawBitmap(mBackgroundBitmap, 0, 0, mBackgroundPaint);
            float centerX = bounds.width() / 2f;
            float centerY = bounds.height() / 2f;

            float mScale = ((float) bounds.width()) / (float) mBackgroundBitmap.getWidth();

            mBackgroundBitmap = Bitmap.createScaledBitmap
                    (mBackgroundBitmap, (int)(mBackgroundBitmap.getWidth() * mScale),
                            (int)(mBackgroundBitmap.getHeight() * mScale), true);

            mBackgroundBitmapAmbient = Bitmap.createScaledBitmap
                    (mBackgroundBitmapAmbient, (int)(mBackgroundBitmapAmbient.getWidth() * mScale),
                            (int)(mBackgroundBitmapAmbient.getHeight() * mScale), true);

            // Draw the background.
            if (isInAmbientMode()) {
                //canvas.drawColor(Color.BLACK);
                canvas.drawBitmap(mBackgroundBitmapAmbient, 0, 0, null);
            } else {
                // canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mBackgroundPaint);
                canvas.drawBitmap(mBackgroundBitmap, 0, 0, null);
            }

            double sinVal = 0, cosVal = 0, angle = 0;
            float length1 = 0, length2 = 0;
            float x1 = 0, y1 = 0, x2 = 0, y2 = 0;

            // draw ticks
            int w = bounds.width(), h = bounds.height();
            float cx = w / 2.0f, cy = h / 2.0f;
            length1 = cx - 25;
            length2 = cx;
            for (int i = 0; i < 60; i++) {
                angle = (i * Math.PI * 2 / 60);
                sinVal = Math.sin(angle);
                cosVal = Math.cos(angle);
                float len = (i % 5 == 0) ? length1 :
                        (length1 + 15);
                x1 = (float)(sinVal * len);
                y1 = (float)(-cosVal * len);
                x2 = (float)(sinVal * length2);
                y2 = (float)(-cosVal * length2);
                canvas.drawLine(cx + x1, cy + y1, cx + x2,
                        cy + y2, mTickPaint);
            }

            float secLength = centerX - 20;
            float minLength = centerX - 40;
            float hrLength = centerX - 80;

            /*
             * These calculations reflect the rotation in degrees per unit of
             * time, e.g. 360 / 60 = 6 and 360 / 12 = 30
             */
            final float secondsRotation = mTime.second * 6f;
            final float minutesRotation = mTime.minute * 6f;
            // account for the offset of the hour hand due to minutes of the hour.
            final float hourHandOffset = mTime.minute / 2f;
            final float hoursRotation = (mTime.hour * 30) + hourHandOffset;

            // save the canvas state before we begin to rotate it
            canvas.save();

            canvas.rotate(hoursRotation, centerX, centerY);
            canvas.drawRoundRect(centerX - HAND_END_CAP_RADIUS, centerY - hrLength,
                    centerX + HAND_END_CAP_RADIUS, centerY + HAND_END_CAP_RADIUS,
                    HAND_END_CAP_RADIUS, HAND_END_CAP_RADIUS, mHourPaint);

            canvas.rotate(minutesRotation - hoursRotation, centerX, centerY);
            canvas.drawRoundRect(centerX - HAND_END_CAP_RADIUS, centerY - minLength,
                    centerX + HAND_END_CAP_RADIUS, centerY + HAND_END_CAP_RADIUS,
                    HAND_END_CAP_RADIUS, HAND_END_CAP_RADIUS, mMinutePaint);


            if (!mAmbient) {

                canvas.rotate(secondsRotation - minutesRotation, centerX, centerY);
                canvas.drawLine(centerX, centerY - HAND_END_CAP_RADIUS, centerX,
                        centerY - secLength, mHandPaint);
            }
            canvas.drawCircle(centerX, centerY, HAND_END_CAP_RADIUS, mHandPaint);
            // restore the canvas' original orientation.
            canvas.restore();

            canvas.drawCircle(centerX, centerY, HAND_END_CAP_RADIUS,
                    mHourPaint);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            RoyalsWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            RoyalsWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
