#!/bin/bash

# List of versions to build
VERSIONS=("1.21.4" "1.21.5" "1.21.6" "1.21.7" "1.21.8" "1.21.10" "1.21.11" "26.1")

# Ensure the output directory exists
mkdir -p build/all_versions

for MC_VERSION in "${VERSIONS[@]}"; do
    echo "=========================================="
    echo "Building for Minecraft $MC_VERSION..."
    echo "=========================================="

    # Handle yarn mappings
    YARN_BUILD="1"
    if [[ "$MC_VERSION" == "1.21.4" ]]; then YARN_BUILD="3"; fi
    
    # Determine Java version
    JAVA_VER="21"
    if [[ "$MC_VERSION" == "26.1" ]]; then
        JAVA_VER="25"
    fi

    # Run build
    ./gradlew clean build \
      -Pminecraft_version=$MC_VERSION \
      -Pyarn_mappings=$MC_VERSION+build.$YARN_BUILD \
      -Pjava_version=$JAVA_VER

    if [ $? -eq 0 ]; then
        echo "Build successful for $MC_VERSION!"
        # Copy the jar to a separate folder
        cp build/libs/mikutester-*.jar build/all_versions/mikutester-$MC_VERSION.jar
    else
        echo "Build FAILED for $MC_VERSION. Skipping..."
    fi
done

echo "=========================================="
echo "All builds finished! Check build/all_versions/ for the jars."
echo "=========================================="
