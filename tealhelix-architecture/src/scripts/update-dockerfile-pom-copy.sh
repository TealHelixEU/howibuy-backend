#!/usr/bin/env bash
set -euo pipefail

DOCKERFILE_PATH="${1:-Dockerfile}"
BEGIN_MARKER="# BEGIN auto-pom-copy"
END_MARKER="# END auto-pom-copy"

if [[ ! -f "$DOCKERFILE_PATH" ]]; then
	echo "Dockerfile not found: $DOCKERFILE_PATH" >&2
	exit 1
fi

if ! grep -q "^${BEGIN_MARKER}$" "$DOCKERFILE_PATH"; then
	echo "Missing marker: ${BEGIN_MARKER}" >&2
	exit 1
fi

if ! grep -q "^${END_MARKER}$" "$DOCKERFILE_PATH"; then
	echo "Missing marker: ${END_MARKER}" >&2
	exit 1
fi

tmp_block="$(mktemp)"
tmp_out="$(mktemp)"
trap 'rm -f "$tmp_block" "$tmp_out"' EXIT

{
	echo "$BEGIN_MARKER"
	while IFS= read -r pom; do
		echo "COPY ${pom} ${pom}"
	done < <(find . -type f -name 'pom.xml' -not -path '*/target/*' | sed 's#^\./##' | sort)
	echo "$END_MARKER"
} > "$tmp_block"

awk -v block_file="$tmp_block" -v begin="$BEGIN_MARKER" -v end="$END_MARKER" '
	BEGIN {
		in_block = 0
		while ((getline line < block_file) > 0) {
			block = block line "\n"
		}
		close(block_file)
	}
	$0 == begin {
		printf "%s", block
		in_block = 1
		next
	}
	$0 == end {
		in_block = 0
		next
	}
	!in_block {
		print
	}
' "$DOCKERFILE_PATH" > "$tmp_out"

mv "$tmp_out" "$DOCKERFILE_PATH"
echo "Updated auto-generated pom copy block in ${DOCKERFILE_PATH}"
