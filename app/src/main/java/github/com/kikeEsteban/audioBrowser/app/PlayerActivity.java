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
import android.widget.TextView;


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
    public static final String PREF_SUCCESS_COUNT = "success_count";
    public static final String PREF_STATS_SERVER_CHECK =
            "stats_server_check";
    public static final String PREF_STATS_SERVER_ALLOWED =
            "stats_server_allowed";
    public static final String PREF_ERROR_COUNT = "error_count";
    public static final String PREF_ERR_SERVER_CHECK =
            "err_server_check";
    public static final String PREF_ERR_SERVER_ALLOWED =
            "err_server_allowed";
    public static final String PREF_UNIQUE_ID = "unique_id";


    // loading
    private long mLoadingStartTime;
    private long mLoadingLastUpdateTime;
    private boolean mLoadingKeepGoing;
    private ProgressDialog mProgressDialog;
    private Handler mHandler;

    // Waveform listener
    private CheapSoundFile mSoundFile;

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
    private boolean mKeyDown;
    private float mDensity;
    private String mExtension;
    private int mLastDisplayedStartPos;
    private int mLastDisplayedEndPos;


    private int mOffset;
    private int mOffsetGoal;
    private int mFlingVelocity;
    private int mPlayEndMsec;
    private int mStartPos;
    private int mEndPos;


    private TimeScrollView mTimeScrollView;


    // Player

    private static final int UPDATE_FREQUENCY = 500;
    private static final int STEP_VALUE = 4000;
    private static final float VISUALIZER_HEIGHT_DIP = 50f;

    private TextView selectedFile = null;
  //  private SeekBar seekbar = null;
    private MediaPlayer player = null;
    private ImageButton playButton = null;
    private ImageButton prevButton = null;
    private ImageButton nextButton = null;

    private boolean isStarted = true;
    private String currentFile = "";
    private boolean isMoveingSeekBar = false;

    private final Handler handler = new Handler();

    private final Runnable updatePositionRunnable = new Runnable() {
        public void run() {
            updatePosition();
        }
    };

    private Button startLoopTimeButton = null;
    private Button endLoopTimeButton = null;

    // Visualization
    // Vista de visualización
    //private LinearLayout mLinearLayout;
    // private VisualizerView mVisualizerView;
    // private Visualizer mVisualizer;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        // TODO: Add custom view zoom seek bar
        //seekbar = (SeekBar)findViewById(R.id.seekbar);
        playButton = (ImageButton)findViewById(R.id.play);
        prevButton = (ImageButton)findViewById(R.id.prev);
        nextButton = (ImageButton)findViewById(R.id.next);
        selectedFile = (TextView) findViewById(R.id.selectedfile);
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
       // startPlay(currentFile);

        // TODO: Add custom view zoom seek bar
        //seekbar.setOnSeekBarChangeListener(seekBarChanged);

        mIsPlaying = false;
        mFilename = currentFile;
        mSoundFile = null;
        mKeyDown = false;


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

        mHandler.postDelayed(mTimerRunnable, 100);

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


    }

    private Runnable mTimerRunnable = new Runnable() {
        public void run() {
            // Updating an EditText is slow on Android.  Make sure
            // we only do the update if the text has actually changed.
            if (mStartPos != mLastDisplayedStartPos ){
          //          && !mStartText.hasFocus()) {
          //      mStartText.setText(formatTime(mStartPos));
                mLastDisplayedStartPos = mStartPos;
            }

            if (mEndPos != mLastDisplayedEndPos ){
          //       &&   !mEndText.hasFocus()) {
          //      mEndText.setText(formatTime(mEndPos));
                mLastDisplayedEndPos = mEndPos;
            }

            mHandler.postDelayed(mTimerRunnable, 100);
        }
    };

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

        handler.removeCallbacks(updatePositionRunnable);
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
                        handler.removeCallbacks(updatePositionRunnable);
                        player.pause();
                        playButton.setImageResource(android.R.drawable.ic_media_play);
                    }
                    else
                    {
                        if(isStarted)
                        {
                            player.start();
                            playButton.setImageResource(android.R.drawable.ic_media_pause);
                            // jump to visualizer layout and launch audio
                            updatePosition();
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
                    int seekto = player.getCurrentPosition() + STEP_VALUE;

                    if(seekto > player.getDuration())
                        seekto = player.getDuration();

                    player.pause();
                    player.seekTo(seekto);
                    player.start();

                    break;
                }
                case R.id.prev:
                {
                    int seekto = player.getCurrentPosition() - STEP_VALUE;

                    if(seekto < 0)
                        seekto = 0;

                    player.pause();
                    player.seekTo(seekto);
                    player.start();

                    break;
                }
            }
        }
    };

    private void startPlay(String file) {
        Log.i("Selected: ", file);

        selectedFile.setText(file);
        // TODO: Add custom view zoom seek bar
        //seekbar.setProgress(0);

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

        // TODO: Add custom view zoom seek bar
        //seekbar.setMax(player.getDuration());
        playButton.setImageResource(android.R.drawable.ic_media_pause);

        updatePosition();

        isStarted = true;
    }

    private void stopPlay() {
        player.stop();
        player.reset();
        playButton.setImageResource(android.R.drawable.ic_media_play);
        handler.removeCallbacks(updatePositionRunnable);
        // TODO: Add custom view zoom seek bar
        // seekbar.setProgress(0);

        isStarted = false;
    }

    private void updatePosition(){
        handler.removeCallbacks(updatePositionRunnable);

        // TODO: Add custom view zoom seek bar
        //seekbar.setProgress(player.getCurrentPosition());

        handler.postDelayed(updatePositionRunnable, UPDATE_FREQUENCY);
    }


    // Waveform listener implementation

    private void loadFromFile() {
        mFile = new File(mFilename);
        mExtension = getExtensionFromFilename(mFilename);

        /*SongMetadataReader metadataReader = new SongMetadataReader(
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
*/
        mLoadingStartTime = System.currentTimeMillis();
        mLoadingLastUpdateTime = System.currentTimeMillis();
        mLoadingKeepGoing = true;
        mProgressDialog = new ProgressDialog(PlayerActivity.this);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setTitle(R.string.progress_dialog_loading);
        mProgressDialog.setCancelable(true);
        DialogInterface d = new DialogInterface() {
            @Override
            public void cancel() {
                mLoadingKeepGoing = false;
            }

            @Override
            public void dismiss() {

            }
        };
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
                    //mInfo.setText(e.toString());

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
        Log.i("Ringdroid", "handleFatalError");

        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        int failureCount = prefs.getInt(PREF_ERROR_COUNT, 0);
        final SharedPreferences.Editor prefsEditor = prefs.edit();
        prefsEditor.putInt(PREF_ERROR_COUNT, failureCount + 1);
        prefsEditor.commit();

        // Just show a simple "write error" message
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
            Log.i("Ringdroid", "Success: " + message);
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
        mLastDisplayedStartPos = -1;
        mLastDisplayedEndPos = -1;

        mTouchDragging = false;

        setmOffset(0);
        mOffsetGoal = 0;
        mFlingVelocity = 0;
        resetPositions();
        if (mEndPos > mMaxPos)
            mEndPos = mMaxPos;

        /*
        mCaption =
                mSoundFile.getFiletype() + ", " +
                        mSoundFile.getSampleRate() + " Hz, " +
                        mSoundFile.getAvgBitrateKbps() + " kbps, " +
                        formatTime(mMaxPos) + " " +
                        getResources().getString(R.string.time_seconds);
        mInfo.setText(mCaption);
*/

        updateDisplay();
    }

    private void resetPositions() {
        mStartPos = mWaveformView.secondsToPixels(0.0);
        mEndPos = mWaveformView.secondsToPixels(15.0);
    }

    public void waveformTouchStart(float x) {
        mTouchDragging = true;
        mTouchStart = x;
        mTouchInitialOffset = getmOffset();
        mFlingVelocity = 0;
        mWaveformTouchStartMsec = System.currentTimeMillis();
    }

    public void waveformTouchMove(float x) {

         setmOffset(trap((int)(mTouchInitialOffset + (mTouchStart - x))));
         updateDisplay();

    }

    private int trap(int pos) {
        if (pos < 0)
            return 0;
        if (pos > mMaxPos)
            return mMaxPos;
        return pos;
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

            if (mFlingVelocity != 0) {
                float saveVel = mFlingVelocity;

                offsetDelta = mFlingVelocity / 30;
                float threshold = 80;
                if (mFlingVelocity > threshold) {
                    mFlingVelocity -= threshold;
                } else if (mFlingVelocity < -threshold) {
                    mFlingVelocity += threshold;
                } else {
                    mFlingVelocity = 0;
                }

                setmOffset(getmOffset() + offsetDelta);

                if (getmOffset() + mWidth / 2 > mMaxPos) {
                    setmOffset(mMaxPos - mWidth / 2);
                    mFlingVelocity = 0;
                }
                if (getmOffset() < 0) {
                    setmOffset(0);
                    mFlingVelocity = 0;
                }
                mOffsetGoal = getmOffset();
            } else {
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

        mWaveformView.setParameters(mStartPos, mEndPos, getmOffset());
        mWaveformView.invalidate();

        // Update TimeScroll view data
        float relativeOffset = (float) getmOffset() /mWaveformView.getNumOfHeightAtThisZoomLevel();
        float relativeWidth = (float)mWaveformView.getMeasuredWidth()/mWaveformView.getNumOfHeightAtThisZoomLevel();
        mTimeScrollView.setData(relativeOffset,relativeWidth);
        mTimeScrollView.invalidate();

        /*
        mStartMarker.setContentDescription(
                getResources().getText(R.string.start_marker) + " " +
                        formatTime(mStartPos));
        mEndMarker.setContentDescription(
                getResources().getText(R.string.end_marker) + " " +
                        formatTime(mEndPos));

        int startX = mStartPos - mOffset - mMarkerLeftInset;
        if (startX + mStartMarker.getWidth() >= 0) {
            if (!mStartVisible) {
                // Delay this to avoid flicker
                mHandler.postDelayed(new Runnable() {
                    public void run() {
                        mStartVisible = true;
                        mStartMarker.setAlpha(255);
                    }
                }, 0);
            }
        } else {
            if (mStartVisible) {
                mStartMarker.setAlpha(0);
                mStartVisible = false;
            }
            startX = 0;
        }

        int endX = mEndPos - mOffset - mEndMarker.getWidth() +
                mMarkerRightInset;
        if (endX + mEndMarker.getWidth() >= 0) {
            if (!mEndVisible) {
                // Delay this to avoid flicker
                mHandler.postDelayed(new Runnable() {
                    public void run() {
                        mEndVisible = true;
                        mEndMarker.setAlpha(255);
                    }
                }, 0);
            }
        } else {
            if (mEndVisible) {
                mEndMarker.setAlpha(0);
                mEndVisible = false;
            }
            endX = 0;
        }

        mStartMarker.setLayoutParams(
                new AbsoluteLayout.LayoutParams(
                        AbsoluteLayout.LayoutParams.WRAP_CONTENT,
                        AbsoluteLayout.LayoutParams.WRAP_CONTENT,
                        startX,
                        mMarkerTopOffset));

        mEndMarker.setLayoutParams(
                new AbsoluteLayout.LayoutParams(
                        AbsoluteLayout.LayoutParams.WRAP_CONTENT,
                        AbsoluteLayout.LayoutParams.WRAP_CONTENT,
                        endX,
                        mWaveformView.getMeasuredHeight() -
                                mEndMarker.getHeight() - mMarkerBottomOffset));
        */
    }

    private void setOffsetGoalNoUpdate(int offset) {
        if (mTouchDragging) {
            return;
        }

        mOffsetGoal = offset;
        if (mOffsetGoal + mWidth / 2 > mMaxPos)
            mOffsetGoal = mMaxPos - mWidth / 2;
        if (mOffsetGoal < 0)
            mOffsetGoal = 0;
    }

    private synchronized void handlePause() {
        if (player != null && player.isPlaying()) {
            player.pause();
        }
        mWaveformView.setPlayback(-1);
        mIsPlaying = false;
       // enableDisableButtons();
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
        mFlingVelocity = (int)(-vx);
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
        if (mOffsetGoal != getmOffset() && !mKeyDown)
            updateDisplay();
        else if (mIsPlaying) {
            updateDisplay();
        } else if (mFlingVelocity != 0) {
            updateDisplay();
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
        final NumberPicker np2 = (NumberPicker) d.findViewById(R.id.numberPicker1);
        final NumberPicker np3 = (NumberPicker) d.findViewById(R.id.numberPicker1);
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

    public int getmOffset() {
        return mOffset;
    }

    public void setmOffset(int mOffset) {
        this.mOffset = mOffset;
    }



    /*
    private void enableDisableButtons() {
        if (mIsPlaying) {
            mPlayButton.setImageResource(android.R.drawable.ic_media_pause);
            mPlayButton.setContentDescription(getResources().getText(R.string.stop));
        } else {
            mPlayButton.setImageResource(android.R.drawable.ic_media_play);
            mPlayButton.setContentDescription(getResources().getText(R.string.play));
        }
    }
    */



    /**
     * A simple class that draws waveform data received from a
     * Visualizer.OnDataCaptureListener#onWaveFormDataCapture
     */

    /*
    class VisualizerView extends View {
        private byte[] mBytes;
        private float[] mPoints;
        private Rect mRect = new Rect();
        private Paint mForePaint = new Paint();
        public VisualizerView(Context context) {
            super(context);
            init();
        }
        private void init() {
            mBytes = null;
            mForePaint.setStrokeWidth(1f);
            mForePaint.setAntiAlias(true);
            mForePaint.setColor(Color.rgb(0, 128, 255));
        }
        public void updateVisualizer(byte[] bytes) {
            mBytes = bytes;
            invalidate();
        }
        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (mBytes == null) {
                return;
            }
            if (mPoints == null || mPoints.length < mBytes.length * 4) {
                mPoints = new float[mBytes.length * 4];
            }
            mRect.set(0, 0, getWidth(), getHeight());
            for (int i = 0; i < mBytes.length - 1; i++) {
                mPoints[i * 4] = mRect.width() * i / (mBytes.length - 1);
                mPoints[i * 4 + 1] = mRect.height() / 2
                        + ((byte) (mBytes[i] + 128)) * (mRect.height() / 2) / 128;
                mPoints[i * 4 + 2] = mRect.width() * (i + 1) / (mBytes.length - 1);
                mPoints[i * 4 + 3] = mRect.height() / 2
                        + ((byte) (mBytes[i + 1] + 128)) * (mRect.height() / 2) / 128;
            }
            canvas.drawLines(mPoints, mForePaint);
        }
    }*/

    /*
    private void setupVisualizerFxAndUI() {
    // Create a VisualizerView (defined below), which will render the simplified audio
    // wave form to a Canvas.
        try {
            mLinearLayout = new LinearLayout(this);
            mLinearLayout.setOrientation(LinearLayout.VERTICAL);

            mVisualizerView = new VisualizerView(this);
            mVisualizerView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    (int)(VISUALIZER_HEIGHT_DIP * getResources().getDisplayMetrics().density)));
            mLinearLayout.addView(mVisualizerView);
            // Create the Visualizer object and attach it to our media player.
            System.out.println(player.getAudioSessionId());
            mVisualizer = new Visualizer(player.getAudioSessionId());
            mVisualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
            mVisualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {

                public void onWaveFormDataCapture(Visualizer visualizer, byte[] bytes,
                                                  int samplingRate) {
                    mVisualizerView.updateVisualizer(bytes);
                }

                public void onFftDataCapture(Visualizer visualizer, byte[] bytes, int samplingRate) {


                }
            }, Visualizer.getMaxCaptureRate() / 2, true, false);
            //setContentView(mLinearLayout);
        } catch(Exception e){
            e.printStackTrace();
        }

    }*/



}
