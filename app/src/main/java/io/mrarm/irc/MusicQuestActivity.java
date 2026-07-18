package io.mrarm.irc;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MusicQuestActivity extends ThemedActivity implements MusicPlayerManager.PlayStateListener {

    private TextView mNowPlayingText;
    private TextView mBottomNowPlayingText;
    private Button mPlayToggleButton;
    private TextView mPlayerStatusText;

    private TextView mStreamNameText;
    private TextView mStreamDescriptionText;
    private TextView mStreamContentTypeText;
    private TextView mStreamStartedText;
    private TextView mStreamBitrateText;
    private TextView mStreamListenersText;
    private TextView mStreamGenreText;

    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable mRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            fetchMetadata();
            mHandler.postDelayed(this, 10000); // refresh every 10 seconds
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_quest);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Music Quest");
        }

        mNowPlayingText = findViewById(R.id.tvNowPlay);
        mBottomNowPlayingText = findViewById(R.id.NowPlaying);
        mPlayToggleButton = findViewById(R.id.playTrig);
        mPlayerStatusText = findViewById(R.id.name);

        mStreamNameText = findViewById(R.id.tvChannel);
        mStreamDescriptionText = findViewById(R.id.rjName);
        mStreamContentTypeText = findViewById(R.id.tvMimeType);
        mStreamStartedText = findViewById(R.id.tvSampleRate);
        mStreamBitrateText = findViewById(R.id.tvBitRate);
        mStreamListenersText = null;
        mStreamGenreText = findViewById(R.id.tvGenre);

        if (mPlayToggleButton != null) {
            mPlayToggleButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    MusicPlayerManager.getInstance().togglePlay();
                }
            });
        }

        int refreshBtnId = getResources().getIdentifier("refresh_info_button", "id", getPackageName());
        if (refreshBtnId != 0) {
            View refreshBtn = findViewById(refreshBtnId);
            if (refreshBtn != null) {
                refreshBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        fetchMetadata();
                        Toast.makeText(MusicQuestActivity.this, "Refreshing info...", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

        showFallbackMetadata();
    }

    @Override
    protected void onResume() {
        super.onResume();
        MusicPlayerManager.getInstance().setListener(this);
        mHandler.post(mRefreshRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        MusicPlayerManager.getInstance().setListener(null);
        mHandler.removeCallbacks(mRefreshRunnable);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void fetchMetadata() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL("http://icecast.thaiirc.com:8000/status-json.xsl");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.setRequestMethod("GET");

                    int responseCode = conn.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                        StringBuilder response = new StringBuilder();
                        String inputLine;
                        while ((inputLine = in.readLine()) != null) {
                            response.append(inputLine);
                        }
                        in.close();
                        parseAndShowMetadata(response.toString());
                    } else {
                        showFallbackMetadata();
                    }
                } catch (Exception e) {
                    Log.e("MusicQuestActivity", "Error fetching metadata", e);
                    showFallbackMetadata();
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
        }).start();
    }

    private void parseAndShowMetadata(String jsonString) {
        try {
            JSONObject obj = new JSONObject(jsonString);
            if (obj.has("icestats")) {
                JSONObject icestats = obj.getJSONObject("icestats");
                if (icestats.has("source")) {
                    Object sourceObj = icestats.get("source");
                    JSONObject targetSource = null;
                    if (sourceObj instanceof JSONArray) {
                        JSONArray sources = (JSONArray) sourceObj;
                        for (int i = 0; i < sources.length(); i++) {
                            JSONObject src = sources.getJSONObject(i);
                            if (src.has("mount") && src.getString("mount").equals("/ices")) {
                                targetSource = src;
                                break;
                            }
                        }
                    } else if (sourceObj instanceof JSONObject) {
                        JSONObject src = (JSONObject) sourceObj;
                        if (src.has("mount") && src.getString("mount").equals("/ices")) {
                            targetSource = src;
                        }
                    }

                    if (targetSource != null) {
                        final String serverName = targetSource.optString("server_name", "Musichitz Looktung");
                        final String serverDesc = targetSource.optString("server_description", "ฮิตทั่วฟ้า แฟนเพลงขวัญใจมหาชน");
                        final String contentType = targetSource.optString("server_type", "audio/mpeg");
                        final String streamStarted = targetSource.optString("stream_start", "Fri, 17 Jul 2026 14:38:26 +0700");
                        final int bitrate = targetSource.optInt("bitrate", 128);
                        final int listeners = targetSource.optInt("listeners", 1);
                        final int peakListeners = targetSource.optInt("listener_peak", 4);
                        final String genre = targetSource.optString("genre", "Country");
                        final String title = targetSource.optString("title", "ส.ธ. - นุช วิลาวัลย์");

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (mStreamNameText != null) mStreamNameText.setText(serverName);
                                if (mStreamDescriptionText != null) mStreamDescriptionText.setText(serverDesc);
                                if (mStreamContentTypeText != null) mStreamContentTypeText.setText(contentType);
                                if (mStreamStartedText != null) mStreamStartedText.setText(streamStarted);
                                if (mStreamBitrateText != null) mStreamBitrateText.setText(bitrate + " kbps");
                                if (mStreamListenersText != null) mStreamListenersText.setText(listeners + " (Peak: " + peakListeners + ")");
                                if (mStreamGenreText != null) mStreamGenreText.setText(genre);
                                if (mNowPlayingText != null) mNowPlayingText.setText(title);
                                if (mBottomNowPlayingText != null) mBottomNowPlayingText.setText(title);
                            }
                        });
                        return;
                    }
                }
            }
        } catch (Exception e) {
            Log.e("MusicQuestActivity", "JSON parsing error", e);
        }
        showFallbackMetadata();
    }

    private void showFallbackMetadata() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mStreamNameText != null) mStreamNameText.setText("Musichitz Looktung");
                if (mStreamDescriptionText != null) mStreamDescriptionText.setText("ฮิตทั่วบ้าน ฮอตทุกหลังคาเรือน");
                if (mStreamContentTypeText != null) mStreamContentTypeText.setText("audio/mpeg");
                if (mStreamStartedText != null) mStreamStartedText.setText("Fri, 17 Jul 2026 14:38:26 +0700");
                if (mStreamBitrateText != null) mStreamBitrateText.setText("128 kbps");
                if (mStreamListenersText != null) mStreamListenersText.setText("1 (Peak: 4)");
                if (mStreamGenreText != null) mStreamGenreText.setText("Country");
                if (mNowPlayingText != null) mNowPlayingText.setText("ส.ธ. - นุช วิลาวัลย์");
                if (mBottomNowPlayingText != null) mBottomNowPlayingText.setText("ส.ธ. - นุช วิลาวัลย์");
            }
        });
    }

    @Override
    public void onPlayStateChanged(final boolean isPlaying, final boolean isPreparing) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean isThisStreamPlaying = isPlaying && MusicPlayerManager.DEFAULT_STREAM_URL.equals(MusicPlayerManager.getInstance().getCurrentUrl());
                boolean isThisStreamPreparing = isPreparing && MusicPlayerManager.DEFAULT_STREAM_URL.equals(MusicPlayerManager.getInstance().getCurrentUrl());
                if (mPlayToggleButton != null) {
                    if (isThisStreamPreparing) {
                        mPlayToggleButton.setText("BUFFERING...");
                        mPlayToggleButton.setEnabled(false);
                    } else if (isThisStreamPlaying) {
                        mPlayToggleButton.setText("STOP");
                        mPlayToggleButton.setEnabled(true);
                    } else {
                        mPlayToggleButton.setText("PLAY");
                        mPlayToggleButton.setEnabled(true);
                    }
                }
                if (mPlayerStatusText != null) {
                    if (isThisStreamPreparing) {
                        mPlayerStatusText.setText("Buffering...");
                    } else if (isThisStreamPlaying) {
                        mPlayerStatusText.setText("Playing");
                    } else {
                        mPlayerStatusText.setText("Radio Off");
                    }
                }
            }
        });
    }

    @Override
    public void onPlayerError(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MusicQuestActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }
}
