/*
 * Copyright (C) 2008 Google Inc.
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

package github.com.kikeEsteban.audioBrowser.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import github.com.kikeEsteban.audioBrowser.app.soundfile.CheapSoundFile;

/**
 * WaveformView is an Android view that displays a visual representation
 * of an audio waveform.  It retrieves the frame gains from a CheapSoundFile
 * object and recomputes the shape contour at several zoom levels.
 *
 * This class doesn't handle selection or any of the touch interactions
 * directly, so it exposes a listener interface.  The class that embeds
 * this view should add itself as a listener and make the view scroll
 * and respond to other events appropriately.
 *
 * WaveformView doesn't actually handle selection, but it will just display
 * the selected part of the waveform in a different color.
 */
public class WaveformView extends View {
    public interface WaveformListener {
        public void waveformTouchStart(float x);
        public void waveformTouchMove(float x);
        public void waveformTouchEnd();
        public void waveformFling(float x);
        public void waveformDraw();
        public void waveformZoomIn();
        public void waveformZoomOut();
    };

    // Colors
    private Paint mGridPaint;
    private Paint mSelectedLinePaint;
    private Paint mSelectedAreaPaint;
    private Paint mSelectedAreaOncePaint;
    private Paint mUnselectedLinePaint;
    private Paint mUnselectedBkgndLinePaint;
    private Paint mBorderLinePaint;
    private Paint mPlaybackLinePaint;
    private Paint mTimecodePaint;

    private CheapSoundFile mSoundFile;
    private int[] mLenByZoomLevel;
    private double[][] mValuesByZoomLevel;
    private double[] mZoomFactorByZoomLevel;
    private int[] mHeightsAtThisZoomLevel;
    private int mZoomLevel;
    private int mNumZoomLevels;
    private int mSampleRate;
    private int mSamplesPerFrame;
    private int mOffset;
    private int mSelectionStart;
    private int mSelectionEnd;
    private int mPlaybackPos;
    private float mDensity;
    private float mInitialScaleSpan;
    private WaveformListener mListener;
    private GestureDetector mGestureDetector;
    private ScaleGestureDetector mScaleGestureDetector;
    private boolean mInitialized;
    private GLRenderer mWaveRenderer;
    private int mCurrentNumOfHeights = 0;

    public static final int NO_LOOP_MODE = 0;
    public static final int CONTINUOUS_LOOP_MODE = 1;
    public static final int ONCE_LOOP_MODE = 2;

    private int mLoopMode = CONTINUOUS_LOOP_MODE;

    public WaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // We don't want keys, the markers get these
        setFocusable(false);

        mGridPaint = new Paint();
        mGridPaint.setAntiAlias(false);
        mGridPaint.setColor(
                Color.argb(255,100,100,100));
        mSelectedLinePaint = new Paint();
        mSelectedLinePaint.setAntiAlias(false);
        mSelectedLinePaint.setColor(
                Color.argb(255,255,255,0));
        mSelectedAreaPaint = new Paint();
        mSelectedAreaPaint.setAntiAlias(false);
        mSelectedAreaPaint.setColor(
                Color.argb(100,255,255,0));
        mSelectedAreaOncePaint = new Paint();
        mSelectedAreaOncePaint.setAntiAlias(false);
        mSelectedAreaOncePaint.setColor(
                Color.argb(100,205,105,0));

        mUnselectedLinePaint = new Paint();
        mUnselectedLinePaint.setAntiAlias(false);
                mUnselectedLinePaint.setColor(
                Color.argb(255,255,255,0));
        mUnselectedBkgndLinePaint = new Paint();
        mUnselectedBkgndLinePaint.setAntiAlias(false);
        mUnselectedBkgndLinePaint.setColor(
                     Color.argb(100,0,255,0) );
        mBorderLinePaint = new Paint();
        mBorderLinePaint.setAntiAlias(true);
        mBorderLinePaint.setStrokeWidth(1.5f);
        mBorderLinePaint.setPathEffect(
            new DashPathEffect(new float[] { 3.0f, 2.0f }, 0.0f));
//        mBorderLinePaint.setColor(
//            getResources().getColor(R.drawable.selection_border));
        mPlaybackLinePaint = new Paint();
        mPlaybackLinePaint.setAntiAlias(false);
        mPlaybackLinePaint.setColor(
            Color.argb(255,255,100,0));
        mTimecodePaint = new Paint();
        mTimecodePaint.setTextSize(12);
        mTimecodePaint.setAntiAlias(true);

        mTimecodePaint.setColor(
                Color.argb(255,255,255,255));
        mTimecodePaint.setShadowLayer(
            2, 1, 1,
            Color.argb(100,100,100,100));

	mGestureDetector = new GestureDetector(
	        context,
		new GestureDetector.SimpleOnGestureListener() {
		    public boolean onFling(
			        MotionEvent e1, MotionEvent e2, float vx, float vy) {
			mListener.waveformFling(vx);
			return true;
		    }
		});

	mScaleGestureDetector = new ScaleGestureDetector(
	        context,
		new ScaleGestureDetector.SimpleOnScaleGestureListener() {
		    public boolean onScaleBegin(ScaleGestureDetector d) {
                        Log.i("Ringdroid", "ScaleBegin " + d.getCurrentSpanX());
                        mInitialScaleSpan = Math.abs(d.getCurrentSpanX());
			return true;
		    }
		    public boolean onScale(ScaleGestureDetector d) {
                        float scale = Math.abs(d.getCurrentSpanX());
                        Log.i("Ringdroid", "Scale " + (scale - mInitialScaleSpan));
                        if (scale - mInitialScaleSpan > 40) {
                            mListener.waveformZoomIn();
                            mInitialScaleSpan = scale;
                            justZoom = true;
                        }
                        if (scale - mInitialScaleSpan < -40) {
                            mListener.waveformZoomOut();
                            mInitialScaleSpan = scale;
                            justZoom = true;
                        }
			return true;
		    }
		    public void onScaleEnd(ScaleGestureDetector d) {
                        Log.i("Ringdroid", "ScaleEnd " + d.getCurrentSpanX());
		    }
		});

        mSoundFile = null;
        mLenByZoomLevel = null;
        mValuesByZoomLevel = null;
        mHeightsAtThisZoomLevel = null;
        mOffset = 0;
        mPlaybackPos = -1;
        mSelectionStart = 0;
        mSelectionEnd = 0;
        mDensity = 1.0f;
        mInitialized = false;
    }

    public void setWaveRenderer(GLRenderer waveRenderer){
        this.mWaveRenderer = waveRenderer;
    }

    private boolean justZoom = false;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
	mScaleGestureDetector.onTouchEvent(event);
	if (mGestureDetector.onTouchEvent(event)) {
	    return true;
	}

        switch(event.getAction()) {
        case MotionEvent.ACTION_DOWN:
            justZoom = false;
            mListener.waveformTouchStart(event.getX());
            break;
        case MotionEvent.ACTION_MOVE:
            if(!justZoom)
                mListener.waveformTouchMove(event.getX());
            break;
        case MotionEvent.ACTION_UP:
            mListener.waveformTouchEnd();
            break;
        }
        return true;
    }

    public boolean hasSoundFile() {
        return mSoundFile != null;
    }

    public void setSoundFile(CheapSoundFile soundFile) {
        mSoundFile = soundFile;
        mSampleRate = mSoundFile.getSampleRate();
        mSamplesPerFrame = mSoundFile.getSamplesPerFrame();
        computeDoublesForAllZoomLevels();
        mHeightsAtThisZoomLevel = null;
    }

    public boolean isInitialized() {
        return mInitialized;
    }

    public int getZoomLevel() {
        return mZoomLevel;
    }

    public void setZoomLevel(int zoomLevel) {
        while (mZoomLevel > zoomLevel) {
            zoomIn();
        }
        while (mZoomLevel < zoomLevel) {
            zoomOut();
        }
    }

    public boolean canZoomIn() {
        return (mZoomLevel > 0);
    }

    public void zoomIn() {
        if (canZoomIn()) {
            mZoomLevel--;
            mSelectionStart *= 2;
            mSelectionEnd *= 2;
            mCurrentNumOfHeights *= 2;
            mHeightsAtThisZoomLevel = null;
            int offsetCenter = mOffset + getMeasuredWidth() / 2;
            offsetCenter *= 2;
            mOffset = offsetCenter - getMeasuredWidth() / 2;
            if (mOffset < 0)
                mOffset = 0;
            invalidate();
        }
    }

    public boolean canZoomOut() {
        return (mZoomLevel < mNumZoomLevels - 1);
    }

    public void zoomOut() {
        if (canZoomOut()) {
            mZoomLevel++;
            mSelectionStart /= 2;
            mSelectionEnd /= 2;
            mCurrentNumOfHeights /= 2;
            int offsetCenter = mOffset + getMeasuredWidth() / 2;
            offsetCenter /= 2;
            mOffset = offsetCenter - getMeasuredWidth() / 2;
            if (mOffset < 0)
                mOffset = 0;
            mHeightsAtThisZoomLevel = null;
            invalidate();
        }
    }

    public int maxPos() {
        return mLenByZoomLevel[mZoomLevel];
    }

    public int secondsToFrames(double seconds) {
        return (int)(1.0 * seconds * mSampleRate / mSamplesPerFrame + 0.5);
    }

    public int secondsToPixels(double seconds) {
        double z = mZoomFactorByZoomLevel[mZoomLevel];
        return (int)(z * seconds * mSampleRate / mSamplesPerFrame + 0.5);
    }

    public double pixelsToSeconds(int pixels) {
        double z = mZoomFactorByZoomLevel[mZoomLevel];
        return (pixels * (double)mSamplesPerFrame / (mSampleRate * z));
    }

    public int millisecsToPixels(int msecs) {
        double z = mZoomFactorByZoomLevel[mZoomLevel];
        return (int)((msecs * 1.0 * mSampleRate * z) /
                     (1000.0 * mSamplesPerFrame) + 0.5);
    }

    public int pixelsToMillisecs(int pixels) {
        double z = mZoomFactorByZoomLevel[mZoomLevel];
        return (int)(pixels * (1000.0 * mSamplesPerFrame) /
                     (mSampleRate * z) + 0.5);
    }

    public void setParameters(int start, int end, int offset) {
        mSelectionStart = start;
        mSelectionEnd = end;
        mOffset = offset;
    }

    public int getStart() {
        return mSelectionStart;
    }

    public int getEnd() {
        return mSelectionEnd;
    }

    public int getOffset() {
        return mOffset;
    }

    public void setPlayback(int pos) {
        mPlaybackPos = pos;
    }

    public void setListener(WaveformListener listener) {
        mListener = listener;
    }

    public void recomputeHeights(float density) {
        mHeightsAtThisZoomLevel = null;
        mDensity = density;
        mTimecodePaint.setTextSize((int)(12 * density));

        invalidate();
    }

    protected void drawWaveformLine(Canvas canvas,
                                    int x, int y0, int y1,
                                    Paint paint) {
        canvas.drawLine(x, y0, x, y1, paint);
    }


    public int getNumOfHeightAtThisZoomLevel(){
        return mCurrentNumOfHeights;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mSoundFile == null)
            return;

        if (mHeightsAtThisZoomLevel == null)
            computeIntsForThisZoomLevel();

        // Draw waveform
        int measuredWidth = getMeasuredWidth();
        int measuredHeight = getMeasuredHeight();
        int start = mOffset;
        int width = mHeightsAtThisZoomLevel.length - start;
        int ctr = measuredHeight / 2;

        if (width > measuredWidth)
            width = measuredWidth;

        // Draw grid
        double onePixelInSecs = pixelsToSeconds(1);
        boolean onlyEveryFiveSecs = (onePixelInSecs > 1.0 / 50.0);
        double fractionalSecs = mOffset * onePixelInSecs;
        int integerSecs = (int) fractionalSecs;
        int i = 0;
        while (i < width) {
            i++;
            fractionalSecs += onePixelInSecs;
            int integerSecsNew = (int) fractionalSecs;
            if (integerSecsNew != integerSecs) {
                integerSecs = integerSecsNew;
                if (!onlyEveryFiveSecs || 0 == (integerSecs % 5)) {
                    canvas.drawLine(i, 0, i, measuredHeight, mGridPaint);
                }
            }
        }


        float[] newHeights = new float[width];
        for (i = 0; i < width; i++) {
            newHeights[i] = mHeightsAtThisZoomLevel[start + i];
        }

        mWaveRenderer.setData(newHeights,start);

        if(mPlaybackPos-start>=0){
            canvas.drawLine(mPlaybackPos-start, 0, mPlaybackPos-start, measuredHeight, mPlaybackLinePaint);
        }

        if((mSelectionStart-start>=0)&& (mSelectionStart-start<measuredWidth)){
            if(mLoopMode == CONTINUOUS_LOOP_MODE)
                canvas.drawRect(0,0,mSelectionStart-start,measuredHeight,mSelectedAreaPaint);
            else if(mLoopMode == ONCE_LOOP_MODE)
                canvas.drawRect(0,0,mSelectionStart-start,measuredHeight,mSelectedAreaOncePaint);
            canvas.drawLine(mSelectionStart-start, 0, mSelectionStart-start, measuredHeight, mSelectedLinePaint);
        } else if(mSelectionStart-start>measuredWidth){
            if(mLoopMode == CONTINUOUS_LOOP_MODE)
                canvas.drawRect(0,0,measuredWidth,measuredHeight,mSelectedAreaPaint);
            else if(mLoopMode == ONCE_LOOP_MODE)
                canvas.drawRect(0,0,measuredWidth,measuredHeight,mSelectedAreaOncePaint);
        }

        if((mSelectionEnd-start>=0) && (mSelectionEnd-start<measuredWidth)) {
            if(mLoopMode == CONTINUOUS_LOOP_MODE)
                canvas.drawRect(mSelectionEnd-start,0,measuredWidth,measuredHeight,mSelectedAreaPaint);
            else if(mLoopMode == ONCE_LOOP_MODE){
                canvas.drawRect(mSelectionEnd-start,0,measuredWidth,measuredHeight,mSelectedAreaOncePaint);
            }
            canvas.drawLine(mSelectionEnd - start, 0, mSelectionEnd - start, measuredHeight, mSelectedLinePaint);
        } else if(mSelectionEnd-start<0){
            if(mLoopMode == CONTINUOUS_LOOP_MODE)
                canvas.drawRect(0,0,measuredWidth,measuredHeight,mSelectedAreaPaint);
            else if(mLoopMode == ONCE_LOOP_MODE)
                canvas.drawRect(0,0,measuredWidth,measuredHeight,mSelectedAreaOncePaint);
        }

        // Draw timecode
        double timecodeIntervalSecs = 1.0;
        if (timecodeIntervalSecs / onePixelInSecs < 50) {
            timecodeIntervalSecs = 5.0;
        }
        if (timecodeIntervalSecs / onePixelInSecs < 50) {
            timecodeIntervalSecs = 15.0;
        }

        // Draw grid
        fractionalSecs = mOffset * onePixelInSecs;
        int integerTimecode = (int) (fractionalSecs / timecodeIntervalSecs);
        i = 0;
        while (i < width) {
            i++;
            fractionalSecs += onePixelInSecs;
            integerSecs = (int) fractionalSecs;
            int integerTimecodeNew = (int) (fractionalSecs /
                                            timecodeIntervalSecs);
            if (integerTimecodeNew != integerTimecode) {
                integerTimecode = integerTimecodeNew;

                // Turn, e.g. 67 seconds into "1:07"
                String timecodeMinutes = "" + (integerSecs / 60);
                String timecodeSeconds = "" + (integerSecs % 60);
                if ((integerSecs % 60) < 10) {
                    timecodeSeconds = "0" + timecodeSeconds;
                }
                String timecodeStr = timecodeMinutes + ":" + timecodeSeconds;
                float offset = (float) (
                    0.5 * mTimecodePaint.measureText(timecodeStr));
                canvas.drawText(timecodeStr,
                                i - offset,
                                (int)(12 * mDensity),
                                mTimecodePaint);
            }
        }

        if (mListener != null) {
            mListener.waveformDraw();
        }
    }

    public void setSelectionStart(int startFrame){
        mSelectionStart = startFrame;
    }

    public void setSelectionEnd(int endFrame){
        mSelectionEnd = endFrame;
    }

    public int getNumOfFrames(){
        return mSoundFile.getNumFrames();
    }

    /**
     * Called once when a new sound file is added
     */
    private void computeDoublesForAllZoomLevels() {
        int numFrames = mSoundFile.getNumFrames();
        int[] frameGains = mSoundFile.getFrameGains();
        double[] smoothedGains = new double[numFrames];
        if (numFrames == 1) {
            smoothedGains[0] = frameGains[0];
        } else if (numFrames == 2) {
            smoothedGains[0] = frameGains[0];
            smoothedGains[1] = frameGains[1];
        } else if (numFrames > 2) {
            smoothedGains[0] = (double)(
                (frameGains[0] / 2.0) +
                (frameGains[1] / 2.0));
            for (int i = 1; i < numFrames - 1; i++) {
                smoothedGains[i] = (double)(
                    (frameGains[i - 1] / 3.0) +
                    (frameGains[i    ] / 3.0) +
                    (frameGains[i + 1] / 3.0));
            }
            smoothedGains[numFrames - 1] = (double)(
                (frameGains[numFrames - 2] / 2.0) +
                (frameGains[numFrames - 1] / 2.0));
        }

        // Make sure the range is no more than 0 - 255
        double maxGain = 1.0;
        for (int i = 0; i < numFrames; i++) {
            if (smoothedGains[i] > maxGain) {
                maxGain = smoothedGains[i];
            }
        }
        double scaleFactor = 1.0;
        if (maxGain > 255.0) {
            scaleFactor = 255 / maxGain;
        }        

        // Build histogram of 256 bins and figure out the new scaled max
        maxGain = 0;
        int gainHist[] = new int[256];
        for (int i = 0; i < numFrames; i++) {
            int smoothedGain = (int)(smoothedGains[i] * scaleFactor);
            if (smoothedGain < 0)
                smoothedGain = 0;
            if (smoothedGain > 255)
                smoothedGain = 255;

            if (smoothedGain > maxGain)
                maxGain = smoothedGain;

            gainHist[smoothedGain]++;
        }

        // Re-calibrate the min to be 5%
        double minGain = 0;
        int sum = 0;
        while (minGain < 255 && sum < numFrames / 20) {
            sum += gainHist[(int)minGain];
            minGain++;
        }

        // Re-calibrate the max to be 99%
        sum = 0;
        while (maxGain > 2 && sum < numFrames / 100) {
            sum += gainHist[(int)maxGain];
            maxGain--;
        }

        // Compute the heights
        double[] heights = new double[numFrames];
        double range = maxGain - minGain;
        for (int i = 0; i < numFrames; i++) {
            double value = (smoothedGains[i] * scaleFactor - minGain) / range;
            if (value < 0.0)
                value = 0.0;
            if (value > 1.0)
                value = 1.0;
            heights[i] = value * value;
        }

        mNumZoomLevels = 5;
        mLenByZoomLevel = new int[5];
        mZoomFactorByZoomLevel = new double[5];
        mValuesByZoomLevel = new double[5][];

        // Level 0 is doubled, with interpolated values
        mLenByZoomLevel[0] = numFrames * 2;
        mZoomFactorByZoomLevel[0] = 2.0;
        mValuesByZoomLevel[0] = new double[mLenByZoomLevel[0]];
        if (numFrames > 0) {
            mValuesByZoomLevel[0][0] = 0.5 * heights[0];
            mValuesByZoomLevel[0][1] = heights[0];
        }
        for (int i = 1; i < numFrames; i++) {
            mValuesByZoomLevel[0][2 * i] = 0.5 * (heights[i - 1] + heights[i]);
            mValuesByZoomLevel[0][2 * i + 1] = heights[i];
        }

        // Level 1 is normal
        mLenByZoomLevel[1] = numFrames;
        mValuesByZoomLevel[1] = new double[mLenByZoomLevel[1]];
        mZoomFactorByZoomLevel[1] = 1.0;
        for (int i = 0; i < mLenByZoomLevel[1]; i++) {
            mValuesByZoomLevel[1][i] = heights[i];
        }

        // 3 more levels are each halved
        for (int j = 2; j < 5; j++) {
            mLenByZoomLevel[j] = mLenByZoomLevel[j - 1] / 2;
            mValuesByZoomLevel[j] = new double[mLenByZoomLevel[j]];
            mZoomFactorByZoomLevel[j] = mZoomFactorByZoomLevel[j - 1] / 2.0;
            for (int i = 0; i < mLenByZoomLevel[j]; i++) {
                mValuesByZoomLevel[j][i] =
                    0.5 * (mValuesByZoomLevel[j - 1][2 * i] +
                           mValuesByZoomLevel[j - 1][2 * i + 1]);
            }
        }

        if (numFrames > 5000) {
            mZoomLevel = 3;
        } else if (numFrames > 1000) {
            mZoomLevel = 2;
        } else if (numFrames > 300) {
            mZoomLevel = 1;
        } else {
            mZoomLevel = 0;
        }
        computeIntsForThisZoomLevel();
        mInitialized = true;
    }

    /**
     * Called the first time we need to draw when the zoom level has changed
     * or the screen is resized
     */
    private void computeIntsForThisZoomLevel() {
        int halfHeight = (getMeasuredHeight() / 2) - 1;
        mHeightsAtThisZoomLevel = new int[mLenByZoomLevel[mZoomLevel]];
        for (int i = 0; i < mLenByZoomLevel[mZoomLevel]; i++) {
            mHeightsAtThisZoomLevel[i] =
                (int)(mValuesByZoomLevel[mZoomLevel][i] * halfHeight);
        }
        mCurrentNumOfHeights = mHeightsAtThisZoomLevel.length;
    }

    void setLoopMode(int loopMode){
        mLoopMode = loopMode;
    }
}
