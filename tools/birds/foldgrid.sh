#!/bin/bash
# foldgrid.sh bird 'json1' 'json2' ...
b=$1; shift; n=0
for j in "$@"; do python3 scripts/foldtest.py $n $b "$j" >/dev/null 2>&1; n=$((n+1)); done
python3 - $b $n <<'P'
import sys
from PIL import Image
b,n=sys.argv[1],int(sys.argv[2])
c=Image.new('RGB',(1600,400*n),'white')
for r in range(n):
    for i in range(4):
        im=Image.open(f'/tmp/fold_{b}_{r}_{i}.png'); c.paste(im,(i*400,r*400),im)
c.save(f'/tmp/fold_{b}.png')
P
