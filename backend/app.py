from flask import Flask, request, jsonify
from flask_cors import CORS
import json
import os

app = Flask(__name__)
CORS(app)

DATA_FILE = "data.json"

def load_data():
    if not os.path.exists(DATA_FILE):
        return {}
    try:
        with open(DATA_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    except:
        return {}

def save_data(data):
    with open(DATA_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False)

@app.route("/", methods=["GET"])
def index():
    return "Wish Backend is running!"

@app.route("/colors", methods=["GET"])
def get_colors():
    return jsonify(load_data())

@app.route("/colors", methods=["PUT"])
def update_colors():
    try:
        data = request.get_json()
        if data is None:
            return jsonify({"error": "Invalid JSON"}), 400
        
        if "uuid" in data and "data" in data and len(data) <= 3:
            uuid = data["uuid"]
            user_data = data["data"]
            username = user_data.get("username", "").lower()
            
            db = load_data()
            db[uuid] = user_data
   
            if username:
                db[username] = user_data
            save_data(db)
        else:
            return jsonify({"error": "Update your mod to the latest version for security reasons."}), 400

        return jsonify({"status": "success"})
    except Exception as e:
        return jsonify({"error": str(e)}), 500

if __name__ == "__main__":
    # Ensure data file exists
    if not os.path.exists(DATA_FILE):
        save_data({})
    app.run(host="0.0.0.0", port=5000)
