#!/bin/bash

# Configuration
RN_VERSION="0.77.0"
GROUP_ID_PATH="com/facebook/react/react-android"
PACKAGE_TO_REMOVE="com/facebook/react/views/scroll"
MAVEN_URL="https://repo1.maven.org/maven2/${GROUP_ID_PATH}/${RN_VERSION}"
echo "ℹ️  React Native version: ${MAVEN_URL}"

# Working directories
TEMP_DIR="patch_workspace"
OUTPUT_REPO="custom-rn-maven/${GROUP_ID_PATH}/${RN_VERSION}"

echo "🚀 Starting React Native ${RN_VERSION} patching process..."

# 1. Clean up old workspace
rm -rf $TEMP_DIR
mkdir -p $TEMP_DIR
mkdir -p $OUTPUT_REPO

# 2. Download artifacts from Maven Central
# The -f flag forces curl to fail immediately on a 404 Error
echo "⬇️  Downloading POM and Module metadata..."
curl -sSfL "${MAVEN_URL}/react-android-${RN_VERSION}.pom" -o "${OUTPUT_REPO}/react-android-${RN_VERSION}.pom" || { echo "❌ POM not found. Check RN_VERSION!"; exit 1; }
curl -sSfL "${MAVEN_URL}/react-android-${RN_VERSION}.module" -o "${OUTPUT_REPO}/react-android-${RN_VERSION}.module" || { echo "❌ Module metadata not found!"; exit 1; }

echo "⬇️  Downloading Debug and Release AARs..."
curl -sSfL "${MAVEN_URL}/react-android-${RN_VERSION}-debug.aar" -o "${TEMP_DIR}/react-android-${RN_VERSION}-debug.aar" || { echo "❌ Debug AAR not found!"; exit 1; }
curl -sSfL "${MAVEN_URL}/react-android-${RN_VERSION}-release.aar" -o "${TEMP_DIR}/react-android-${RN_VERSION}-release.aar" || { echo "❌ Release AAR not found!"; exit 1; }

# Helper function to unpack, patch, and repack an AAR safely
patch_aar() {
    local AAR_NAME=$1
    echo "📦 Patching ${AAR_NAME}..."
    
    # Run in a subshell so we don't break the main directory path
    (
        cd $TEMP_DIR
        unzip -q "${AAR_NAME}" -d extracted_aar
        rm "${AAR_NAME}"
        
        cd extracted_aar
        unzip -q classes.jar -d temp_classes
        rm classes.jar
        
        if [ -d "temp_classes/${PACKAGE_TO_REMOVE}" ]; then
            rm -rf "temp_classes/${PACKAGE_TO_REMOVE}"
            echo "   ✅ Removed ${PACKAGE_TO_REMOVE}"
        else
            echo "   ⚠️  Warning: Package not found in this AAR!"
        fi
        
        # cMf prevents jar from creating a new manifest and overriding the original React Native one
        cd temp_classes
        jar cMf ../classes.jar .
        cd ..
        rm -rf temp_classes
        
        jar cMf "../${AAR_NAME}" .
        cd ..
        rm -rf extracted_aar
    )
}

# 3. Patch both AARs
patch_aar "react-android-${RN_VERSION}-debug.aar"
patch_aar "react-android-${RN_VERSION}-release.aar"

# 4. Move patched AARs to the local Maven repo
echo "🚚 Moving patched artifacts to local Maven repository..."
mv "${TEMP_DIR}/react-android-${RN_VERSION}-debug.aar" "${OUTPUT_REPO}/"
mv "${TEMP_DIR}/react-android-${RN_VERSION}-release.aar" "${OUTPUT_REPO}/"

# # 5. Cleanup
# rm -rf $TEMP_DIR

echo "🎉 Done! Your patched Debug and Release artifacts are ready."
