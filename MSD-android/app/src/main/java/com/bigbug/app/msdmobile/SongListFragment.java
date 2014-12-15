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
import android.widget.AdapterView;
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
 * Activities containing this fragment MUST implement the {@link OnFragmentInteractionListener}
 * interface.
 */
public class SongListFragment extends Fragment {

    private OnFragmentInteractionListener mListener;

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


    public static SongListFragment newInstance(String param) {
        SongListFragment fragment = new SongListFragment();
        return fragment;
    }

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public SongListFragment() {
        mRecords = new Records<SongProfile>(100);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAdapter = new SongProfileAdapter(getActivity(), mRecords);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_song, container, false);

        // Set the adapter
        mListView = (AbsListView) view.findViewById(android.R.id.list);
        mListView.setAdapter(mAdapter);

        // Set OnItemClickListener so we can be notified on item clicks
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (null != mListener) {
                    // Notify the active callbacks interface (the activity, if the
                    // fragment is attached to one) that an item has been selected.
                    mListener.onFragmentInteraction(mRecords.get(position));
                }
            }
        });

        setEmptyText("Loading resources from server...");

        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Send a volley request to the MSD-django server to fetch the song profiles.
        if (mRecords.size() == 0) {
            updateSongProfiles();
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (OnFragmentInteractionListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnFragmentInteractionListener");
        }
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
        final String json = sp.getString(Constants.Key.JSON_SONG_PROFILES, "");
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
            sp.edit().putString(Constants.Key.JSON_SONG_PROFILES, json).commit();
        }
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AnimateFirstDisplayListener.sDisplayedImages.clear();
    }

    public void updateSongProfiles() {
        // Construct query url
        final String query = Constants.Url.SONG_PROFILES;

        // Request video records
        RecordsRequester.request(query, mRecords, mAdapter, new RecordsRequester.OnParsingRecordListener() {

            @Override
            public Object onParsingRecord(JSONObject record) {
                SongProfile profile = new SongProfile();
                try {
                    JSONObject fields = record.getJSONObject("fields");
                    profile.mTrackId   = fields.getString("trackid");
                    profile.mReleaseId = fields.getString("releaseid");
                    profile.mTitle     = fields.getString("title");
                    profile.mArtist    = fields.getString("artist");
                    profile.mYear      = fields.getString("year");
                    profile.mImage     = fields.getString("image");
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

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p/>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        public void onFragmentInteraction(SongProfile profile);
    }
}
