package github.com.kikeEsteban.audioBrowser.app;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.NumberPicker;
import android.widget.SeekBar;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;

import github.com.kikeEsteban.audioBrowser.app.soundfile.CheapSoundFile;


public class PlayerActivity extends ActionBarActivity
implements WaveformView.WaveformListener, NumberPicker.OnValueChangeListener{

    /**
     * Preference names
     */
    public static final String PREF_ERROR_COUNT = "error_count";

    // loading
    private long mLoadingStartTime;
    private long mLoadingLastUpdateTime;
    private boolean mLoadingKeepGoing;
    private ProgressDialog mProgressDialog;
    private Handler mHandler;

    // Waveform listener
    private CheapSoundFile mSoundFile;

    private String mTitle;
    private String mArtist;
    private String mAlbum;
    private int mYear;
    private String mGenre;

    private boolean mTouchDragging;
    private float mTouchStart;
    private int mTouchInitialOffset;
    private long mWaveformTouchStartMsec;
    private int mMaxPos;
    private boolean mIsPlaying;
    private WaveformView mWaveformView;
    private int mPlayStartOffset;
    private int mWidth;
    private int mPlayStartMsec;
    private boolean mCanSeekAccurately;
    private File mFile;
    private String mFilename;
    private float mDensity;
    private String mExtension;

    private int mOffset;
    private int mOffsetGoal;
    private int mFlingVelocity;
    private int mPlayEndMsec;
    private int mStartPos;
    private int mEndPos;

    private TimeScrollView mTimeScrollView;

    // Player
    private static final int UPDATE_FREQUENCY = 500;

  //  private SeekBar seekbar = null;
    private MediaPlayer player = null;
    private ImageButton playButton = null;
    private ImageButton prevButton = null;
    private ImageButton nextButton = null;

    private boolean isStarted = true;
    private String currentFile = "";
    private boolean isMoveingSeekBar = false;

    private Button startLoopTimeButton = null;
    private Button endLoopTimeButton = null;

    private boolean mWaitingForUserGesture = false;
    private int mWaitingTime = 2000;
    final Handler mWaitHandler = new Handler();
    Runnable mEndWaitDelayed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        playButton = (ImageButton)findViewById(R.id.play);
        prevButton = (ImageButton)findViewById(R.id.prev);
        nextButton = (ImageButton)findViewById(R.id.next);
        startLoopTimeButton = (Button)findViewById(R.id.loop_start_time);
        endLoopTimeButton = (Button)findViewById(R.id.loop_end_time);
        mTimeScrollView = (TimeScrollView)findViewById(R.id.timeScroll);

        playButton.setOnClickListener(onButtonClick);
        nextButton.setOnClickListener(onButtonClick);
        prevButton.setOnClickListener(onButtonClick);

        // Start player

        mHandler = new Handler();

        player = new MediaPlayer();
        player.setOnCompletionListener(onCompletion);
        player.setOnErrorListener(onError);

        Bundle b = getIntent().getExtras();
        currentFile = b.getString("songFile");

        mIsPlaying = false;
        mFilename = currentFile;
        mSoundFile = null;

        WaveSurfaceView waveSurfaceView = (WaveSurfaceView)findViewById(R.id.waveform2);

        mWaveformView = (WaveformView)findViewById(R.id.waveform);
        mWaveformView.setWaveRenderer(waveSurfaceView.getRenderer());
        mWaveformView.setListener(this);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mDensity = metrics.density;

        if (mSoundFile != null && !mWaveformView.hasSoundFile()) {
            mWaveformView.setSoundFile(mSoundFile);
            mWaveformView.recomputeHeights(mDensity);
            mMaxPos = mWaveformView.maxPos();
        }

        if (!mFilename.equals("record")) {
            loadFromFile();
        }

        startLoopTimeButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v) {
                show();
            }
        });
        endLoopTimeButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v) {
                show();
            }
        });

        mEndWaitDelayed = new Runnable() {
            @Override
            public void run() {
                mWaitingForUserGesture = false;
            }
        };

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.player, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_save) {
            return true;
        } else if (id == R.id.action_export) {
            return true;
        } else if (id == R.id.action_share) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mWaitHandler.removeCallbacks(mEndWaitDelayed);
        player.stop();
        player.reset();
        player.release();
        player = null;
    }

    private MediaPlayer.OnCompletionListener onCompletion = new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mp) {
            stopPlay();
        }
    };

    private MediaPlayer.OnErrorListener onError = new MediaPlayer.OnErrorListener() {

        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
            // returning false will call the OnCompletionListener
            return false;
        }
    };

    private SeekBar.OnSeekBarChangeListener seekBarChanged = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            isMoveingSeekBar = false;
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            isMoveingSeekBar = true;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress,boolean fromUser) {
            if(isMoveingSeekBar)
            {
                player.seekTo(progress);
                Log.i("OnSeekBarChangeListener", "onProgressChanged");
            }
        }
    };

    private View.OnClickListener onButtonClick = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            switch(v.getId())
            {
                case R.id.play:
                {
                    if(player.isPlaying())
                    {
                        player.pause();
                        playButton.setImageResource(android.R.drawable.ic_media_play);
                    }
                    else
                    {
                        if(isStarted)
                        {
                            player.start();
                            playButton.setImageResource(android.R.drawable.ic_media_pause);
                        }
                        else
                        {
                            startPlay(currentFile);
                        }
                    }

                    break;
                }
                case R.id.next:
                {
                    // Move to loop points
                    /*
                    int seekto = player.getCurrentPosition() + STEP_VALUE;

                    if(seekto > player.getDuration())
                        seekto = player.getDuration();

                    player.pause();
                    player.seekTo(seekto);
                    player.start();
                    */
                    break;
                }
                case R.id.prev:
                {
                    // Move to loop points or to song begining
                    /*
                    int seekto = player.getCurrentPosition() - STEP_VALUE;

                    if(seekto < 0)
                        seekto = 0;

                    player.pause();
                    player.seekTo(seekto);
                    player.start();
                    */

                    break;
                }
            }
        }
    };

    private void startPlay(String file) {
        player.stop();
        player.reset();

        try {
            player.setDataSource(file);
            player.prepare();
            player.start();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        playButton.setImageResource(android.R.drawable.ic_media_pause);
        isStarted = true;
    }

    private void stopPlay() {
        player.stop();
        player.reset();
        playButton.setImageResource(android.R.drawable.ic_media_play);
        isStarted = false;
    }


    // Waveform listener implementation

    private void loadFromFile() {
        mFile = new File(mFilename);
        mExtension = getExtensionFromFilename(mFilename);

        SongMetadataReader metadataReader = new SongMetadataReader(
                this, mFilename);
        mTitle = metadataReader.mTitle;
        mArtist = metadataReader.mArtist;
        mAlbum = metadataReader.mAlbum;
        mYear = metadataReader.mYear;
        mGenre = metadataReader.mGenre;

        String titleLabel = mTitle;
        if (mArtist != null && mArtist.length() > 0) {
            titleLabel += " - " + mArtist;
        }
        setTitle(titleLabel);

        mLoadingStartTime = System.currentTimeMillis();
        mLoadingLastUpdateTime = System.currentTimeMillis();
        mLoadingKeepGoing = true;
        mProgressDialog = new ProgressDialog(PlayerActivity.this);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setTitle(R.string.progress_dialog_loading);
        mProgressDialog.setCancelable(true);

        //mProgressDialog.setOnCancelListener(d);
        mProgressDialog.show();

        final CheapSoundFile.ProgressListener listener =
                new CheapSoundFile.ProgressListener() {
                    public boolean reportProgress(double fractionComplete) {
                        long now = System.currentTimeMillis();
                        if (now - mLoadingLastUpdateTime > 100) {
                            mProgressDialog.setProgress(
                                    (int)(mProgressDialog.getMax() *
                                            fractionComplete));
                            mLoadingLastUpdateTime = now;
                        }
                        return mLoadingKeepGoing;
                    }
                };

        // Create the MediaPlayer in a background thread
        mCanSeekAccurately = false;
        new Thread() {
            public void run() {
                mCanSeekAccurately = SeekTest.CanSeekAccurately(
                        getPreferences(Context.MODE_PRIVATE));

                System.out.println("Seek test done, creating media player.");
                try {
                    player = new MediaPlayer();
                    player.setDataSource(mFile.getAbsolutePath());
                    player.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    player.prepare();
                } catch (final java.io.IOException e) {
                    Runnable runnable = new Runnable() {
                        public void run() {
                            handleFatalError(
                                    "ReadError",
                                    getResources().getText(R.string.read_error),
                                    e);
                        }
                    };
                    mHandler.post(runnable);
                };
            }
        }.start();

        // Load the sound file in a background thread
        new Thread() {
            public void run() {
                try {
                    mSoundFile = CheapSoundFile.create(mFile.getAbsolutePath(),
                            listener);

                    if (mSoundFile == null) {
                        mProgressDialog.dismiss();
                        String name = mFile.getName().toLowerCase();
                        String[] components = name.split("\\.");
                        String err;
                        if (components.length < 2) {
                            err = getResources().getString(
                                    R.string.no_extension_error);
                        } else {
                            err = getResources().getString(
                                    R.string.bad_extension_error) + " " +
                                    components[components.length - 1];
                        }
                        final String finalErr = err;
                        Runnable runnable = new Runnable() {
                            public void run() {
                                handleFatalError(
                                        "UnsupportedExtension",
                                        finalErr,
                                        new Exception());
                            }
                        };
                        mHandler.post(runnable);
                        return;
                    }
                } catch (final Exception e) {
                    mProgressDialog.dismiss();
                    e.printStackTrace();
                    Runnable runnable = new Runnable() {
                        public void run() {
                            handleFatalError(
                                    "ReadError",
                                    getResources().getText(R.string.read_error),
                                    e);
                        }
                    };
                    mHandler.post(runnable);
                    return;
                }
                mProgressDialog.dismiss();
                if (mLoadingKeepGoing) {
                    Runnable runnable = new Runnable() {
                        public void run() {
                            finishOpeningSoundFile();
                        }
                    };
                    mHandler.post(runnable);
                } else {
                    PlayerActivity.this.finish();
                }
            }
        }.start();
    }

    private void handleFatalError(
            final CharSequence errorInternalName,
            final CharSequence errorString,
            final Exception exception) {
        Log.i("SAL", "handleFatalError");

        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        int failureCount = prefs.getInt(PREF_ERROR_COUNT, 0);
        final SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putInt(PREF_ERROR_COUNT, failureCount + 1);
        prefsEditor.commit();

        showFinalAlert(exception, errorString);

    }

    /**
     * Show a "final" alert dialog that will exit the activity
     * after the user clicks on the OK button.  If an exception
     * is passed, it's assumed to be an error condition, and the
     * dialog is presented as an error, and the stack trace is
     * logged.  If there's no exception, it's a success message.
     */
    private void showFinalAlert(Exception e, CharSequence message) {
        CharSequence title;
        if (e != null) {
            Log.e("PlayerActivity", "Error: " + message);
            Log.e("PlayerActivity", getStackTrace(e));
            title = getResources().getText(R.string.alert_title_failure);
            setResult(RESULT_CANCELED, new Intent());
        } else {
            Log.i("SAL", "Success: " + message);
            title = getResources().getText(R.string.alert_title_success);
        }

        new AlertDialog.Builder(PlayerActivity.this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(
                        R.string.alert_ok_button,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int whichButton) {
                                finish();
                            }
                        })
                .setCancelable(false)
                .show();
    }

    private String getStackTrace(Exception e) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(stream, true);
        e.printStackTrace(writer);
        return stream.toString();
    }

    private String getExtensionFromFilename(String filename) {
        return filename.substring(filename.lastIndexOf('.'),
                filename.length());
    }

    private void finishOpeningSoundFile() {
        mWaveformView.setSoundFile(mSoundFile);
        mWaveformView.recomputeHeights(mDensity);
        mMaxPos = mWaveformView.maxPos();
        mTouchDragging = false;
        setmOffset(0);
        mOffsetGoal = 0;
        setmFlingVelocity(0);
        resetPositions();
        if (mEndPos > mMaxPos)
            mEndPos = mMaxPos;
        updateDisplay();
    }

    private synchronized void handlePause() {
        if (player != null && player.isPlaying()) {
            player.pause();
        }
        mWaveformView.setPlayback(-1);
        mIsPlaying = false;
        // enableDisableButtons();
    }

    private void resetPositions() {
        mStartPos = mWaveformView.secondsToPixels(0.0);
        mEndPos = mWaveformView.secondsToPixels(15.0);
    }

    public void waveformTouchStart(float x) {
        mTouchDragging = true;
        mTouchStart = x;
        mTouchInitialOffset = getmOffset();
        setmFlingVelocity(0);
        mWaveformTouchStartMsec = System.currentTimeMillis();
    }

    public void waveformTouchMove(float x) {
         setmOffset(trap((int)(mTouchInitialOffset + (mTouchStart - x))));
         updateDisplay();
    }

    private int trap(int pos) {
        if (pos < 0)
            return 0;
        if (pos + mWidth > mMaxPos)
            pos = mMaxPos - mWidth;
        return pos;
    }

    public void waveformTouchEnd() {
        mTouchDragging = false;
        mOffsetGoal = getmOffset();
        long elapsedMsec = System.currentTimeMillis() -
                mWaveformTouchStartMsec;
        if (elapsedMsec < 300) {
            if (mIsPlaying) {
                int seekMsec = mWaveformView.pixelsToMillisecs(
                        (int)(mTouchStart + getmOffset()));
                if (seekMsec >= mPlayStartMsec &&
                        seekMsec < mPlayEndMsec) {
                    player.seekTo(seekMsec - mPlayStartOffset);
                } else {
                    handlePause();
                }
            } else {
                onPlay((int)(mTouchStart + getmOffset()));
            }
        }
    }

    private synchronized void onPlay(int startPosition) {
        if (mIsPlaying) {
            handlePause();
            return;
        }

        if (player == null) {
            // Not initialized yet
            return;
        }

        try {
            mPlayStartMsec = mWaveformView.pixelsToMillisecs(startPosition);
            if (startPosition < mStartPos) {
                mPlayEndMsec = mWaveformView.pixelsToMillisecs(mStartPos);
            } else if (startPosition > mEndPos) {
                mPlayEndMsec = mWaveformView.pixelsToMillisecs(mMaxPos);
            } else {
                mPlayEndMsec = mWaveformView.pixelsToMillisecs(mEndPos);
            }

            mPlayStartOffset = 0;

            int startFrame = mWaveformView.secondsToFrames(
                    mPlayStartMsec * 0.001);
            int endFrame = mWaveformView.secondsToFrames(
                    mPlayEndMsec * 0.001);
            int startByte = mSoundFile.getSeekableFrameOffset(startFrame);
            int endByte = mSoundFile.getSeekableFrameOffset(endFrame);
            if (mCanSeekAccurately && startByte >= 0 && endByte >= 0) {
                try {
                    player.reset();
                    player.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    FileInputStream subsetInputStream = new FileInputStream(
                            mFile.getAbsolutePath());
                    player.setDataSource(subsetInputStream.getFD(),
                            startByte, endByte - startByte);
                    player.prepare();
                    mPlayStartOffset = mPlayStartMsec;
                } catch (Exception e) {
                    System.out.println("Exception trying to play file subset");
                    player.reset();
                    player.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    player.setDataSource(mFile.getAbsolutePath());
                    player.prepare();
                    mPlayStartOffset = 0;
                }
            }

            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public synchronized void onCompletion(MediaPlayer arg0) {
                    handlePause();
                }
            });
            mIsPlaying = true;

            if (mPlayStartOffset == 0) {
                player.seekTo(mPlayStartMsec);
            }
            player.start();
            updateDisplay();
            //enableDisableButtons();
        } catch (Exception e) {
            //showFinalAlert(e, R.string.play_error);
            return;
        }
    }

    public void waveformFling(float vx) {
        mTouchDragging = false;
        mOffsetGoal = getmOffset();
        setmFlingVelocity((int)(-vx));
        updateDisplay();
    }

    public void waveformZoomIn() {
        mWaveformView.zoomIn();
        mStartPos = mWaveformView.getStart();
        mEndPos = mWaveformView.getEnd();
        mMaxPos = mWaveformView.maxPos();
        setmOffset(mWaveformView.getOffset());
        mOffsetGoal = getmOffset();
        //enableZoomButtons();
        updateDisplay();
    }

    public void waveformZoomOut() {
        mWaveformView.zoomOut();
        mStartPos = mWaveformView.getStart();
        mEndPos = mWaveformView.getEnd();
        mMaxPos = mWaveformView.maxPos();
        setmOffset(mWaveformView.getOffset());
        mOffsetGoal = getmOffset();
        //enableZoomButtons();
        updateDisplay();
    }

    /**
     * Every time we get a message that our waveform drew, see if we need to
     * animate and trigger another redraw.
     */
    public void waveformDraw() {
        mWidth = mWaveformView.getMeasuredWidth();
        System.out.println(mWidth);
        if (mOffsetGoal != getmOffset())
            updateDisplay();
        else if (mIsPlaying) {
            updateDisplay();
        } else if (getmFlingVelocity() != 0) {
            updateDisplay();
        }
    }

    private synchronized void updateDisplay() {
        if (mIsPlaying) {
            int now = player.getCurrentPosition() + mPlayStartOffset;
            int frames = mWaveformView.millisecsToPixels(now);
            mWaveformView.setPlayback(frames);
            setOffsetGoalNoUpdate(frames - mWidth / 2);
            if (now >= mPlayEndMsec) {
                handlePause();
            }
        }

        if (!mTouchDragging) {
            int offsetDelta;

            if (getmFlingVelocity() != 0) {
                float saveVel = getmFlingVelocity();

                offsetDelta = getmFlingVelocity() / 30;
                float threshold = 80;
                if (getmFlingVelocity() > threshold) {
                    setmFlingVelocity((int)(getmFlingVelocity() - threshold));
                } else if (getmFlingVelocity() < -threshold) {
                    setmFlingVelocity((int)(getmFlingVelocity() + threshold));
                } else {
                    setmFlingVelocity(0);
                }

                setmOffset(getmOffset() + offsetDelta);

                if (getmOffset() + mWidth > mMaxPos) {
                    setmOffset(mMaxPos - mWidth);
                    setmFlingVelocity(0);
                }
                if (getmOffset() < 0) {
                    setmOffset(0);
                    setmFlingVelocity(0);
                }
                mOffsetGoal = getmOffset();
            } else {
                if(!mWaitingForUserGesture){  // When fling reach 0, we wait for new user gestures before returning to playback
                    offsetDelta = mOffsetGoal - getmOffset();

                    if (offsetDelta > 10)
                        offsetDelta = offsetDelta / 10;
                    else if (offsetDelta > 0)
                        offsetDelta = 1;
                    else if (offsetDelta < -10)
                        offsetDelta = offsetDelta / 10;
                    else if (offsetDelta < 0)
                        offsetDelta = -1;
                    else
                        offsetDelta = 0;

                    setmOffset(getmOffset() + offsetDelta);
                }
            }
        }

        mWaveformView.setParameters(mStartPos, mEndPos, getmOffset());
        mWaveformView.invalidate();

        // Update TimeScroll view data
        float relativeOffset = (float) getmOffset() /mWaveformView.getNumOfHeightAtThisZoomLevel();
        float relativeWidth = (float)mWaveformView.getMeasuredWidth()/mWaveformView.getNumOfHeightAtThisZoomLevel();
        mTimeScrollView.setData(relativeOffset,relativeWidth);
        mTimeScrollView.invalidate();
    }

    private void setOffsetGoalNoUpdate(int offset) {
        if (mTouchDragging) {
            return;
        }

        mOffsetGoal = offset;
        if (mOffsetGoal + mWidth > mMaxPos)
            mOffsetGoal = mMaxPos - mWidth;
        if (mOffsetGoal < 0)
            mOffsetGoal = 0;
    }

    public int getmOffset() {
        return mOffset;
    }

    public void setmOffset(int mOffset) {
        this.mOffset = mOffset;
    }

    public int getmFlingVelocity() {
        return mFlingVelocity;
    }

    public void setmFlingVelocity(int mFlingVelocity) {
        this.mFlingVelocity = mFlingVelocity;
        if(mFlingVelocity == 0){
            mWaitingForUserGesture = true;
            mWaitHandler.removeCallbacks(mEndWaitDelayed);
            mWaitHandler.postDelayed(mEndWaitDelayed, mWaitingTime);
        }
    }

    @Override
    public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
        Log.i("value is",""+newVal);
    }

    public void show()
    {
        final Dialog d = new Dialog(PlayerActivity.this);
        d.setTitle("NumberPicker");
        d.setContentView(R.layout.time_picker_dialog);
        Button b1 = (Button) d.findViewById(R.id.button1);
        Button b2 = (Button) d.findViewById(R.id.button2);
        final NumberPicker np1 = (NumberPicker) d.findViewById(R.id.numberPicker1);
        final NumberPicker np2 = (NumberPicker) d.findViewById(R.id.numberPicker2);
        final NumberPicker np3 = (NumberPicker) d.findViewById(R.id.numberPicker3);
        np1.setMaxValue(500); // max value 100
        np1.setMinValue(0);   // min value 0
        np1.setWrapSelectorWheel(true);
        np1.setOnValueChangedListener(this);
        np2.setMaxValue(60); // max value 100
        np2.setMinValue(0);   // min value 0
        np2.setWrapSelectorWheel(true);
        np2.setOnValueChangedListener(this);
        np3.setMaxValue(60); // max value 100
        np3.setMinValue(0);   // min value 0
        np3.setWrapSelectorWheel(true);
        np3.setOnValueChangedListener(this);

        b1.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v) {
             //   tv.setText(String.valueOf(np.getValue())); //set the value to textview
                d.dismiss();
            }
        });
        b2.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v) {
                d.dismiss(); // dismiss the dialog
            }
        });
        d.show();
    }




}
