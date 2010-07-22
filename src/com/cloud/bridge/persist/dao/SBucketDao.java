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
package com.cloud.bridge.persist.dao;

import java.util.List;

import com.cloud.bridge.model.SBucket;
import com.cloud.bridge.persist.EntityDao;

/**
 * @author Kelven Yang
 */
public class SBucketDao extends EntityDao<SBucket> {
	public SBucketDao() {
		super(SBucket.class);
	}

	public SBucket getByName(String bucketName) {
		return queryEntity("from SBucket where name=?", new Object[] {bucketName});
	}
	
	public List<SBucket> listBuckets(String canonicalId) {
		return queryEntities("from SBucket where ownerCanonicalId=? order by createTime asc", 
			new Object[] {canonicalId});
	}
}