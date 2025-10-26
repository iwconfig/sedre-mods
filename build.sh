#!/usr/bin/env bash
set -e # Exit immediately if a command exits with a non-zero status.

source build.conf

# Check if JAVAC is set and executable
if [ ! -x "$JAVAC" ]; then
    echo "ERROR: Java compiler not found or not executable at '$JAVAC'."
    echo "Please edit the JAVAC variable in this script."
    exit 1
fi

# Helper function to join array elements with a ':' for the classpath
join_by() { local IFS=":"; echo "$*"; }

# --- BUILD LOGIC ---
echo "Starting build process..."

# Loop through each configuration file in the 'patches' directory
for patch_config in build.conf.d/*.conf; do
    echo "--------------------------------------------------------"
    echo "Processing patch configuration: $patch_config"

    # Source the configuration file to get TARGET_JAR and JAVA_SOURCES
    source "$patch_config"

    # Prepare for a clean build
    rm -rf build
    mkdir build
    
    # Prepend 'src/' to each source file path
    full_source_paths=()
    for src in "${JAVA_SOURCES[@]}"; do
        full_source_paths+=("src/$src")
    done

    echo "Compiling sources..."
    set -x # Turn on command echoing for the compile step

    # Compile the specified source files into the 'build' directory
    "$JAVAC" \
      -d build \
      -source 1.4 \
      -target 1.4 \
      -cp "$(join_by "${CLASSPATH[@]}")" \
      "${full_source_paths[@]}"
    
    set +x # Turn off command echoing

    echo "Packaging JAR: $TARGET_JAR"
    # Create the JAR from the contents of the 'build' directory
    (cd build && jar -cf "../$TARGET_JAR" .)

    echo "Verifying contents of $TARGET_JAR:"
    jar -tf "$TARGET_JAR"

    # Clean up for the next patch
    rm -rf build
    echo "Successfully created $TARGET_JAR"
done

echo "--------------------------------------------------------"
echo "All patches built successfully!"
echo "--------------------------------------------------------"
