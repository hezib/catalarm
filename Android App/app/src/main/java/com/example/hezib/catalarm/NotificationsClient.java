package com.example.hezib.catalarm;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.ibm.mobilefirstplatform.clientsdk.android.core.api.BMSClient;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPPush;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPPushException;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPPushNotificationButton;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPPushNotificationCategory;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPPushNotificationListener;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPPushNotificationOptions;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPPushNotificationStatus;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPPushNotificationStatusListener;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPPushResponseListener;
import com.ibm.mobilefirstplatform.clientsdk.android.push.api.MFPSimplePushNotification;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by hezib on 13/11/2017.
 */

public class NotificationsClient {

    private static final String TAG = NotificationsClient.class.getSimpleName();

    private MFPPush push;
    private MFPPushNotificationListener notificationListener;

    public interface NotificationsHandler {

        void connectToUv4l();
    }

    private MFPPushNotificationOptions getOptions() {
        MFPPushNotificationOptions options = new MFPPushNotificationOptions();
        MFPPushNotificationButton streamButton = new MFPPushNotificationButton.Builder("stream")
                .setIcon("check_circle_icon")
                .setLabel("Stream")
                .build();
        List<MFPPushNotificationButton> buttonGroup_1 =  new ArrayList<MFPPushNotificationButton>();
        buttonGroup_1.add(streamButton);
        MFPPushNotificationCategory category = new MFPPushNotificationCategory.Builder("stream").setButtons(buttonGroup_1).build();
        List<MFPPushNotificationCategory> categoryList =  new ArrayList<MFPPushNotificationCategory>();
        categoryList.add(category);
        options.setInteractiveNotificationCategories(categoryList);
        return options;
    }

    void init(NotificationsHandler events) {
        if (events instanceof Context) {
            Context context = (Context)events;
            BMSClient.getInstance().initialize(context, BMSClient.REGION_UK);
            push = MFPPush.getInstance();
            push.initialize(context, "<Your-App-Guid>", "<Your-Client-Secret>", getOptions());
        }

        // Create notification listener and enable pop up notification when a message is received
        notificationListener = new MFPPushNotificationListener() {
            @Override
            public void onReceive(final MFPSimplePushNotification message) {
                if(message == null) return;
                Log.i(TAG, "Received a Push Notification: " + message.toString());
                if(message.actionName == null) return;
                if(message.actionName.equals("stream")) {
                    events.connectToUv4l();
                }
            }
        };

        push.setNotificationStatusListener(new MFPPushNotificationStatusListener() {
            @Override
            public void onStatusChange(String messageId, MFPPushNotificationStatus status) {
                if(status.equals(MFPPushNotificationStatus.OPENED)) {
                    Log.d(TAG, "notification " + messageId + " has been opened");
                } else if(status.equals(MFPPushNotificationStatus.DISMISSED)) {
                    Log.d(TAG, "notification " + messageId + " has been dismissed");
                } else if(status.equals(MFPPushNotificationStatus.RECEIVED)) {
                    Log.d(TAG, "notification " + messageId + " has been received");
                } else if(status.equals(MFPPushNotificationStatus.QUEUED)) {
                    Log.d(TAG, "notification " + messageId + " has been queued");
                }
            }
        });

        MFPPushResponseListener<String> registrationResponselistener = new MFPPushResponseListener<String>() {
            @Override
            public void onSuccess(String response) {
                Log.i(TAG, "Successfully registered for push notifications, " + response);
                // Start listening to notification listener now that registration has succeeded
                push.listen(notificationListener);
            }

            @Override
            public void onFailure(MFPPushException exception) {
                String errLog = "Error registering for push notifications: ";
                String errMessage = exception.getErrorMessage();
                int statusCode = exception.getStatusCode();

                // Set error log based on response code and error message
                if (statusCode == 401) {
                    errLog += "Cannot authenticate successfully with Bluemix Push instance, ensure your CLIENT SECRET was set correctly.";
                } else if (statusCode == 404 && errMessage.contains("Push GCM Configuration")) {
                    errLog += "Push GCM Configuration does not exist, ensure you have configured GCM Push credentials on your Bluemix Push dashboard correctly.";
                } else if (statusCode == 404 && errMessage.contains("PushApplication")) {
                    errLog += "Cannot find Bluemix Push instance, ensure your APPLICATION ID was set correctly and your phone can successfully connect to the internet.";
                } else if (statusCode >= 500) {
                    errLog += "Bluemix and/or your Push instance seem to be having problems, please try again later.";
                }

                Log.d(TAG, errLog);
                // make push null since registration failed
                push = null;
            }
        };

        // Attempt to register device using response listener created above
        // Include unique sample user Id instead of Sample UserId in order to send targeted push notifications to specific users
        push.registerDevice(registrationResponselistener);

    }

    void hold() {
        if (push != null) {
            Log.d(TAG, "Notifications in hold");
            push.hold();
        }
    }

    void resume() {
        if (push != null) {
            Log.d(TAG, "listening for Notifications");
            push.listen(notificationListener);
        }
    }
}
