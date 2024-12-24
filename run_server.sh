#!/bin/bash

# Create and activate virtual environment
python3 -m venv venv
source venv/bin/activate

# Install required packages
pip install fastapi uvicorn python-multipart

# Start the FastAPI server in the background
nohup uvicorn server:app --host 0.0.0.0 --port 9000 > server.log 2>&1 &

# Start ngrok in the background
nohup ngrok http --hostname=pro-physically-squirrel.ngrok-free.app 9000 > ngrok.log 2>&1 &

# Print success message
echo "Server started successfully!"
echo "Server logs: server.log"
echo "Ngrok logs: ngrok.log"
echo "To stop the server, use: pkill -f uvicorn"
echo "To stop ngrok, use: pkill -f ngrok"
