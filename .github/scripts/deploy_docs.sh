#!/usr/bin/env bash

set -e  # Exit if any command fails.

# There is no point in doing this if we lack the SSH key to publish the guide.
if [ "$GUIDE_SSH_KEY" != "" ]; then

    # Publish the JavaDoc to the right place on the Deep Symmetry web server.
    if [ "$release_snapshot" != "true" ]; then
        rsync -avz target/apidocs guides@deepsymmetry.org:/var/www/html/cratedigger
    else
        rsync -avz target/apidocs guides@deepsymmetry.org:/var/www/html/cratedigger/snapshot
    fi
else
    echo "No SSH key present, not building user guide."
fi
