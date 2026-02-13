#!/bin/bash

# Define JDK version and installation path
JDK_VERSION="21"
JDK_DIR="/root/jdks"
JDK_NAME="jdk-21"
JDK_ARCHIVE="OpenJDK21U-jdk_x64_linux_hotspot_21.0.6_7.tar.gz"
JDK_URL="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.6%2B7/OpenJDK21U-jdk_x64_linux_hotspot_21.0.6_7.tar.gz"

# Ensure JDK directory exists
mkdir -p "$JDK_DIR"

# Check if JDK is already installed
if [ ! -d "$JDK_DIR/$JDK_NAME" ]; then
    echo "JDK $JDK_VERSION not found. Downloading..."
    
    # Download JDK
    if [ ! -f "$JDK_DIR/$JDK_ARCHIVE" ]; then
        wget -O "$JDK_DIR/$JDK_ARCHIVE" "$JDK_URL"
        if [ $? -ne 0 ]; then
            echo "Failed to download JDK. Please check your network connection."
            exit 1
        fi
    fi
    
    echo "Extracting JDK..."
    tar -xzf "$JDK_DIR/$JDK_ARCHIVE" -C "$JDK_DIR"
    
    # Identify the extracted directory name (it might vary)
    EXTRACTED_DIR=$(ls -d "$JDK_DIR"/jdk-21* | head -n 1)
    if [ -d "$EXTRACTED_DIR" ]; then
        mv "$EXTRACTED_DIR" "$JDK_DIR/$JDK_NAME"
    else
        echo "Failed to extract JDK correctly."
        exit 1
    fi
    
    # Clean up archive
    rm "$JDK_DIR/$JDK_ARCHIVE"
    echo "JDK installed successfully to $JDK_DIR/$JDK_NAME"
else
    echo "JDK $JDK_VERSION found at $JDK_DIR/$JDK_NAME"
fi

# Set environment variables for the build session
export JAVA_HOME="$JDK_DIR/$JDK_NAME"
export PATH="$JAVA_HOME/bin:$PATH"

echo "Using Java version:"
java -version

# Run Maven build (skip tests)
echo "Building project with Maven..."
cd /root/yusi
./mvnw clean package -DskipTests -s settings.xml

if [ $? -ne 0 ]; then
    echo "Maven build failed."
    exit 1
fi

echo "Maven build successful. Starting Docker build..."

# Run Docker build
docker compose build --no-cache yusi

if [ $? -ne 0 ]; then
    echo "Docker build failed."
    exit 1
fi

echo "Docker build successful. Restarting container..."
docker compose up -d yusi

echo "Done."
