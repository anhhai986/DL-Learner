#!/bin/bash
# This script builds the DL-LEarner project and creates the Zip and Tarball file
# which contains the command line interface 'cli' and 'enrichment'.
mvn -DskipTests=true clean install
cd interfaces
mvn -Prelease package -DskipTests=true
