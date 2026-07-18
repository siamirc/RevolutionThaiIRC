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

public class LiveRadioActivity extends ThemedActivity implements MusicPlayerManager.PlayStateListener {

    private static final String STREAM_URL = "http://radio.thaiirc.com:8002/ices";
    private static final String METADATA_URL = "http://radio.thaiirc.com:8002/status-json.xsl";

    private TextView mNowPlayingText;
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
        setContentView(R.layout.activity_live_radio);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Live Radio");
        }

        mNowPlayingText = findViewById(R.id.now_playing_text);
        mPlayToggleButton = findViewById(R.id.play_toggle_button);
        mPlayerStatusText = findViewById(R.id.player_status_text);

        mStreamNameText = findViewById(R.id.stream_name);
        mStreamDescriptionText = findViewById(R.id.stream_description);
        mStreamContentTypeText = findViewById(R.id.stream_content_type);
        mStreamStartedText = findViewById(R.id.stream_started);
        mStreamBitrateText = findViewById(R.id.stream_bitrate);
        mStreamListenersText = findViewById(R.id.stream_listeners);
        mStreamGenreText = findViewById(R.id.stream_genre);

        mPlayToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MusicPlayerManager.getInstance().togglePlay(STREAM_URL);
            }
        });

        findViewById(R.id.refresh_info_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchMetadata();
                Toast.makeText(LiveRadioActivity.this, "Refreshing info...", Toast.LENGTH_SHORT).show();
            }
        });

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
                    URL url = new URL(METADATA_URL);
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
                    Log.e("LiveRadioActivity", "Error fetching metadata", e);
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
                        final String serverName = targetSource.optString("server_name", "Musichitz Station Online");
                        final String serverDesc = targetSource.optString("server_description", "Music All Day Music All Night");
                        final String contentType = targetSource.optString("server_type", "audio/mpeg");
                        final String streamStarted = targetSource.optString("stream_start", "Sat, 18 Jul 2026 00:10:12 +0700");
                        final int bitrate = targetSource.optInt("bitrate", 128);
                        final int listeners = targetSource.optInt("listeners", 1);
                        final int peakListeners = targetSource.optInt("listener_peak", 7);
                        final String genre = targetSource.optString("genre", "Pop");
                        final String title = targetSource.optString("title", "Violette Wautier - BACK TO REALITY");

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mStreamNameText.setText(serverName);
                                mStreamDescriptionText.setText(serverDesc);
                                mStreamContentTypeText.setText(contentType);
                                mStreamStartedText.setText(streamStarted);
                                mStreamBitrateText.setText(String.valueOf(bitrate));
                                mStreamListenersText.setText(listeners + " (Peak: " + peakListeners + ")");
                                mStreamGenreText.setText(genre);
                                mNowPlayingText.setText(title);
                            }
                        });
                        return;
                    }
                }
            }
        } catch (Exception e) {
            Log.e("LiveRadioActivity", "JSON parsing error", e);
        }
        showFallbackMetadata();
    }

    private void showFallbackMetadata() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStreamNameText.setText("Musichitz Station Online");
                mStreamDescriptionText.setText("Music All Day Music All Night");
                mStreamContentTypeText.setText("audio/mpeg");
                mStreamStartedText.setText("Sat, 18 Jul 2026 00:10:12 +0700");
                mStreamBitrateText.setText("128");
                mStreamListenersText.setText("1 (Peak: 7)");
                mStreamGenreText.setText("Pop");
                mNowPlayingText.setText("Violette Wautier - BACK TO REALITY");
            }
        });
    }

    @Override
    public void onPlayStateChanged(final boolean isPlaying, final boolean isPreparing) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean isThisStreamPlaying = isPlaying && STREAM_URL.equals(MusicPlayerManager.getInstance().getCurrentUrl());
                boolean isThisStreamPreparing = isPreparing && STREAM_URL.equals(MusicPlayerManager.getInstance().getCurrentUrl());
                if (isThisStreamPreparing) {
                    mPlayToggleButton.setText("BUFFERING...");
                    mPlayToggleButton.setEnabled(false);
                    mPlayerStatusText.setText("Status: Buffering stream...");
                } else if (isThisStreamPlaying) {
                    mPlayToggleButton.setText("TURN OFF");
                    mPlayToggleButton.setEnabled(true);
                    mPlayerStatusText.setText("Status: Playing");
                } else {
                    mPlayToggleButton.setText("TURN ON");
                    mPlayToggleButton.setEnabled(true);
                    mPlayerStatusText.setText("Status: Stopped");
                }
            }
        });
    }

    @Override
    public void onPlayerError(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(LiveRadioActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }
}
