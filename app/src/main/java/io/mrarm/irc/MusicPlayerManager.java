package io.mrarm.irc;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.Log;
import java.io.IOException;

public class MusicPlayerManager {
    private static final String TAG = "MusicPlayerManager";
    private static final String STREAM_URL = "http://icecast.thaiirc.com:8000/ices";

    private static MusicPlayerManager sInstance;
    private MediaPlayer mMediaPlayer;
    private boolean mIsPlaying = false;
    private boolean mIsPreparing = false;
    private PlayStateListener mListener;

    public interface PlayStateListener {
        void onPlayStateChanged(boolean isPlaying, boolean isPreparing);
        void onPlayerError(String message);
    }

    private MusicPlayerManager() {}

    public static synchronized MusicPlayerManager getInstance() {
        if (sInstance == null) {
            sInstance = new MusicPlayerManager();
        }
        return sInstance;
    }

    public void setListener(PlayStateListener listener) {
        mListener = listener;
        if (mListener != null) {
            mListener.onPlayStateChanged(mIsPlaying, mIsPreparing);
        }
    }

    public boolean isPlaying() {
        return mIsPlaying;
    }

    public boolean isPreparing() {
        return mIsPreparing;
    }

    public void togglePlay() {
        if (mIsPlaying || mIsPreparing) {
            stop();
        } else {
            start();
        }
    }

    public void start() {
        if (mIsPlaying || mIsPreparing) return;
        mIsPreparing = true;
        if (mListener != null) {
            mListener.onPlayStateChanged(false, true);
        }
        try {
            if (mMediaPlayer != null) {
                try {
                    mMediaPlayer.release();
                } catch (Exception ignored) {}
            }
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setAudioAttributes(
                new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            );
            mMediaPlayer.setDataSource(STREAM_URL);
            mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mIsPreparing = false;
                    mIsPlaying = true;
                    if (mMediaPlayer != null) {
                        try {
                            mMediaPlayer.start();
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to start MediaPlayer", e);
                            stop();
                            return;
                        }
                    }
                    if (mListener != null) {
                        mListener.onPlayStateChanged(true, false);
                    }
                }
            });
            mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.e(TAG, "MediaPlayer error: what=" + what + ", extra=" + extra);
                    stop();
                    if (mListener != null) {
                        mListener.onPlayerError("Connection error. Please try again.");
                    }
                    return true;
                }
            });
            mMediaPlayer.prepareAsync();
        } catch (IOException e) {
            Log.e(TAG, "Error setting up MediaPlayer data source", e);
            stop();
            if (mListener != null) {
                mListener.onPlayerError("Failed to initialize player.");
            }
        }
    }

    public void stop() {
        mIsPlaying = false;
        mIsPreparing = false;
        if (mMediaPlayer != null) {
            try {
                mMediaPlayer.stop();
            } catch (Exception ignored) {}
            try {
                mMediaPlayer.release();
            } catch (Exception ignored) {}
            mMediaPlayer = null;
        }
        if (mListener != null) {
            mListener.onPlayStateChanged(false, false);
        }
    }
}
