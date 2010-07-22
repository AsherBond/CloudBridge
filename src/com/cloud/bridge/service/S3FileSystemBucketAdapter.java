/*
 * Copyright 2010 Cloud.com, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cloud.bridge.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.activation.DataHandler;
import javax.activation.DataSource;

import org.apache.log4j.Logger;

import com.cloud.bridge.service.exception.FileNotExistException;
import com.cloud.bridge.service.exception.InternalErrorException;
import com.cloud.bridge.service.exception.OutOfStorageException;
import com.cloud.bridge.util.FileRangeDataSource;
import com.cloud.bridge.util.StringHelper;

/**
 * @author Kelven Yang
 */
public class S3FileSystemBucketAdapter implements S3BucketAdapter {
    protected final static Logger logger = Logger.getLogger(S3FileSystemBucketAdapter.class);
	
	public S3FileSystemBucketAdapter() {
	}
	
	@Override
	public void createContainer(String mountedRoot, String bucket) {
		String dir = getBucketFolderDir(mountedRoot, bucket);
		if(!new File(dir).mkdirs())
			throw new OutOfStorageException("Unable to create " + dir + " for bucket " + bucket); 
	}
	
	@Override
	public void deleteContainer(String mountedRoot, String bucket) {
		String dir = getBucketFolderDir(mountedRoot, bucket);
		File path = new File(dir);
		if(!deleteDirectory(path))
			throw new OutOfStorageException("Unable to delete " + dir + " for bucket " + bucket); 
	}
	
	@Override
	public String getBucketFolderDir(String mountedRoot, String bucket) {
		String bucketFolder = getBucketFolderName(bucket);
		String dir;
		String separator = ""+File.separatorChar;
		if(!mountedRoot.endsWith(separator))
			dir = mountedRoot + separator + bucketFolder;
		else
			dir = mountedRoot + bucketFolder;
		
		return dir;
	}
	
	@Override
	public String saveObject(InputStream is, String mountedRoot, String bucket, String fileName) {
		MessageDigest md5;
		try {
			md5 = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			logger.error("Unexpected exception " + e.getMessage(), e);
			throw new InternalErrorException("Unabel to get MD5 MessageDigest", e);
		}
		
		File file = new File(getBucketFolderDir(mountedRoot, bucket) + File.separatorChar + fileName);
		try {
	        final FileOutputStream fos = new FileOutputStream(file);
	        byte[] buffer = new byte[4096];
	        int len = 0;
	        while( (len = is.read(buffer)) > 0) {
	        	fos.write(buffer, 0, len);
	        	md5.update(buffer, 0, len);
	        }
	        fos.close();
	        
	        return StringHelper.toHexString(md5.digest());
		} catch(IOException e) {
			logger.error("Unexpected exception " + e.getMessage(), e);
			throw new OutOfStorageException(e);
		}
	}
	
	@Override
	public DataHandler loadObject(String mountedRoot, String bucket, String fileName) {
		File file = new File(getBucketFolderDir(mountedRoot, bucket) + File.separatorChar + fileName);
		try {
			return new DataHandler(file.toURL());
		} catch (MalformedURLException e) {
			throw new FileNotExistException("Unable to open underlying object file");
		}
	}

	@Override
	public DataHandler loadObjectRange(String mountedRoot, String bucket, String fileName, long startPos, long endPos) {
		File file = new File(getBucketFolderDir(mountedRoot, bucket) + File.separatorChar + fileName);
		try {
			DataSource ds = new FileRangeDataSource(file, startPos, endPos);
			return new DataHandler(ds);
		} catch (MalformedURLException e) {
			throw new FileNotExistException("Unable to open underlying object file");
		} catch(IOException e) {
			throw new FileNotExistException("Unable to open underlying object file");
		}
	}
	
	public static boolean deleteDirectory(File path) {
		 if( path.exists() ) {
			 File[] files = path.listFiles();
			 for(int i = 0; i < files.length; i++) {
				 if(files[i].isDirectory()) {
					 deleteDirectory(files[i]);
				 } else {
					 files[i].delete();
				 }
			 }
		 }
		 return path.delete();
	}
	
	private String getBucketFolderName(String bucket) {
		// temporary 
		String name = bucket.replace(' ', '_');
		name = bucket.replace('\\', '-');
		name = bucket.replace('/', '-');
		
		return name;
	}
}