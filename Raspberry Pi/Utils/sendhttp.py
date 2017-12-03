import requests
import base64


def send(image_raw, timestamp, alarm):
    url = 'http://localhost:1880/motion'
    image_enc = base64.b64encode(image_raw)
    requests.post(url=url, json={'pic':image_enc, 'timestamp':timestamp, 'alarm':alarm})
    

