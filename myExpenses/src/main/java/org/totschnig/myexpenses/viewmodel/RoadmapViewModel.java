package org.totschnig.myexpenses.viewmodel;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.text.format.DateUtils;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.totschnig.myexpenses.BuildConfig;
import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.model.ContribFeature;
import org.totschnig.myexpenses.preference.PrefKey;
import org.totschnig.myexpenses.retrofit.Issue;
import org.totschnig.myexpenses.retrofit.RoadmapService;
import org.totschnig.myexpenses.retrofit.Vote;
import org.totschnig.myexpenses.util.Utils;
import org.totschnig.myexpenses.util.io.StreamReader;
import org.totschnig.myexpenses.util.licence.LicenceHandler;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import timber.log.Timber;

public class RoadmapViewModel extends AndroidViewModel {
  public static final String ROADMAP_URL = BuildConfig.DEBUG ?
      "https://votedb-staging.herokuapp.com/"  : "https://roadmap.myexpenses.mobi/";

  @Inject
  OkHttpClient.Builder builder;
  @Inject
  LicenceHandler licenceHandler;

  private final MutableLiveData<List<Issue>> data = new MutableLiveData<>();
  private final MutableLiveData<Vote> lastVote = new MutableLiveData<>();
  private final MutableLiveData<Vote> voteResult = new MutableLiveData<>();
  private final MutableLiveData<Integer> voteReminder = new MutableLiveData<>();
  private static final String ISSUE_CACHE = "issue_cache.json";
  private static final String ROADMAP_VOTE = "roadmap_vote.json";
  private RoadmapService roadmapService;
  private Gson gson;

  public RoadmapViewModel(Application application) {
    super(application);
    ((MyApplication) application).getAppComponent().inject(this);
    gson = new Gson();

    final OkHttpClient okHttpClient = builder
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();
    Retrofit retrofit = new Retrofit.Builder()
        .baseUrl(ROADMAP_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .client(okHttpClient)
        .build();
    roadmapService = retrofit.create(RoadmapService.class);
  }

  public void loadLastVote() {
    new LoadLastVoteTask().execute();
  }

  public void loadVoteReminder() {
    new LoadVoteReminderTask().execute();
  }

  public LiveData<List<Issue>> getData() {
    return data;
  }

  public LiveData<Vote> getVoteResult() {
    return voteResult;
  }

  public LiveData<Vote> getLastVote() {
    return lastVote;
  }

  public void loadData(boolean withCache) {
    new LoadIssuesTask(withCache).execute();
  }

  public void submitVote(String key, Map<Integer, Integer> voteWeights) {
    new VoteTask(key).execute(voteWeights);
  }

  public void cacheWeights(Map<Integer, Integer> voteWeights) {
    PrefKey.ROADMAP_VOTE.putString(gson.toJson(voteWeights));
  }

  public Map<Integer, Integer> restoreWeights() {
    String stored = PrefKey.ROADMAP_VOTE.getString(null);

    return stored != null ? gson.fromJson(stored, new TypeToken<Map<Integer, Integer>>(){}.getType()) :
        new HashMap<>();
  }

  public LiveData<Integer> getVoteReminder() {
    return voteReminder;
  }

  private class VoteTask extends AsyncTask<Map<Integer, Integer>, Void, Vote> {

    @Nullable
    private final String key;

    public VoteTask(@Nullable String key) {
      this.key = key;
    }

    @Override
    protected Vote doInBackground(Map<Integer, Integer>... votes) {
      boolean isPro = ContribFeature.ROADMAP_VOTING.hasAccess();
      Vote vote = new Vote(key != null ? key : licenceHandler.buildRoadmapVoteKey(), votes[0], isPro);
      try {
        Call<Void> voteCall = roadmapService.createVote(vote);
        Response<Void> voteResponse = voteCall.execute();
        if (voteResponse.isSuccessful()) {
          writeToFile(ROADMAP_VOTE, gson.toJson(vote));
          //after a vote has been recorded, we start checking again, if the vote has become outdated
          PrefKey.VOTE_REMINDER_SHOWN.putBoolean(false);
          return vote;
        }
      } catch (IOException | SecurityException e) {
        Timber.i(e);
      }
      return null;
    }

    @Override
    protected void onPostExecute(Vote result) {
      voteResult.setValue(result);
    }
  }

  private class LoadIssuesTask extends AsyncTask<Void, Void, List<Issue>> {

    private final boolean withCache;

    public LoadIssuesTask(boolean withCache) {
      this.withCache = withCache;
    }

    @Override
    protected List<Issue> doInBackground(Void... voids) {
      List<Issue> issueList = null;

      if (withCache) {
        try {
          Type listType = new TypeToken<ArrayList<Issue>>() {
          }.getType();
          issueList = gson.fromJson(readFromFile(ISSUE_CACHE), listType);
          Timber.i("Loaded %d issues from cache", issueList.size());
        } catch (IOException e) {
          Timber.i(e);
        }
      }

      if (issueList == null) {
        issueList = readIssuesFromNetwork();
      }
      return issueList;
    }

    @Override
    protected void onPostExecute(List<Issue> result) {
      data.setValue(result);
    }
  }

  private class LoadLastVoteTask extends AsyncTask<Void, Void, Vote> {

    @Override
    protected Vote doInBackground(Void... voids) {
      return readLastVoteFromFile();
    }


    @Override
    protected void onPostExecute(Vote result) {
      lastVote.setValue(result);
    }
  }

  private class LoadVoteReminderTask extends AsyncTask<Void, Void, Integer> {

    private final int WAIT_AFTER_INSTALL_DAYS = BuildConfig.DEBUG ? 0 : 100;
    private final long CHECK_INTERVALL_MILLIS = BuildConfig.DEBUG ? DateUtils.MINUTE_IN_MILLIS : DateUtils.WEEK_IN_MILLIS * 4;

    @Override
    protected Integer doInBackground(Void... voids) {
      final long voteReminderLastCheck = PrefKey.VOTE_REMINDER_LAST_CHECK.getLong(0);
      final long now = System.currentTimeMillis();
      final long sinceLastCheck = now - voteReminderLastCheck;
      if (PrefKey.VOTE_REMINDER_SHOWN.getBoolean(false) || sinceLastCheck < CHECK_INTERVALL_MILLIS) {
        return null;
      }
      Vote lastVote = readLastVoteFromFile();
      if (lastVote == null && Utils.getDaysSinceInstall(getApplication()) < WAIT_AFTER_INSTALL_DAYS) {
        return null;
      }
      List<Issue> issueList = readIssuesFromNetwork();
      PrefKey.VOTE_REMINDER_LAST_CHECK.putLong(now);
      if (issueList == null) {
        return null;
      }
      if (lastVote == null) {
        return R.string.roadmap_intro;
      }
      List<Integer> issueNumbers = Stream.of(issueList).map(Issue::getNumber).collect(Collectors.toList());
      for (Integer issueNr: lastVote.getVote().keySet()) {
        if (!issueNumbers.contains(issueNr)) {
          return R.string.reminder_vote_update;
        }
      }
      return null;
    }

    @Override
    protected void onPostExecute(Integer integer) {
      voteReminder.setValue(integer);
    }
  }

  @Nullable
  private Vote readLastVoteFromFile() {
    Vote lastVote = null;

    try {
      lastVote = gson.fromJson(readFromFile(ROADMAP_VOTE), Vote.class);
    } catch (IOException e) {
      Timber.i(e);
    }
    return lastVote;
  }

  private List<Issue> readIssuesFromNetwork() {
    List<Issue> issueList = null;
    try {
      Call<List<Issue>> issuesCall = roadmapService.getIssues();
      Response<List<Issue>> response = issuesCall.execute();
      issueList = response.body();
      if (response.isSuccessful() && issueList != null) {
        Timber.i("Loaded %d issues from network", issueList.size());
        writeToFile(ISSUE_CACHE, gson.toJson(issueList));
      }
    } catch (IOException | SecurityException e) {
      Timber.i(e);
    }
    return issueList;
  }


  private void writeToFile(String fileName, String json) throws IOException {
    FileOutputStream fos = getApplication().openFileOutput(fileName, Context.MODE_PRIVATE);
    fos.write(json.getBytes());
    fos.close();
  }

  private String readFromFile(String filename) throws IOException {
    FileInputStream fis = getApplication().openFileInput(filename);
    return new StreamReader(fis).read();
  }
}
