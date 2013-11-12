package com.tokbox.android.opentokrtc;

import android.app.Fragment;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.opentok.android.Connection;
import com.opentok.android.OpentokException;
import com.opentok.android.Publisher;
import com.opentok.android.Session;
import com.opentok.android.Stream;
import com.opentok.android.Subscriber;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;

/**
 * Created by ankur on 11/10/13.
 */
public class ChatRoomFragment extends Fragment implements Session.Listener, Publisher.Listener, Subscriber.Listener {

    public static final String ARG_ROOM_ID = "room_id";
    public static final String TAG = "ChatRoomFragment";

    protected String mRoomName;
    protected Room mRoom;

    protected Session mSession;
    protected Publisher mPublisher;
    protected boolean mIsPublisherStreaming;
    protected Subscriber mSubscriber;
    protected ArrayList<Stream> mStreams;

    protected FrameLayout mSubscriberContainer;
    protected FrameLayout mPublisherContainer;

    private class GetRoomDataTask extends AsyncTask<String, Void, Room> {

        // TODO: support cancellation and progress indicator?
        // TODO: better error handling

        protected HttpClient mHttpClient;
        protected HttpGet mHttpGet;

        public GetRoomDataTask() {
            mHttpClient = new DefaultHttpClient();
        }

        @Override
        protected Room doInBackground(String... params) {
            String sessionId = null;
            String token = null;
            String apiKey = null;
            initializeGetRequest(params[0]);
            try {
                HttpResponse roomResponse = mHttpClient.execute(mHttpGet);
                HttpEntity roomEntity = roomResponse.getEntity();
                String temp = EntityUtils.toString(roomEntity);
                Log.i(TAG, "retrieved room response: " + temp);
                JSONObject roomJson = new JSONObject(temp);
                sessionId = roomJson.getString("sid");
                token = roomJson.getString("token");
                apiKey = roomJson.getString("apiKey");
            } catch (Exception exception) {
                Log.e(TAG, "could not get room data: " + exception.getMessage());
            }
            return new Room(params[0], sessionId, token, apiKey);
        }

        @Override
        protected void onPostExecute(final Room result) {
            // TODO: it might be better to set up some kind of callback interface
            setRoom(result);
        }

        protected void initializeGetRequest(String room) {
            URI roomURI;
            URL url;
            // TODO: construct urlStr from injectable values for testing
            String urlStr = "https://opentokrtc.com/" + room + ".json";
            try {
                url = new URL(urlStr);
                roomURI = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef());
            } catch (URISyntaxException exception) {
                Log.e(TAG, "the room URI is malformed: " + exception.getMessage());
                return;
            } catch (MalformedURLException exception) {
                Log.e(TAG, "the room URI is malformed: " + exception.getMessage());
                return;
            }
            // TODO: check if alternate constructor will escape invalid characters properly, might be able to avoid all above code in this method
            mHttpGet = new HttpGet(roomURI);
        }
    }

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ChatRoomFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(TAG, "onCreate");

        String roomName = getArguments().getString(ARG_ROOM_ID);
        setRoomName(roomName);

        mStreams = new ArrayList<Stream>();
        mIsPublisherStreaming = false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.i(TAG, "onCreateView");

        View rootView = inflater.inflate(R.layout.fragment_chat_room, container, false);

        mSubscriberContainer = (FrameLayout) rootView.findViewById(R.id.subscriberContainer);
        mPublisherContainer = (FrameLayout) rootView.findViewById(R.id.publisherContainer);

        return rootView;
    }

    @Override
    public void onStop() {
        super.onStop();

        Log.i(TAG, "onStop");

        if (mSession != null) {
            mSession.disconnect();
        }
        getActivity().finish();
    }



    public void setRoomName(String roomName) {
        Log.i(TAG, "setRoomName");
        if (roomName != null && !roomName.equals(mRoomName)) {
            mRoomName = roomName;
            initializeRoom();
        }
    }

    public String getRoomName() {
        return mRoomName;
    }

    protected void setRoom(Room room) {
        Log.i(TAG, "setting room: " + room.toString());
        mRoom = room;
        enterRoom();
    }

    protected void enterRoom() {
        Log.i(TAG, "enterRoom");
        mSession = Session.newInstance(getActivity(), mRoom.getSessionId(), this);
        mSession.connect(mRoom.getApiKey(), mRoom.getToken());
    }

    protected void leaveRoom() {
        Log.i(TAG, "leaveRoom");
        // TODO: make sure this method is called before the activity goes away
        mSession.disconnect();
    }

    /**
     * Initialization occurs as soon as a new mRoomName has been set
     */
    private void initializeRoom() {
        Log.i(TAG, "initializing chat room fragment for room: " + mRoomName);
        getActivity().setTitle(mRoomName);
        GetRoomDataTask task = new GetRoomDataTask();
        task.execute(mRoomName);
    }

    private void subscribeToStream(Stream stream) {
        Log.i(TAG, "subscribing to stream: " + stream.getStreamId());
        mSubscriber = Subscriber.newInstance(getActivity(), stream, this);
        FrameLayout subscriberContainer = (FrameLayout) getView().findViewById(R.id.subscriberContainer);
        subscriberContainer.addView(mSubscriber.getView());
        mSession.subscribe(mSubscriber);
    }

    @Override
    public void onSessionConnected() {
        Log.i(TAG, "session connected.");

        if (mPublisher == null) {
            mPublisher = Publisher.newInstance(getActivity(), this, null);
            mSession.publish(mPublisher);
            mIsPublisherStreaming = false;

            FrameLayout publisherContainer = (FrameLayout) getView().findViewById(R.id.publisherContainer);
            publisherContainer.addView(mPublisher.getView());
        }
    }

    @Override
    public void onSessionDisconnected() {
        Log.i(TAG, "session disconnected.");

        if (mPublisher != null) {
            mPublisherContainer.removeView(mPublisher.getView());
        }

        if (mSubscriber != null) {
            mSubscriberContainer.removeView(mSubscriber.getView());
        }

        mPublisher = null;
        mIsPublisherStreaming = false;
        mSubscriber = null;
        mStreams.clear();
        mSession = null;
    }

    @Override
    public void onSessionReceivedStream(Stream stream) {
        Log.i(TAG, "stream received: " + stream.getStreamId());

        if (mPublisher != null && ((!mIsPublisherStreaming) || (mIsPublisherStreaming && !mPublisher.getStreamId().equals(stream.getStreamId())))) {

            mStreams.add(stream);

            if (mSubscriber == null) {
                subscribeToStream(stream);
            }
        }
    }

    @Override
    public void onSessionDroppedStream(Stream stream) {
        Log.i(TAG, "stream dropped: " + stream.getStreamId());

        mStreams.remove(stream);

        if (stream.getStreamId().equals(mSubscriber.getStream().getStreamId())) {
            mSubscriberContainer.removeView(mSubscriber.getView());
            mSubscriber = null;
            if (!mStreams.isEmpty()) {
                subscribeToStream(mStreams.get(0));
            }
        }
    }

    @Override
    public void onSessionCreatedConnection(Connection connection) {
        Log.i(TAG, "connection created: " + connection.getConnectionId());
    }

    @Override
    public void onSessionDroppedConnection(Connection connection) {
        Log.i(TAG, "connection dropped: " + connection.getConnectionId());
    }

    @Override
    public void onSessionException(OpentokException e) {
        Log.e(TAG, "session exception: " + e.getMessage());
    }

    @Override
    public void onPublisherStreamingStarted() {
        Log.i(TAG, "publisher is streaming.");
        mIsPublisherStreaming = true;
    }

    @Override
    public void onPublisherStreamingStopped() {
        Log.i(TAG, "publisher is not streaming.");
    }

    @Override
    public void onPublisherChangedCamera(int i) {
        Log.i(TAG, "publisher changed camera.");
    }

    @Override
    public void onPublisherException(OpentokException e) {
        Log.e(TAG, "publisher exception: " + e.getMessage());
    }

    @Override
    public void onSubscriberConnected(Subscriber subscriber) {
        Log.i(TAG, "subscriber connected.");
    }

    @Override
    public void onSubscriberVideoDisabled(Subscriber subscriber) {
        Log.i(TAG, "subscriber video disabled.");
    }

    @Override
    public void onSubscriberException(Subscriber subscriber, OpentokException e) {
        Log.e(TAG, "subscriber exception: " + e.getMessage());
    }

}