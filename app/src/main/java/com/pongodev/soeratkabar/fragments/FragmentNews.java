package com.pongodev.soeratkabar.fragments;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NoConnectionError;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.lsjwzh.widget.materialloadingprogressbar.CircleProgressBar;
import com.marshalchen.ultimaterecyclerview.ItemTouchListenerAdapter;
import com.marshalchen.ultimaterecyclerview.UltimateRecyclerView;
import com.pongodev.soeratkabar.R;
import com.pongodev.soeratkabar.activities.ActivityDetail;
import com.pongodev.soeratkabar.adapters.AdapterList;
import com.pongodev.soeratkabar.libs.MySingleton;
import com.pongodev.soeratkabar.utils.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Design and developed by pongodev.com
 *
 * FragmentNews is created to display latest location data.
 * Created using Fragment.
 */
public class FragmentNews extends Fragment implements
        OnClickListener {

    // Tag for log
    private static final String TAG = FragmentNews.class.getSimpleName();

    // Create view objects
    private TextView mLblNoResult;
    private LinearLayout mLytRetry;
    private CircleProgressBar mPrgLoading;
    private UltimateRecyclerView mUltimateRecyclerView;

    // Create adapter object
    private AdapterList mAdapter;

    // Paramater (true = data still exist in server, false = data already loaded all)
    private boolean mIsStillLoding = true;
    // Paramater (true = is first time, false = not first time)
    private boolean mIsAppFirstLaunched = true;
    // initially offset will be 0, later will be updated while parsing the json
    private int mStartIndex = 0;

    // Create variable to store category id and activity
    private String mCategoryId;
    // Create arraylist to store location data
    private ArrayList<HashMap<String, String>> newsData = new ArrayList<>();
    // Create variable to handle admob visibility
    private boolean mIsAdmobVisible;

    // Return media earlier than this max_id.
    private Integer mCurrentPage=1;

    private Bundle bundle;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_list, container, false);

        Utils.saveIntPreferences(getActivity(),
                Utils.ARG_DRAWER_PREFERENCE,
                Utils.ARG_PREFERENCES_DRAWER, 2);
        Log.e("testing1", "= " + R.id.nav_latest_news);
        // Get Bundle data
        bundle = this.getArguments();


        if (bundle != null) {
            // Get data from ActivityHome or Activity Place By Category
            String mActivity = bundle.getString(Utils.EXTRA_ACTIVITY);
            if (mActivity != null) {
                if (mActivity.equals(Utils.TAG_ACTIVITY_NEWSBYCATEGORY)) {
                    mCategoryId = bundle.getString(Utils.EXTRA_CATEGORY_ID);
                }
            }
        }

        // Connect view objects and view ids from xml
        mUltimateRecyclerView = (UltimateRecyclerView) view.findViewById(R.id.ultimate_recycler_view);
        mLblNoResult          = (TextView) view.findViewById(R.id.lblNoResult);
        mLytRetry             = (LinearLayout) view.findViewById(R.id.lytRetry);
        mPrgLoading           = (CircleProgressBar) view.findViewById(R.id.prgLoading);

        AppCompatButton btnRetry    = (AppCompatButton) view.findViewById(R.id.btnRetry);
        AdView mAdView              = (AdView) view.findViewById(R.id.adView);

        // Set click listener to the button
        btnRetry.setOnClickListener(this);
        // Set circular progress bar color
        mPrgLoading.setColorSchemeResources(R.color.accent_color);
        // Set circular progress bar visibility to visible first
        mPrgLoading.setVisibility(View.VISIBLE);

        // Get admob visibility value
        mIsAdmobVisible = Utils.admobVisibility(mAdView, Utils.IS_ADMOB_VISIBLE);
        // Load ad in background using asynctask class
        new SyncShowAd(mAdView).execute();

        // Set arraylist
        newsData = new ArrayList<>();

        // Set mAdapter
        mAdapter = new AdapterList(getActivity(), newsData);
        mUltimateRecyclerView.setAdapter(mAdapter);
        mUltimateRecyclerView.setHasFixedSize(false);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity());
        mUltimateRecyclerView.setLayoutManager(linearLayoutManager);

        mUltimateRecyclerView.enableLoadmore();
        // Set layout for custom loading when load more
        mAdapter.setCustomLoadMoreView(LayoutInflater.from(getActivity())
                .inflate(R.layout.loadmore_progressbar, null));

        // Listener for handle load more
        mUltimateRecyclerView.setOnLoadMoreListener(new UltimateRecyclerView.OnLoadMoreListener() {
            @Override
            public void loadMore(int itemsCount, final int maxLastVisiblePosition) {
                // if True is means still have data in server
                if (mIsStillLoding) {
                    // Set layout for custom the loading when load more. mAdapter is set
                    // again because when load data is response error setCustomLoadMoreView
                    // is null to clear view loading
                    mAdapter.setCustomLoadMoreView(LayoutInflater.from(getActivity())
                            .inflate(R.layout.loadmore_progressbar, null));

                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        public void run() {
                            getNewsData();
                        }
                    }, 1000);
                } else {
                    disableLoadmore();
                }

            }
        });

        // Condition when item in list is click
        ItemTouchListenerAdapter itemTouchListenerAdapter = new ItemTouchListenerAdapter(mUltimateRecyclerView.mRecyclerView,
            new ItemTouchListenerAdapter.RecyclerViewOnItemClickListener() {
                @Override
                public void onItemClick(RecyclerView parent, View clickedView, int position) {
                    // To handle when position  = newsData.size means loading view si click
                    if (position < newsData.size()) {
                        Intent i = new Intent(getActivity(), ActivityDetail.class);
                        i.putExtra(Utils.EXTRA_NEWS_ID, newsData.get(position).get(Utils.KEY_ID));
                        startActivity(i);
                    }
                }

                @Override
                public void onItemLongClick(RecyclerView recyclerView, View view, int i) {
                }
            });

        // Enable touch listener
        mUltimateRecyclerView.mRecyclerView.addOnItemTouchListener(itemTouchListenerAdapter);

        // Set StartIndex from zero and first time is true
        mStartIndex = 0;
        mIsAppFirstLaunched = true;
        // Get data from server in first time when activity create
        getNewsData();

        return view;
    }

    // Asynctask class to load admob in background
    public class SyncShowAd extends AsyncTask<Void, Void, Void> {

        final AdView ad;
        AdRequest adRequest;

        public SyncShowAd(AdView ad) {
            this.ad = ad;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            // Check AD visibility. If visible, create adRequest
            if (mIsAdmobVisible) {
                // Create an AD request
                if (Utils.IS_ADMOB_IN_DEBUG) {
                    adRequest = new AdRequest.Builder().
                            addTestDevice(AdRequest.DEVICE_ID_EMULATOR).build();
                } else {
                    adRequest = new AdRequest.Builder().build();
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            // Check ad visibility. If visible, display ad banner and interstitial
            if (mIsAdmobVisible) {
                // Start loading the ad
                ad.loadAd(adRequest);

            }

        }
    }

    private void getNewsData() {

        String url;
        if(bundle != null){
            // http://pongodev.com/?json=get_posts&count=10&status=published&offset=0
            url = Utils.API_WORDPRESS+Utils.VALUE_GET_POSTS+"&"+
                    Utils.PARAM_COUNT+Utils.VALUE_PER_PAGE+"&"+
                    Utils.PARAM_STATUS+"&"+
                    Utils.PARAM_OFFSET+mStartIndex+"&"+
                    Utils.PARAM_CATEGORIES+mCategoryId;
        } else {
            // http://pongodev.com/category/support/?json=get_posts&count=6&status=published&offset=0
            url = Utils.API_WORDPRESS+Utils.VALUE_GET_POSTS+"&"+
                    Utils.PARAM_COUNT+Utils.VALUE_PER_PAGE+"&"+
                    Utils.PARAM_STATUS+"&"+
                    Utils.PARAM_OFFSET+mStartIndex;
        }


        Log.e("testing","url= "+url);
        JsonObjectRequest request = new JsonObjectRequest(url, null,
                new Response.Listener<JSONObject>() {
                    JSONArray postsWordpressArray, attachmentsWordpressArray;
                    JSONObject postsWordpressObject, attachmentsWordpressObject,
                            attachmentsImagesWordpressObject, attachmentsImagesFullWordpressObject;
                    Integer mTotalPages;

                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            // Get all json from server
                            postsWordpressArray = response.getJSONArray(Utils.ARRAY_POSTS);
                            if (postsWordpressArray.length() > 0) {
                                haveResultView();
                                for (int i = 0; i < postsWordpressArray.length(); i++) {
                                    HashMap<String, String> dataMap = new HashMap<>();
                                    postsWordpressObject = postsWordpressArray.getJSONObject(i);

                                    // Get id, title, and date
                                    dataMap.put(Utils.KEY_ID, postsWordpressObject.getString(Utils.KEY_ID));
                                    dataMap.put(Utils.KEY_TITLE, postsWordpressObject.getString(Utils.KEY_TITLE));
                                    dataMap.put(Utils.KEY_DATE, postsWordpressObject.getString(Utils.KEY_DATE));

                                    // Get Images Full size
                                    attachmentsWordpressArray = postsWordpressObject.getJSONArray(Utils.ARRAY_ATTACHMENTS);

                                    // Variable to store length of attach array
                                    int attachLength = attachmentsWordpressArray.length();

                                    // Condition if length of attach array 0
                                    if(attachLength==0){
                                        dataMap.put(Utils.KEY_IMAGE_FULL_URL, "empty");
                                        dataMap.put(Utils.KEY_IMAGE_THUMBNAIL_URL, "empty");
                                    } else {
                                        attachmentsWordpressObject = attachmentsWordpressArray.getJSONObject(0);
                                        attachmentsImagesWordpressObject = attachmentsWordpressObject.getJSONObject(Utils.OBJECT_IMAGES);
                                        attachmentsImagesFullWordpressObject = attachmentsImagesWordpressObject.getJSONObject(Utils.OBJECT_IMAGES_FULL);
                                        dataMap.put(Utils.KEY_IMAGE_FULL_URL, attachmentsImagesFullWordpressObject.getString(Utils.KEY_URL));

                                        // Get Images Thumbnail size
                                        attachmentsImagesFullWordpressObject = attachmentsImagesWordpressObject.getJSONObject(Utils.OBJECT_IMAGES_THUMBNAIL);
                                        dataMap.put(Utils.KEY_IMAGE_THUMBNAIL_URL, attachmentsImagesFullWordpressObject.getString(Utils.KEY_URL));
                                    }


                                    newsData.add(dataMap);

                                    // Insert 1 by 1 to mAdapter
                                    mAdapter.notifyItemInserted(newsData.size());
                                }
                                mTotalPages = response.getInt(Utils.KEY_PAGES);
                                // Condition when paginationFLICKRObject is null its mean nomore data in server.
                                if(mCurrentPage < mTotalPages){
                                    mCurrentPage+=1;
                                    mStartIndex+=Utils.VALUE_PER_PAGE;
                                } else {
                                    disableLoadmore();

                                }

                                // If success get data it means next its not first time again
                                mIsAppFirstLaunched = false;

                                // Possibility still exist in server
                                mIsStillLoding = true;

                                // Data from server already load all or no data in server
                            } else {
                                if (mIsAppFirstLaunched && mAdapter.getAdapterItemCount() <= 0) {
                                    noResultView();
                                }
                                disableLoadmore();
                            }

                        } catch (JSONException e) {
                            Log.d(Utils.TAG_PONGODEV + TAG, "JSON Parsing error: " + e.getMessage());
                            mPrgLoading.setVisibility(View.GONE);
                        }
                        mPrgLoading.setVisibility(View.GONE);

                    }
                },

                new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // To make sure Activity is still in the foreground
                        Activity activity = getActivity();
                        if(activity != null && isAdded()){
                            Log.d(Utils.TAG_PONGODEV + TAG, "on Error Response: " + error.getMessage());
                            // "try-catch" To handle when still in process and then application closed
                            try {
                                if (error instanceof NoConnectionError) {
                                    retryView(getString(R.string.no_internet_connection));
                                } else {
                                    retryView(getString(R.string.response_error));
                                }

                                // To handle when no data in mAdapter and then get error because
                                // no connection or problem in server
                                if (newsData.size() == 0) {
                                    retryView(getString(R.string.no_result));

                                    // Conditon when loadmore, it have data when loadmore then get
                                    // error because no connection
                                } else {
                                    mAdapter.setCustomLoadMoreView(null);
                                    mAdapter.notifyDataSetChanged();
                                }

                                mPrgLoading.setVisibility(View.GONE);

                            } catch (Exception e) {
                                Log.d(Utils.TAG_PONGODEV + TAG, "failed catch volley " + e.toString());
                                mPrgLoading.setVisibility(View.GONE);
                            }
                        }

                    }
                }
        );
        request.setRetryPolicy(new DefaultRetryPolicy(Utils.ARG_TIMEOUT_MS,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        MySingleton.getInstance(getActivity()).getRequestQueue().add(request);

    }

    // Method to display retry layout if can not connect to the server
    private void retryView(String message) {
        mLytRetry.setVisibility(View.VISIBLE);
        mUltimateRecyclerView.setVisibility(View.GONE);
        mLblNoResult.setVisibility(View.GONE);
        mLblNoResult.setText(message);
    }

    // Method to display result view if data is available
    private void haveResultView() {
        mLytRetry.setVisibility(View.GONE);
        mUltimateRecyclerView.setVisibility(View.VISIBLE);
        mLblNoResult.setVisibility(View.GONE);
    }

    // Method to display no result view if data is not available
    private void noResultView() {
        mLytRetry.setVisibility(View.GONE);
        mUltimateRecyclerView.setVisibility(View.GONE);
        mLblNoResult.setVisibility(View.VISIBLE);

    }

    // Method to disable load more
    private void disableLoadmore() {
        mIsStillLoding = false;
        if (mUltimateRecyclerView.isLoadMoreEnabled()) {
            mUltimateRecyclerView.disableLoadmore();
        }
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btnRetry:
                mPrgLoading.setVisibility(View.VISIBLE);
                haveResultView();
                getNewsData();
                break;
            default:
                break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Utils.saveIntPreferences(getActivity(),
                Utils.ARG_DRAWER_PREFERENCE, Utils.ARG_PREFERENCES_DRAWER, 1);
    }

}

