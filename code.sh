#!/bin/bash

# --- Configuration ---
BASE_URL="https://pro-physically-squirrel.ngrok-free.app"
LIST_API_PATH="/list-processed-files/"
DOWNLOAD_API_PATH="/download-file/"
# Optional: Create a directory to download files into
DOWNLOAD_DIR="./downloaded_files"

# --- Script Logic ---

# Exit on error and ensure pipeline errors are caught
set -e
set -o pipefail

# Check if jq is installed
if ! command -v jq &> /dev/null
then
    echo "jq could not be found. Please install jq to parse the JSON response."
    echo "For example, on Debian/Ubuntu: sudo apt-get install jq"
    echo "On macOS (using Homebrew): brew install jq"
    exit 1
fi

# Create download directory if it doesn't exist
mkdir -p "$DOWNLOAD_DIR"
cd "$DOWNLOAD_DIR"
echo "Downloading files into: $(pwd)"
echo "-------------------------------------"

echo "Fetching list of files from ${BASE_URL}${LIST_API_PATH}..."

# Get the list of files and parse JSON with jq.
# -s flag makes curl silent. -L follows redirects.
# jq -r '.processed_files[]' extracts each filename from the "processed_files" array.
curl -sL "${BASE_URL}${LIST_API_PATH}" | jq -r '.processed_files[]' | while IFS= read -r filename; do
    # Skip empty lines that might result from jq or processing (though unlikely here)
    if [ -z "$filename" ]; then
        continue
    fi

    # Trim potential leading/trailing whitespace (jq -r usually handles this but it's safe)
    filename=$(echo "$filename" | xargs)

    if [ -z "$filename" ]; then # Check again after trimming
        continue
    fi

    echo "Attempting to download: ${filename}"

    # Download the file using -O to save with the remote filename
    # -f makes curl fail silently on server errors (HTTP 4xx, 5xx) and return an error code
    if curl -sL -f -O "${BASE_URL}${DOWNLOAD_API_PATH}${filename}"; then
        echo "Successfully downloaded: ${filename}"
    else
        # curl -f returns 22 for 404s and other HTTP errors.
        echo "ERROR: Failed to download ${filename}. HTTP error or file not found."
        # If you want the script to continue even if one file fails,
        # remove `set -e` at the top and uncomment the next line:
        # continue
    fi
    echo "---"
done

echo "-------------------------------------"
echo "All download attempts finished."
echo "Files are in: $(pwd)"

# Go back to the original directory
cd ..