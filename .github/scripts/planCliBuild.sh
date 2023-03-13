#!/usr/bin/env bash

# Copyright 2022-2023 Crown Copyright
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# This code is adapted from GitHub Docs, to publish a 'latest' tag built from the main branch,
# and a tag for each Git tagged version starting with 'v'. See here:
# https://docs.github.com/en/packages/managing-github-packages-using-github-actions-workflows/publishing-and-installing-a-package-with-github-actions#upgrading-a-workflow-that-accesses-a-registry-using-a-personal-access-token

REPO_OWNER=$1
GIT_REF=$2

export_github_env_for_image() {
  IMAGE_NAME=$1
  ENV_PREFIX=$2
  IMAGE_ID="ghcr.io/$REPO_OWNER/$IMAGE_NAME"

  # Change all uppercase to lowercase
  IMAGE_ID=$(echo "$IMAGE_ID" | tr '[A-Z]' '[a-z]')

  {
    echo "${ENV_PREFIX}_IMAGE_ID=$IMAGE_ID"
    echo "${ENV_PREFIX}_IMAGE_NAME=$IMAGE_NAME"
  } >> "$GITHUB_ENV"
}

export_common_github_env() {
  IMAGE_NAMES=$1

  # Strip git ref prefix from version
  VERSION=$(echo "$GIT_REF" | sed -e 's,.*/\(.*\),\1,')
  # Strip "v" prefix from tag name
  [[ "$GIT_REF" == "refs/tags/"* ]] && VERSION=$(echo "$VERSION" | sed -e 's/^v//')
  # Use Docker `latest` tag convention
  [ "$VERSION" == "main" ] && VERSION=latest

  {
    echo "GHCR_PACKAGE_NAMES=$IMAGE_NAMES"
    echo "VERSION=$VERSION"
  } >> "$GITHUB_ENV"
}

export_github_env_for_image sleeper-local ENV
export_github_env_for_image sleeper-deployment DEPLOY
export_github_env_for_image sleeper-builder BUILDER
export_common_github_env "sleeper-local,sleeper-deployment,sleeper-builder"