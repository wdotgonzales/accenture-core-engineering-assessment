#!/usr/bin/env bash

# Exit immediately if a command exits with a non-zero status
set -e

TEST_DIR="testscan_dir"

echo "Creating test directory structure in ./${TEST_DIR}..."

# Clean up old test directory if it exists
if [ -d "$TEST_DIR" ]; then
    rm -rf "$TEST_DIR"
fi

# Create main folder and subfolders
mkdir -p "${TEST_DIR}/subdir"
mkdir -p "${TEST_DIR}/nested/deep"
mkdir -p "${TEST_DIR}/.hidden_folder"

# Create sample text files with matches and non-matches
echo "Hello world, Hello again from the root file." > "${TEST_DIR}/file1.txt"
echo "Just some random text here." > "${TEST_DIR}/subdir/file2.txt"
echo "Say Hello to the subfolder! Hello once more." >> "${TEST_DIR}/subdir/file2.txt"
echo "Deep down inside, we say Hello." > "${TEST_DIR}/nested/deep/file3.txt"

# Create non-txt files and hidden items to test filters
echo "This is a log file, should be ignored." > "${TEST_DIR}/app.log"
echo "This is a hidden file, should be ignored." > "${TEST_DIR}/.hidden.txt"
echo "Hidden folder file, should be ignored." > "${TEST_DIR}/.hidden_folder/secret.txt"

echo "Test environment successfully created inside '${TEST_DIR}'"
