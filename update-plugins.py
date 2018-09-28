#!/usr/bin/python

import json
import requests
import os
import sys
from natsort import natsorted
from distutils.version import StrictVersion

def VersionSorter(v):
  try:
    return StrictVersion(v)
  except:
    print("Unable to parse version: %s" % v)
    return StrictVersion("0.1")


path = sys.argv[1]

plugins = requests.get("https://updates.jenkins.io/plugin-versions.json").json()['plugins']

content = ""
for line in open(path).read().split("\n"):
  if ':' not in line:
    continue
  plugin = line.split(":")[0]
  version = line.split(":")[1]
  versions = list(plugins[plugin].keys())
  versions.sort(key=VersionSorter)
  latest_version = versions[-1]
  if version != latest_version:
    print ("%s:%s -> %s" % (plugin, version, latest_version))

  content += "%s:%s\n" % (plugin, latest_version)

open(path, 'w').write(content)