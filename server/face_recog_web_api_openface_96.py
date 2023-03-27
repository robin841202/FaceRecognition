from flask import Flask, jsonify, request
from keras.models import load_model
from collections import Counter
import base64
import os
import json
import numpy as np
import cv2
import tensorflow as tf
import logging
import glob
#import MySQLdb
from datetime import datetime

img_size = 96
#face_detector = cv2.CascadeClassifier('packages/haarcascade_frontalface_default.xml')
#conn=MySQLdb.connect(host="127.0.0.1",user="e508", passwd="f107118109", db="identity_recog", charset="utf8", port=3306)

app = Flask(__name__)

def loadModel():
    print('loading model...')
    global face_recog_model
    face_recog_model = load_model("models/face_recog.h5", custom_objects={'tf': tf})
    face_recog_model._make_predict_function() #have to initialize before threading, let predict more faster
    print('loaded complete.')


def image_to_embedding(image, model):
    image = cv2.resize(image, (img_size, img_size), interpolation=cv2.INTER_AREA) 
    img = image[...,::-1]
    img = np.around(np.transpose(img, (0,1,2))/255.0, decimals=12)
    x_train = np.array([img])
    embedding = model.predict_on_batch(x_train)
    return embedding


def recognize_face(face_image, input_embeddings, model):

    embedding = image_to_embedding(face_image, model)
    minimum_distance = 200
    name = None

    # Loop over  names and encodings.
    for (input_name, input_embedding) in input_embeddings.items():
        euclidean_distance = np.linalg.norm(embedding-input_embedding)
        print('Euclidean distance from %s is %s' %(input_name, euclidean_distance))
        if euclidean_distance < minimum_distance:
            minimum_distance = euclidean_distance
            name = input_name
    print('-----------------------------------------')
    if minimum_distance < 0.7:
        return str(name)
    else:
        return "Unknown"

def create_input_image_embeddings():
    input_embeddings = {}

    for file in glob.glob("images/*"):
        person_name = os.path.splitext(os.path.basename(file))[0]
        image_file = cv2.imread(file, 1)
        input_embeddings[person_name] = image_to_embedding(image_file, face_recog_model)

    return input_embeddings




@app.route("/api/registered", methods=['POST'])
def registered():
    print("registering...")
    request_data = request.json
    img_data = request_data['img']
    name = request_data['name']
    if img_data != None:
        b64decodedImage = base64.b64decode(img_data)
        nparr = np.fromstring(b64decodedImage, dtype=np.uint8)
        image = cv2.imdecode(nparr,cv2.IMREAD_COLOR)
        cv2.imwrite("images/" + name + ".jpg", image)
        response = {"status": "Registered successful."}
    else:
        response = {"status": "Error: No img_data received."}

    return jsonify(response)


@app.route("/api/recognize", methods=['POST'])
def recognize():
    #cursor=conn.cursor()
    print("recognizing...")
    request_data = request.json
    #print(request_data)
    faces_data = request_data['faces']
    result_identity = []
    print(len(faces_data))
    if faces_data != None:
        input_embeddings = create_input_image_embeddings()
        for face_data in faces_data:
            b64decodedImage = base64.b64decode(face_data)
            nparr = np.fromstring(b64decodedImage, dtype=np.uint8)
            face_image = cv2.imdecode(nparr,cv2.IMREAD_COLOR)
            identity = recognize_face(face_image, input_embeddings, face_recog_model)
            result_identity.append(identity)
        print("Result_identity: {0}".format(result_identity))
        most_identity, _ = Counter(result_identity).most_common(1)[0]
        
        #Save Records to MySQL Database
        '''
        try:
            dtime = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
            MYSQL = "INSERT INTO records(img,identity,time_record) VALUES(%s,%s,%s)"
            cursor.execute(MYSQL,(b64decodedImage, most_identity, dtime))  #insert customer data to mysql
            conn.commit()
            logging.info('inserted values identity: %d', most_identity)
        except MySQLdb.Error:
            logging.warn('failed to insert values')
            conn.rollback()
        finally:
            cursor.close()
        '''

        response = {"identity": most_identity}
    else:
        response = {"identity": None}

    print("recognize completed. Most_common Identity: {0}".format(most_identity))
    return jsonify(response) 


@app.route("/api/recognize_mp", methods=['POST'])
def recognize_multiple_people():
    #cursor=conn.cursor()
    print("recognizing...")
    request_data = request.json
    #print(request_data)
    faces_data = request_data['faces']
    result_identity = []
    print(len(faces_data))
    if faces_data != None:
        input_embeddings = create_input_image_embeddings()
        for face_data in faces_data:
            b64decodedImage = base64.b64decode(face_data)
            nparr = np.fromstring(b64decodedImage, dtype=np.uint8)
            face_image = cv2.imdecode(nparr,cv2.IMREAD_COLOR)
            identity = recognize_face(face_image, input_embeddings, face_recog_model)
            result_identity.append(identity)
        print("Result_identity: {0}".format(result_identity))
        #most_identity, _ = Counter(result_identity).most_common(1)[0]
        
        #Save Records to MySQL Database
        '''
        try:
            dtime = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
            MYSQL = "INSERT INTO records(img,identity,time_record) VALUES(%s,%s,%s)"
            cursor.execute(MYSQL,(b64decodedImage, most_identity, dtime))  #insert customer data to mysql
            conn.commit()
            logging.info('inserted values identity: %d', most_identity)
        except MySQLdb.Error:
            logging.warn('failed to insert values')
            conn.rollback()
        finally:
            cursor.close()
        '''

        response = {"identity": result_identity}
    else:
        response = {"identity": None}

    #print("recognize completed. Most_common Identity: {0}".format(most_identity))
    return jsonify(response) 



if __name__ == "__main__":
    loadModel()
    app.run(host='0.0.0.0', port=5000)
