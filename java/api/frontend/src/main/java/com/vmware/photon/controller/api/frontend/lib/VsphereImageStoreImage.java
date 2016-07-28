/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.api.frontend.lib;

import com.vmware.photon.controller.api.frontend.exceptions.external.NameTakenException;
import com.vmware.photon.controller.api.frontend.exceptions.internal.InternalException;
import com.vmware.photon.controller.api.frontend.lib.ova.VmdkMetadata;
import com.vmware.transfer.nfc.NfcClient;
import com.vmware.transfer.nfc.NfcFileOutputStream;
import com.vmware.transfer.streamVmdk.VmdkFormatException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;


/**
 * Class representing a data store folder.
 */
public class VsphereImageStoreImage implements Image {
  private static final Logger logger = LoggerFactory.getLogger(VsphereImageStoreImage.class);
  private NfcClient nfcClient;
  private final String uploadFolder;
  private final String imageId;

  public VsphereImageStoreImage(NfcClient nfcClient, String uploadFolder, String imageId) {
    this.nfcClient = nfcClient;
    this.uploadFolder = uploadFolder;
    this.imageId = imageId;
  }

  @Override
  public String getImageId() {
    return imageId;
  }

  @Override
  public String getUploadFolder() {
    return uploadFolder;
  }

  /**
   * Upload file to remote datastore. If the file name exists in the datastore, it will be overwritten. A typical
   * datastore path for image id 123456789 is: [datastore1] tmp_upload_123456789/[fileName]
   */
  @Override
  public long addFile(String fileName, InputStream inputStream, long fileSize) throws IOException, NameTakenException,
      InternalException {
    try {
      String imagePath = getImageFilePath(fileName);
      logger.info("write to {}", imagePath);
      /*
      try (NfcFileOutputStream outputStream = nfcClient.putFile(imagePath, fileSize)) {
        for (int i = 0; i < fileSize; i++) {
          outputStream.write(inputStream.read());
        }
      }*/
      return fileSize;
    } finally {
      if (inputStream != null) {
        inputStream.close();
      }
    }
  }


  public void copyDiskSCP(String imagePath, String fileName, InputStream inputStream) throws IOException {
	  try{
		  logger.info("InsideCopyDiskSCP ImagePath: {}", imagePath);
		  logger.info("InsideCopyDiskSCP fileName: {}", fileName);
		  JSch jsch = new JSch();
		  Session session = null;
		  session = jsch.getSession("root","10.118.100.229",22);
		  session.setPassword("ca$hc0w");
		  session.setConfig("StrictHostKeyChecking", "no");
		  session.connect();
		  ChannelSftp channel = null;
		  channel = (ChannelSftp)session.openChannel("sftp");
		  channel.connect();
		  channel.cd(imagePath);
		  channel.put(inputStream,fileName);
		  channel.disconnect();
		  session.disconnect();
	  } catch (Exception e) {
		  logger.error(e.getMessage());
		  throw new RuntimeException(e);
	  } finally {
		  if (inputStream != null) {
			  inputStream.close();
		  }
	  }
  }




  /**
   * Upload disk to remote datastore. If the image name exists in the datastore, it will be overwritten. A typical
   * datastore path for image id 123456789 is: [datastore1] tmp_upload_123456789/123456789.vmdk
   */
  @Override
  public long addDisk(String fileName, InputStream inputStream) throws IOException, VmdkFormatException,
      NameTakenException, InternalException {
    if (!inputStream.markSupported()) {
      inputStream = new BufferedInputStream(inputStream);
    }
    int singleExtentSize = VmdkMetadata.getSingleExtentSize(inputStream);
    String imagePath = getImageFilePath(fileName);
    
    logger.info("write to {}", imagePath);
    logger.info("add logic to copy file in the image path: {}", imagePath);
    logger.info("filename {}", fileName);
    //imagePath = imagePath.replace("[] ", "");
    File file = new File(imagePath);
    
    String remoteimagePath= file.getAbsoluteFile().getParent();
    String remoteFileName = file.getName();
    logger.info("write to {}", remoteimagePath);
    logger.info("write to {}", remoteFileName);
    copyDiskSCP(remoteimagePath,remoteFileName, inputStream);
    //nfcClient.putStreamOptimizedDisk(imagePath, inputStream);
    return singleExtentSize * 512L; // a sector is 512 bytes
  }

  @Override
  public void close() {
    if (nfcClient == null) {
      return;
    }

    try {
      nfcClient.close();
    } catch (IOException e) {
      // Adding traces for failure on closing nfc client connection.
      logger.warn("Exception on closing nfc client: {}", e);
    }
    nfcClient = null;
  }

  /**
   * Construct the file path from the folder name and file name.
   *
   * @param fileName
   * @return
   */
  private String getImageFilePath(String fileName) {
	  logger.info("uploadFolder: {}", uploadFolder);
	  logger.info("imageId: {}", imageId);
	  return String.format("%s/%s%s", uploadFolder, imageId, fileName);
  }
}
