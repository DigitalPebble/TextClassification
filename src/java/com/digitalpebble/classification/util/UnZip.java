package com.digitalpebble.classification.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class UnZip {
  static final int BUFFER = 2048;
  
  /**
   * Unzips the argument into the temp directory and returns the unzipped
   * location. The zip must have a root dir element and not just a flat list of files
   **/
  public static File unzip(File inputZip) {
    
    File rootDir = null;
    try {
      BufferedOutputStream dest = null;
      BufferedInputStream is = null;
      ZipEntry entry;
      ZipFile zipfile = new ZipFile(inputZip);
      File test = File.createTempFile("aaa", "aaa");
      String tempDir = test.getParent();
      test.delete();
      Enumeration e = zipfile.entries();
      while (e.hasMoreElements()) {
        entry = (ZipEntry) e.nextElement();
        is = new BufferedInputStream(zipfile.getInputStream(entry));
        int count;
        byte data[] = new byte[BUFFER];
        File target = new File(tempDir, entry.getName());
        if (entry.getName().endsWith("/")) {
          target.mkdir();
          if (rootDir == null) rootDir = target;
          continue;
        }
        FileOutputStream fos = new FileOutputStream(target);
        dest = new BufferedOutputStream(fos, BUFFER);
        while ((count = is.read(data, 0, BUFFER)) != -1) {
          dest.write(data, 0, count);
        }
        dest.flush();
        dest.close();
        is.close();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return rootDir;
  }
  
  public static void main(String args[]) {
    // test loading TextClassif from zip
    System.out.println(unzip(new File(args[0])));
  }
}
