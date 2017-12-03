import argparse
import os
import logging
logging.basicConfig(format='%(asctime)s %(name)s %(levelname)s %(message)s', level=logging.INFO)
log = logging.getLogger('CatAlarm')
from playsound import playsound

def start(args):
    path = '/home/pi/CatAlarm/Alarms/'
    playsound(path + args.alarm + '.mp3')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Play Alarm')
    parser.add_argument('--alarm', default=os.environ.get('alarm', 'airhorn'))

    args = parser.parse_args()
    log.debug(args)
    start(args)