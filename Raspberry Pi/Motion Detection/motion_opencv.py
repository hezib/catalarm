from setproctitle import setproctitle
setproctitle("motion")

import logging
logging.basicConfig(format='%(asctime)s %(name)s %(levelname)s %(message)s', level=logging.INFO)
log = logging.getLogger('CatAlarm')
import datetime
import argparse
import time
import os

from picamera.array import PiRGBArray
from picamera import PiCamera
import cv2
import sendhttp


def start(args):
    with PiCamera() as camera:
        camera.resolution = args.resolution
        camera.framerate = args.fps
        log.info("Warming up camera")
        time.sleep(5)

        loop(args, camera)


def loop(args, camera):
    avg = None
    raw_capture = PiRGBArray(camera, size=args.resolution)
    log.info("Starting capture")

    for f in camera.capture_continuous(raw_capture, format="bgr", use_video_port=True):
        frame = f.array
        sleep_between_frames = args.idle_sleep
        motion = False
        log.info("loop")
        
        # grayscale & blur out noise
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        gray = cv2.GaussianBlur(gray, (21, 21), 0)

        # if the average frame is None, initialize it
        if avg is None:
            log.info("Initialising average frame")
            avg = gray.astype(float)
            raw_capture.truncate(0)
            continue

        # accumulate the weighted average between the current frame and
        # previous frames, then compute the difference between the current
        # frame and running average
        cv2.accumulateWeighted(gray, avg, 0.5)
        frame_delta = cv2.absdiff(gray, cv2.convertScaleAbs(avg))

        # threshold the delta image, dilate the thresholded image 
        # then find contours on thresholded image
        thresh = cv2.threshold(frame_delta, args.delta_threshold, 255, cv2.THRESH_BINARY)[1]
        thresh = cv2.dilate(thresh, None, iterations=2)
        (contours, _) = cv2.findContours(thresh.copy(), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        
        for c in contours:
            # if the contour is too small, ignore it
            if cv2.contourArea(c) < args.min_area:
                continue

            motion = True
            log.info("Motion detected")
            break

        if motion:
            sleep_between_frames = args.active_sleep
            timestamp = datetime.datetime.utcnow().strftime('%Y-%m-%d-%H_%M_%S.%f')
            r, buffer = cv2.imencode(".jpg", frame)
            if (r == True):
                sendhttp.send(buffer, timestamp, args.alarm)
            else:
                raise cv2.error
            
        raw_capture.truncate(0)
        time.sleep(float(sleep_between_frames))


def parse_res(v):
    x, y = v.lower().split('x')
    return int(x), int(y)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Motion detection')
    parser.add_argument('--resolution', help='e.g 640x480', default=parse_res(os.environ.get('resolution', '640x480')))
    parser.add_argument('--fps', help='Framerate e.g: 18', default=int(os.environ.get('fps', '18')))
    parser.add_argument('--delta-threshold', default=int(os.environ.get('delta_threshold', 5)))
    parser.add_argument('--min-area', default=int(os.environ.get('min_area', 5000)))
    parser.add_argument('--idle-sleep',default=float(os.environ.get('idle_sleep', 3)))
    parser.add_argument('--active-sleep',default=float(os.environ.get('active_sleep', 0.5)))
    parser.add_argument('--alarm', default=os.environ.get('alarm', 'alarm1.mp3'))

    args = parser.parse_args()
    log.debug(args)
    start(args)