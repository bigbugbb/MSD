package com.bigbug.app.msdmobile;

import android.app.Activity;
import android.app.Fragment;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;

/**
 * A fragment representing a list of Items.
 * <p/>
 * Large screen devices (such as tablets) are supported by replacing the ListView
 * with a GridView.
 * <p/>
 */
public class RecommendListFragment extends Fragment {

    /**
     * The fragment's ListView/GridView.
     */
    private AbsListView mListView;

    /**
     * The Adapter which will be used to populate the ListView/GridView with Views.
     */
    private BaseAdapter mAdapter;

    /**
     * The song profile records downloaded, set it to the adapter.
     */
    private Records<SongProfile> mRecords;

    /**
     * The release id from which to find other songs with similar attributes.
     */
    private String mReleaseId;


    public static RecommendListFragment newInstance(String releaseId) {
        RecommendListFragment fragment = new RecommendListFragment();
        Bundle args = new Bundle();
        args.putString(Constants.Key.SONG_RELEASE_ID, releaseId);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public RecommendListFragment() {
        mRecords = new Records<SongProfile>(100);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Retrieve the release id.
        mReleaseId = getArguments().getString(Constants.Key.SONG_RELEASE_ID);

        // Create adapter for build and display the listview
        mAdapter = new SongProfileAdapter(getActivity(), mRecords);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_recommend, container, false);

        // Set the adapter
        mListView = (AbsListView) view.findViewById(android.R.id.list);
        mListView.setAdapter(mAdapter);

        setEmptyText("Loading resources from server...");

        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Send a volley request to the MSD-django server to fetch the song profiles.
        if (mRecords.size() == 0) {
            updateRecommendSongs();
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
        final String json = sp.getString(Constants.Key.SONG_PROFILE + mReleaseId, "");
        if (!json.isEmpty()) {
            Type collectionType = new TypeToken<Records<SongProfile>>(){}.getType();
            mRecords = new Gson().fromJson(json, collectionType);
        }
    }

    @Override
    public void onDetach() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
        final String json = new Gson().toJson(mRecords);
        if (!json.isEmpty()) {
            sp.edit().putString(Constants.Key.SONG_PROFILE + mReleaseId, json).commit();
        }
        super.onDetach();
    }

    public void updateRecommendSongs() {
        // Construct query url
        final String query = Constants.Url.RECOMMEND_SONGS + mReleaseId;

        // Request video records
        RecordsRequester.request(query, mRecords, mAdapter, new RecordsRequester.OnParsingRecordListener() {

            @Override
            public Object onParsingRecord(JSONObject record) {
                SongProfile profile = new SongProfile();
                try {
                    // It's actually the cluster data, so no year and track id.
                    JSONObject fields = record.getJSONObject("fields");
                    profile.mTitle  = fields.getString("title");
                    profile.mArtist = fields.getString("artist");
                    profile.mImage  = fields.getString("image");
                } catch (JSONException e) {
                    profile = null;
                }
                return profile;
            }
        });
    }

    /**
     * The default content for this Fragment has a TextView that is shown when
     * the list is empty. If you would like to change the text, call this method
     * to supply the text it should use.
     */
    public void setEmptyText(CharSequence emptyText) {
        View emptyView = mListView.getEmptyView();
        if (emptyView instanceof TextView) {
            ((TextView) emptyView).setText(emptyText);
        }
    }
}
