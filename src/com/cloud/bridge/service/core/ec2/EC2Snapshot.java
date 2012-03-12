/*
 * Copyright (C) 2011 Citrix Systems, Inc.  All rights reserved.
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
package com.cloud.bridge.service.core.ec2;

import java.util.Calendar;

import com.cloud.bridge.util.EC2RestAuth;

public class EC2Snapshot {

	private String   id;
	private String   name;
	private String   volumeId;
	private Long      volumeSize;   // in gigs
    private String   type;
    private String   state;
    private Calendar created;
    private String 	 accountName;
    private String 	 domainId;
    
	public EC2Snapshot() {
		id         	= null;
		name       	= null;
		volumeId   	= null;
		volumeSize 	= null;
		type       	= null;
		state       = null;
		created    	= null;
		accountName = null;
		domainId	= null;
	}
	
	public void setId(String id ) {
		this.id = id;
	}
	
	public String getId() {
		return this.id;
	}

	public void setName( String name ) {
		this.name = name;
	}
	
	public String getName() {
		return this.name;
	}

	public void setVolumeId(String volumeId ) {
		this.volumeId = volumeId;
	}
	
	public String getVolumeId() {
		return this.volumeId;
	}
	
	public void setVolumeSize( Long volumeSize ) {
		this.volumeSize = volumeSize;
	}
	
	public Long getVolumeSize() {
		return this.volumeSize;
	}

	public void setType( String type ) {
		this.type = type;
	}
	
	public String getType() {
		return this.type;
	}


	public void setCreated( String created ) {
		this.created = EC2RestAuth.parseDateString( created );
	}
	
	public Calendar getCreated() {
		return this.created;
	}

	public String getAccountName() {
		return accountName;
	}

	public void setAccountName(String accountName) {
		this.accountName = accountName;
	}

	public String getDomainId() {
		return domainId;
	}

	public void setDomainId(String domainId) {
		this.domainId = domainId;
	}

	public String getState() {
		return state;
	}
	
	public void setState(String state) {
		this.state = state;
	}
}
