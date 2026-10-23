#!/bin/sh
cd "$(dirname "$0")"
java -Djava.library.path=. -jar lossless.jar
