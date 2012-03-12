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

package com.cloud.stack.models;

import com.google.gson.annotations.SerializedName;

/**
 * @author slriv
 *
 */
public class CloudStackDiskOffering {
	@SerializedName(ApiConstants.ID)
	private String id;
	@SerializedName(ApiConstants.NAME)
	private String name;
	@SerializedName(ApiConstants.DISPLAY_TEXT)
	private String displayText;
	@SerializedName(ApiConstants.DISK_SIZE)
	private Long diskSize;
	@SerializedName(ApiConstants.CREATED)
	private String created;
	@SerializedName(ApiConstants.IS_CUSTOMIZED)
	private boolean isCustomized;

	/**
	 * 
	 */
	public CloudStackDiskOffering() {
	}

	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return the displayText
	 */
	public String getDisplayText() {
		return displayText;
	}

	/**
	 * @return the diskSize
	 */
	public Long getDiskSize() {
		return diskSize;
	}

	/**
	 * @return the created
	 */
	public String getCreated() {
		return created;
	}

	/**
	 * @return the isCustomized
	 */
	public boolean isCustomized() {
		return isCustomized;
	}
}
