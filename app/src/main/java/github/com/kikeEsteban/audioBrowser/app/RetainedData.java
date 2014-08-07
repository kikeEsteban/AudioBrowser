package github.com.kikeEsteban.audioBrowser.app;

import android.media.MediaPlayer;

import github.com.kikeEsteban.audioBrowser.app.soundfile.CheapSoundFile;

/**
 * Created by kike on 7/08/14.
 */
public class RetainedData {

    private CheapSoundFile mSoundFile;
    private String mTitle;
    private String mArtist;
    private String mFilename;

    private int mOffset;
    private int mStartPos;
    private int mEndPos;
    private int mZoomLevel;
    private boolean mIsPlaying;
    private int mLoopMode;
    private boolean mPlayerInitialized;

    MediaPlayer mPlayer;

    RetainedData(MediaPlayer player, CheapSoundFile soundFile, String fileName, String title, String artist,
                 int offset, int startPos, int endPos, int zoomLevel, int loopMode, boolean isPlaying, boolean playerInitialized){
        mSoundFile = soundFile;
        mFilename = fileName;
        mTitle = title;
        mArtist = artist;
        mOffset = offset;
        mStartPos = startPos;
        mEndPos = endPos;
        mZoomLevel = zoomLevel;
        mPlayer = player;
        mIsPlaying = isPlaying;
        mLoopMode = loopMode;
        mPlayerInitialized = playerInitialized;
    }

    public MediaPlayer getMediaPlayer(){
        return mPlayer;
    }

    public boolean getIsPlaying(){
        return mIsPlaying;
    }

    public CheapSoundFile getSoundFile(){
        return mSoundFile;
    }

    public String getTitle(){
        return mTitle;
    }

    public String getArtist(){
        return mArtist;
    }

    public String getFileName(){
        return mFilename;
    }

    public int getOffset(){
        return mOffset;
    }

    public int getStartPos(){
        return mStartPos;
    }

    public int getEndPos() {
        return mEndPos;
    }

    public int getZoomLevel(){
        return mZoomLevel;
    }

    public int getLoopMode(){
        return mLoopMode;
    }

    public boolean getPlayerInitialized(){
        return mPlayerInitialized;
    }
}
