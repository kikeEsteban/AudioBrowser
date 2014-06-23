package github.com.kikeEsteban.audioBrowser.app;

import android.media.MediaPlayer;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.IOException;


public class PlayerActivity extends ActionBarActivity {

    private static final int UPDATE_FREQUENCY = 500;
    private static final int STEP_VALUE = 4000;
    private static final float VISUALIZER_HEIGHT_DIP = 50f;

    private TextView selectedFile = null;
    private SeekBar seekbar = null;
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

    // Visualization
    // Vista de visualización
    //private LinearLayout mLinearLayout;
    // private VisualizerView mVisualizerView;
    // private Visualizer mVisualizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        seekbar = (SeekBar)findViewById(R.id.seekbar);
        playButton = (ImageButton)findViewById(R.id.play);
        prevButton = (ImageButton)findViewById(R.id.prev);
        nextButton = (ImageButton)findViewById(R.id.next);
        selectedFile = (TextView) findViewById(R.id.selectedfile);

        playButton.setOnClickListener(onButtonClick);
        nextButton.setOnClickListener(onButtonClick);
        prevButton.setOnClickListener(onButtonClick);

        player = new MediaPlayer();
        player.setOnCompletionListener(onCompletion);
        player.setOnErrorListener(onError);

        Bundle b = getIntent().getExtras();
        currentFile = b.getString("songFile");
        startPlay(currentFile);

        seekbar.setOnSeekBarChangeListener(seekBarChanged);
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
        if (id == R.id.action_settings) {
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
        seekbar.setProgress(0);

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

        seekbar.setMax(player.getDuration());
        playButton.setImageResource(android.R.drawable.ic_media_pause);

        updatePosition();

        isStarted = true;
    }

    private void stopPlay() {
        player.stop();
        player.reset();
        playButton.setImageResource(android.R.drawable.ic_media_play);
        handler.removeCallbacks(updatePositionRunnable);
        seekbar.setProgress(0);

        isStarted = false;
    }

    private void updatePosition(){
        handler.removeCallbacks(updatePositionRunnable);

        seekbar.setProgress(player.getCurrentPosition());

        handler.postDelayed(updatePositionRunnable, UPDATE_FREQUENCY);
    }


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
