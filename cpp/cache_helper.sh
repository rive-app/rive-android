#!/bin/sh

set -e

# required envs
RIVE_CPP_DIR="${RIVE_CPP_DIR:=../..}"
SKIA_DIR="${SKIA_DIR:=skia}"
SKIA_REPO=${SKIA_REPO:-https://github.com/rive-app/skia}
SKIA_BRANCH=${SKIA_BRANCH:-rive}
COMPILE_TARGET="${COMPILE_TARGET:-$(uname -s)_$(uname -m)}"
CACHE_NAME="${CACHE_NAME:=skia}"
OUTPUT_CACHE="${OUTPUT_CACHE:=out}"

# lets just make sure this exists, or fail
if [[ ! -d $RIVE_CPP_DIR ]]
then
    echo "Cannot find $RIVE_CPP_DIR, bad setup"
    exit 1
fi

# determeined envs
SKIA_DEPENDENCIES_DIR="$RIVE_CPP_DIR/skia/dependencies"
SKIA_RECORDER_DIR="$SKIA_DEPENDENCIES_DIR/$SKIA_DIR"

SKIA_COMMIT_HASH="$(git ls-remote $SKIA_REPO $SKIA_BRANCH | awk '{print $1}')"

ARCHIVE_CONTENTS_NAME=archive_contents
ARCHIVE_CONTENTS_PATH="$SKIA_RECORDER_DIR/$ARCHIVE_CONTENTS_NAME"
echo $ARCHIVE_CONTENTS_PATH

ARCHIVE_CONTENTS="missing"
if test -f "$ARCHIVE_CONTENTS_PATH"; then
    ARCHIVE_CONTENTS="$(cat $ARCHIVE_CONTENTS_PATH)"
fi

# is OS_RELEASE too much?
if [[ $OSTYPE == 'darwin'* ]]; then
    # md5 -r == md5sum
    CONFIGURE_VERSION=$(md5 -r configure_skia.sh|awk '{print $1}')
    MAKE_SKIA_HASH=$(md5 -r $SKIA_DEPENDENCIES_DIR/make_skia_recorder.sh|awk '{print $1}')
    BUILD_HASH=$(md5 -r -s "$SKIA_COMMIT_HASH $MAKE_SKIA_HASH $CONFIGURE_VERSION" | awk '{print $1}')
else 
    CONFIGURE_VERSION=$(md5sum configure_skia|awk '{print $1}')
    MAKE_SKIA_HASH=$(md5sum $SKIA_DEPENDENCIES_DIR/make_skia_recorder.sh|awk '{print $1}')
    BUILD_HASH=$(echo "$SKIA_COMMIT_HASH $MAKE_SKIA_HASH $CONFIGURE_VERSION" | md5sum | awk '{print $1}')
fi

echo "Created hash: $BUILD_HASH from skia_commit=$SKIA_COMMIT_HASH make_skia_script=$MAKE_SKIA_HASH configure_script=$CONFIGURE_VERSION"

EXPECTED_ARCHIVE_CONTENTS="$BUILD_HASH"_"$COMPILE_TARGET"

ARCHIVE_FILE_NAME="$CACHE_NAME"_"$EXPECTED_ARCHIVE_CONTENTS.tar.gz"
ARCHIVE_URL="https://cdn.2dimensions.com/archives/$ARCHIVE_FILE_NAME"
ARCHIVE_PATH="$SKIA_RECORDER_DIR/$ARCHIVE_FILE_NAME"

pull_cache() {
    echo "Grabbing cached build from $ARCHIVE_URL"
    pwd
    curl --output $SKIA_RECORDER_DIR/$ARCHIVE_FILE_NAME $ARCHIVE_URL 
    pushd $SKIA_RECORDER_DIR
    tar -xf $ARCHIVE_FILE_NAME out archive_contents third_party
}

is_build_cached_remotely() {
    echo "Checking for cache build $ARCHIVE_URL"
    if curl --output /dev/null --head --silent --fail $ARCHIVE_URL
    then 
        return 0
    else 
        return 1
    fi
}

upload_cache() {
    pushd ./$SKIA_DEPENDENCIES_DIR

    echo $EXPECTED_ARCHIVE_CONTENTS > $SKIA_DIR/$ARCHIVE_CONTENTS_NAME
    # not really sure about this third party biz
    # also we are caching on a per architecture path here, but out could contain more :thinking:
    tar -C $SKIA_DIR -cf $SKIA_DIR/$ARCHIVE_FILE_NAME $OUTPUT_CACHE $ARCHIVE_CONTENTS_NAME third_party/libpng third_party/externals/libpng

    popd

    # # if we're configured to upload the archive back into our cache, lets do it! 
    echo "Uploading to s3://2d-public/archives/$ARCHIVE_FILE_NAME"
    ls $ARCHIVE_PATH
    aws s3 cp $ARCHIVE_PATH s3://2d-public/archives/$ARCHIVE_FILE_NAME
}



is_build_cached_locally() {
    if [ "$EXPECTED_ARCHIVE_CONTENTS" == "$ARCHIVE_CONTENTS" ]; then 
        return 0
    else
        return 1
    fi
}

echo "wat"