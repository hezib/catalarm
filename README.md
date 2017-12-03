# Cat Alarm

Cat Alarm is an IoT solution that helps you keep your cat on ground level, it consists of three main parts:
1. Raspberry Pi with camera kit
2. IBM Bluemix
3. Android App

# Raspberry Pi
While the camera is aimed at the countertop, a motion detection service runs in the background and sends pictures to Bluemix through NodeRED when motion is detected. When Bluemix recognizes a cat in the picture, Raspberry Pi plays an alarm.
UV4L also runs in the background and waits for WebRTC calls from the android app.

# IBM Bluemix
Receives through NodeRED the pictures sent by raspberry pi and inspects them. uses Watson Visual Recognition to identify cats. when a cat is identified, raspberry pi is notified, a push notification is sent to the android app and the picture is saved to cloudant database.

# Android App
Displays statstics regarding your cat activity on the countertop, initiates a WebRTC call to raspberry pi, makes queries to cloudant database and much more. built on top of Google's AppRTC.

# Prerequisites
on the raspberry pi:
- picamera
- node-red (see https://nodered.org/)
- uv4l (see https://www.linux-projects.org/uv4l/)
- python 2 with: setproctitle, psutil and playsound libraries

on Bluemix - an account with the following services:
- Internet of Things Platform Starter boilerplate (IoT Platform + Node.js sdk + Cloudant NoSQL)
- Push notifications
- Watson Visual Recognition

on Android Studio, the dependecies are declared in app/build.gradle

# Replacing placeholders
1. on Raspberry Pi/NodeRED/client-no-cred.txt you should use your credentials from Bluemix IoT Platform on the ibmin/ibmout nodes.
2. on Bluemix/NodeRED/cloud-no-cred.txt you should use your AppSecret and AppGuid from your Bluemix Push Notifications instance.
3. on Android App/app/src/main/java/com/example/hezib/catalarm/NotificationsClient.java you should use your AppGuid and ClientSecret from your Bluemix Push Notifications instance.
4. on Android App/app/src/main/java/com/example/hezib/catalarm/utils/CloudantQueries.java you should use your cloudant credentials, base 64 encoded.
5. on Android App/app/google-services.json replace the file with yours (see https://console.bluemix.net/docs/services/mobilepush/push_step_1.html#push_step_1)
