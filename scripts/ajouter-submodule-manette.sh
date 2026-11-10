#!/usr/bin/env bash
set -euo pipefail

url=${1:?"usage : $0 https://github.com/axone91/Manette [branche, défaut : master]"}
branch=${2:-master}
dest=third_party/Manette

cd "$(git rev-parse --show-toplevel)"
if [ -e "$dest" ]; then
    echo "$dest existe déjà : rien à faire (git submodule update --init pour le remplir)." >&2
    exit 1
fi

git submodule add -b "$branch" "$url" "$dest"
echo
echo "Submodule ajouté sur la branche '$branch'. :"
echo "Après un clone : git clone --recurse-submodules …  ou  git submodule update --init"
echo "Mettre à jour  : git submodule update --remote $dest"
