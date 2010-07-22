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

import org.apache.log4j.Logger;

import com.cloud.bridge.service.exception.InternalErrorException;
import com.cloud.bridge.util.StringHelper;

/**
 * @author Kelven Yang
 */
public class UserContext {
    protected final static Logger logger = Logger.getLogger(UserContext.class);
    
	private static ThreadLocal<UserContext> threadUserContext = new ThreadLocal<UserContext>();

	private boolean annonymous = false;
	private String accessKey;
	private String secretKey;
	private String canonicalUserId;
	private String description;
	
	public UserContext() {
	}
	
	public static UserContext current() {
		UserContext context = threadUserContext.get();
		if(context == null) {
			context = new UserContext();
			threadUserContext.set(context);
		}
		return context;
	}
	
	public void initContext() {
		annonymous = true;
	}
	
	public void initContext(String accessKey, String secretKey, String canonicalUserId, String description) {
		this.accessKey = accessKey;
		this.secretKey = secretKey;
		this.canonicalUserId = canonicalUserId;
		this.description = description;
	}
	
	public String getAccessKey() {
		if(annonymous)
			return StringHelper.EMPTY_STRING;
		
		if(accessKey == null) {
			logger.error("Fatal - UserContext has not been correctly setup");
			throw new InternalErrorException("Uninitalized user context");
		}
		return accessKey;
	}
	
	public String getSecretKey() {
		if(annonymous)
			return StringHelper.EMPTY_STRING;
		
		if(secretKey == null) {
			logger.error("Fatal - UserContext has not been correctly setup");
			throw new InternalErrorException("Uninitalized user context");
		}
		
		return secretKey;
	}
	
	public String getCanonicalUserId() {
		if(annonymous)
			return StringHelper.EMPTY_STRING;
		
		if(canonicalUserId == null) {
			logger.error("Fatal - UserContext has not been correctly setup");
			throw new InternalErrorException("Uninitalized user context");
		}
		
		return canonicalUserId;
	}
	
	public String getDescription() {
		if(description != null)
			return description;
		
		return StringHelper.EMPTY_STRING;
	}
}