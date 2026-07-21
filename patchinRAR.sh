#!/bin/bash

set -e

# Configuration
RN_VERSION="0.77.3"
PACKAGE_TO_REMOVE="com/facebook/react/views/scroll"

# Input/Output
INPUT_AAR="react-android-${RN_VERSION}-release.aar"
OUTPUT_AAR="react-android-${RN_VERSION}-release-patched.aar"

# Working directory
TEMP_DIR="patch_workspace"

echo "🚀 Patching ${INPUT_AAR}..."

# Check if the AAR exists
if [ ! -f "$INPUT_AAR" ]; then
    echo "❌ ${INPUT_AAR} not found in current directory."
    exit 1
fi

# Clean workspace
rm -rf "$TEMP_DIR"
mkdir -p "$TEMP_DIR"

# Copy the AAR into the workspace
cp "$INPUT_AAR" "$TEMP_DIR/"

(
    cd "$TEMP_DIR"

    echo "📦 Extracting AAR..."
    unzip -q "$INPUT_AAR" -d extracted_aar
    rm "$INPUT_AAR"

    cd extracted_aar

    echo "📦 Extracting classes.jar..."
    unzip -q classes.jar -d temp_classes
    rm classes.jar

    if [ -d "temp_classes/${PACKAGE_TO_REMOVE}" ]; then
        rm -rf "temp_classes/${PACKAGE_TO_REMOVE}"
        echo "✅ Removed ${PACKAGE_TO_REMOVE}"
    else
        echo "⚠️ Package not found."
    fi

    echo "📦 Repacking classes.jar..."
    cd temp_classes
    jar cMf ../classes.jar .
    cd ..

    rm -rf temp_classes

    echo "📦 Repacking AAR..."
    jar cMf "../${OUTPUT_AAR}" .

    cd ..
)

# Move patched AAR back to current directory
mv "${TEMP_DIR}/${OUTPUT_AAR}" .

# Cleanup
rm -rf "$TEMP_DIR"

echo "🎉 Done!"
echo "Patched AAR: ${OUTPUT_AAR}"
