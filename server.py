from fastapi import FastAPI, File, UploadFile
from fastapi.responses import JSONResponse
import os

app = FastAPI(
    title="File Upload API with FastAPI",
    description="This API accepts raw data files uploaded from an iOS app and stores them on the server.",
    version="1.0.0",
)

# Directory to save uploaded files
UPLOAD_DIRECTORY = "./uploaded_files"
os.makedirs(UPLOAD_DIRECTORY, exist_ok=True)

@app.post(
    "/upload-file/",
    summary="Upload a File",
    description="Upload a raw data file from your iOS app. The file is stored on the server.",
    tags=["File Upload"]
)
async def upload_file(file: UploadFile = File(...)):
    """
    Endpoint to handle file uploads.
    - Accepts a file from the request.
    - Stores the file on the server in the `uploaded_files` directory.
    """
    file_path = os.path.join(UPLOAD_DIRECTORY, file.filename)
    
    with open(file_path, "wb") as f:
        content = await file.read()
        f.write(content)

    return JSONResponse(
        content={"message": "File uploaded successfully!", "file_name": file.filename},
        status_code=200
    )

@app.get(
    "/server-info/",
    summary="Get Server Info",
    description="Provides the local server URL and the public ngrok URL for access.",
    tags=["Server Info"]
)
async def server_info():
    """
    Retrieve server information.
    - Returns the local server URL.
    - Returns the ngrok public URL for external access.
    """
    ngrok_url = "https://pro-physically-squirrel.ngrok-free.app"
    return {
        "local_url": "http://127.0.0.1:5000",
        "ngrok_url": ngrok_url,
        "message": "Server is running and accessible via ngrok.",
    }

# Run the server : python3 server.py
# ngrok http --hostname=pro-physically-squirrel.ngrok-free.app 9000


if __name__ == "__main__":
    import uvicorn
    # Host the app on all interfaces to allow ngrok access
    uvicorn.run(app, host="0.0.0.0", port=9000)



