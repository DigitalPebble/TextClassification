#!/bin/bash
mkdir dist

version=`cat build.properties | sed 's/version=//'`

# cp -rf lib dist/lib
if [ -e doc/javadoc/ ]; then
  cp -rf doc/javadoc/ dist/
fi
cp -rf *.txt dist/
cp -rf build.* dist/
cp -rf src/ dist/
cp textclassification*.jar dist/
rm -rf `find dist -name .svn`
mv dist TextClassification-$version
tar -cf TextClassification-$version.tar TextClassification-$version
rm -rf TextClassification-$version
gzip TextClassification-$version.tar
