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

import com.amazon.s3.AccessControlList;
import com.amazon.s3.AccessControlPolicy;
import com.amazon.s3.AmazonS3SkeletonInterface;
import com.amazon.s3.CanonicalUser;
import com.amazon.s3.CopyObjectResponse;
import com.amazon.s3.CreateBucket;
import com.amazon.s3.CreateBucketResponse;
import com.amazon.s3.CreateBucketResult;
import com.amazon.s3.DeleteBucket;
import com.amazon.s3.DeleteBucketResponse;
import com.amazon.s3.DeleteObject;
import com.amazon.s3.DeleteObjectResponse;
import com.amazon.s3.GetBucketAccessControlPolicy;
import com.amazon.s3.GetBucketAccessControlPolicyResponse;
import com.amazon.s3.GetBucketLoggingStatus;
import com.amazon.s3.GetBucketLoggingStatusResponse;
import com.amazon.s3.GetObject;
import com.amazon.s3.GetObjectAccessControlPolicy;
import com.amazon.s3.GetObjectAccessControlPolicyResponse;
import com.amazon.s3.GetObjectExtended;
import com.amazon.s3.GetObjectExtendedResponse;
import com.amazon.s3.GetObjectResponse;
import com.amazon.s3.GetObjectResult;
import com.amazon.s3.Grant;
import com.amazon.s3.Grantee;
import com.amazon.s3.ListAllMyBuckets;
import com.amazon.s3.ListAllMyBucketsEntry;
import com.amazon.s3.ListAllMyBucketsList;
import com.amazon.s3.ListAllMyBucketsResponse;
import com.amazon.s3.ListAllMyBucketsResult;
import com.amazon.s3.ListBucket;
import com.amazon.s3.ListBucketResponse;
import com.amazon.s3.ListBucketResult;
import com.amazon.s3.ListEntry;
import com.amazon.s3.MetadataEntry;
import com.amazon.s3.Permission;
import com.amazon.s3.PrefixEntry;
import com.amazon.s3.PutObject;
import com.amazon.s3.PutObjectInline;
import com.amazon.s3.PutObjectInlineResponse;
import com.amazon.s3.PutObjectResponse;
import com.amazon.s3.PutObjectResult;
import com.amazon.s3.SetBucketAccessControlPolicy;
import com.amazon.s3.SetBucketAccessControlPolicyResponse;
import com.amazon.s3.SetBucketLoggingStatus;
import com.amazon.s3.SetBucketLoggingStatusResponse;
import com.amazon.s3.SetObjectAccessControlPolicy;
import com.amazon.s3.SetObjectAccessControlPolicyResponse;
import com.amazon.s3.Status;
import com.amazon.s3.StorageClass;
import com.cloud.bridge.model.SAcl;
import com.cloud.bridge.service.core.s3.S3AccessControlList;
import com.cloud.bridge.service.core.s3.S3AccessControlPolicy;
import com.cloud.bridge.service.core.s3.S3CanonicalUser;
import com.cloud.bridge.service.core.s3.S3CreateBucketRequest;
import com.cloud.bridge.service.core.s3.S3CreateBucketResponse;
import com.cloud.bridge.service.core.s3.S3DeleteBucketRequest;
import com.cloud.bridge.service.core.s3.S3DeleteObjectRequest;
import com.cloud.bridge.service.core.s3.S3Engine;
import com.cloud.bridge.service.core.s3.S3GetBucketAccessControlPolicyRequest;
import com.cloud.bridge.service.core.s3.S3GetObjectAccessControlPolicyRequest;
import com.cloud.bridge.service.core.s3.S3GetObjectRequest;
import com.cloud.bridge.service.core.s3.S3GetObjectResponse;
import com.cloud.bridge.service.core.s3.S3Grant;
import com.cloud.bridge.service.core.s3.S3ListAllMyBucketsEntry;
import com.cloud.bridge.service.core.s3.S3ListAllMyBucketsRequest;
import com.cloud.bridge.service.core.s3.S3ListAllMyBucketsResponse;
import com.cloud.bridge.service.core.s3.S3ListBucketObjectEntry;
import com.cloud.bridge.service.core.s3.S3ListBucketPrefixEntry;
import com.cloud.bridge.service.core.s3.S3ListBucketRequest;
import com.cloud.bridge.service.core.s3.S3ListBucketResponse;
import com.cloud.bridge.service.core.s3.S3MetaDataEntry;
import com.cloud.bridge.service.core.s3.S3PutObjectInlineRequest;
import com.cloud.bridge.service.core.s3.S3PutObjectInlineResponse;
import com.cloud.bridge.service.core.s3.S3Response;
import com.cloud.bridge.service.core.s3.S3SetObjectAccessControlPolicyRequest;
import com.cloud.bridge.service.exception.InternalErrorException;

public class S3SoapServiceImpl implements AmazonS3SkeletonInterface {
    protected final static Logger logger = Logger.getLogger(S3SoapServiceImpl.class);
    
    private S3Engine engine;
    
    public S3SoapServiceImpl(S3Engine engine) {
    	this.engine = engine;
    }
    
	public GetBucketLoggingStatusResponse getBucketLoggingStatus(
          GetBucketLoggingStatus getBucketLoggingStatus) {
        throw new UnsupportedOperationException("Unsupported API");
	}
	
	public SetBucketLoggingStatusResponse setBucketLoggingStatus(SetBucketLoggingStatus setBucketLoggingStatus) {
        throw new UnsupportedOperationException("Unsupported API");
    }
	     
	public CopyObjectResponse copyObject(com.amazon.s3.CopyObject copyObject) {
        //TODO : fill this with the necessary business logic
        throw new UnsupportedOperationException("Please implement " + this.getClass().getName() + "#copyObject");
    }
 
	public GetBucketAccessControlPolicyResponse getBucketAccessControlPolicy(
		GetBucketAccessControlPolicy getBucketAccessControlPolicy) {
		// after authentication, we should setup user context
		UserContext.current().initContext("TODO", "TODO", "TODO", "TODO");
		return toGetBucketAccessControlPolicyResponse(engine.handleRequest(
			toEngineGetBucketAccessControlPolicyRequest(getBucketAccessControlPolicy)));
    }
	
	private S3GetBucketAccessControlPolicyRequest toEngineGetBucketAccessControlPolicyRequest(
		GetBucketAccessControlPolicy getBucketAccessControlPolicy) {
		S3GetBucketAccessControlPolicyRequest request = new S3GetBucketAccessControlPolicyRequest();
		
		request.setAccessKey(getBucketAccessControlPolicy.getAWSAccessKeyId());
		request.setRequestTimestamp(getBucketAccessControlPolicy.getTimestamp());
		request.setSignature(getBucketAccessControlPolicy.getSignature());
		request.setBucketName(getBucketAccessControlPolicy.getBucket());
		return request;
	}
	
	private GetBucketAccessControlPolicyResponse toGetBucketAccessControlPolicyResponse(S3AccessControlPolicy policy) {
		GetBucketAccessControlPolicyResponse response = new GetBucketAccessControlPolicyResponse();
		response.setGetBucketAccessControlPolicyResponse(toAccessControlPolicy(policy));
		return response;
	}
 
	public SetBucketAccessControlPolicyResponse setBucketAccessControlPolicy(
          SetBucketAccessControlPolicy setBucketAccessControlPolicy) {
		
		S3SetObjectAccessControlPolicyRequest request = new S3SetObjectAccessControlPolicyRequest();
		request.setAccessKey(setBucketAccessControlPolicy.getAWSAccessKeyId());
		request.setRequestTimestamp(setBucketAccessControlPolicy.getTimestamp());
		request.setSignature(setBucketAccessControlPolicy.getSignature());
		request.setBucketName(setBucketAccessControlPolicy.getBucket());
		request.setAcl(toEngineAccessControlList(setBucketAccessControlPolicy.getAccessControlList()));
		
		engine.handleRequest(request);
		SetBucketAccessControlPolicyResponse response = new SetBucketAccessControlPolicyResponse();
		return response;
    }
	
	public ListBucketResponse listBucket (ListBucket listBucket) {
		// after authentication, we should setup user context
		UserContext.current().initContext("TODO", "TODO", "TODO", "TODO");
		return toListBucketResponse(engine.handleRequest(toEngineListBucketRequest(listBucket)));
    }
	
	private S3ListBucketRequest toEngineListBucketRequest(ListBucket listBucket) {
		S3ListBucketRequest request = new S3ListBucketRequest();
		
		request.setAccessKey(listBucket.getAWSAccessKeyId());
		request.setRequestTimestamp(listBucket.getTimestamp());
		request.setSignature(listBucket.getSignature());
		
		request.setBucketName(listBucket.getBucket());
		request.setDelimiter(listBucket.getDelimiter());
		request.setMarker(listBucket.getMarker());
		request.setMaxKeys(listBucket.getMaxKeys());
		request.setPrefix(listBucket.getPrefix());
		return request;
	}
	
	private ListBucketResponse toListBucketResponse(S3ListBucketResponse engineResponse) {
		ListBucketResponse response = new ListBucketResponse();
		ListBucketResult result = new ListBucketResult();
		result.setName(engineResponse.getBucketName());
		result.setDelimiter(engineResponse.getDelimiter());
		result.setPrefix(engineResponse.getPrefix());
		result.setMarker(engineResponse.getMarker());
		result.setMaxKeys(engineResponse.getMaxKeys());
		result.setIsTruncated(engineResponse.isTruncated());
		result.setNextMarker(engineResponse.getNextMarker());
		result.setCommonPrefixes(toPrefixEntry(engineResponse.getCommonPrefixes()));
		result.setContents(toListEntry(engineResponse.getContents()));
		response.setListBucketResponse(result);
		return response;
	}
	
	private PrefixEntry[] toPrefixEntry(S3ListBucketPrefixEntry[] engineEntries) {
		if(engineEntries != null) {
			PrefixEntry[] entries = new PrefixEntry[engineEntries.length];
			for(int i = 0; i < engineEntries.length; i++) {
				entries[i] = new PrefixEntry();
				entries[i].setPrefix(engineEntries[i].getPrefix());
			}
			
			return entries;
		}
		return null;
	}
	
	private ListEntry[] toListEntry(S3ListBucketObjectEntry[] engineEntries) {
		if(engineEntries != null) {
			ListEntry[] entries = new ListEntry[engineEntries.length];
			for(int i = 0; i < engineEntries.length; i++) {
				entries[i] = new ListEntry();
				entries[i].setETag(engineEntries[i].getETag());
				entries[i].setKey(engineEntries[i].getKey());
				entries[i].setLastModified(engineEntries[i].getLastModified());
				entries[i].setSize(engineEntries[i].getSize());
				entries[i].setStorageClass(StorageClass.STANDARD);
				
				CanonicalUser owner = new CanonicalUser();
				owner.setID(engineEntries[i].getOwnerCanonicalId());
				owner.setDisplayName(engineEntries[i].getOwnerDisplayName());
				entries[i].setOwner(owner);
			}
			return entries;
		}
		
		return null;
	}
	
	public PutObjectResponse putObject(PutObject putObject) {
        //TODO : fill this with the necessary business logic
        throw new UnsupportedOperationException("Please implement " + this.getClass().getName() + "#putObject");
    }
 
	public CreateBucketResponse createBucket (CreateBucket createBucket) {
		// after authentication, we should setup user context
		UserContext.current().initContext("TODO", "TODO", "TODO", "TODO");
		return toCreateBucketResponse(engine.handleRequest(toEngineCreateBucketRequest(createBucket)));
    }
	
	private S3CreateBucketRequest toEngineCreateBucketRequest(CreateBucket createBucket) {
		S3CreateBucketRequest request = new S3CreateBucketRequest();
		request.setAccessKey(createBucket.getAWSAccessKeyId());
		request.setRequestTimestamp(createBucket.getTimestamp());
		request.setSignature(createBucket.getSignature());
		request.setBucketName(createBucket.getBucket());
		return request;
	}
	
	private CreateBucketResponse toCreateBucketResponse(S3CreateBucketResponse engineResponse) {
		CreateBucketResponse response = new CreateBucketResponse();
		CreateBucketResult result = new CreateBucketResult();
		result.setBucketName(engineResponse.getBucketName());
		response.setCreateBucketReturn(result);
		return response;
	}
 
	public ListAllMyBucketsResponse listAllMyBuckets (ListAllMyBuckets listAllMyBuckets) {
		// after authentication, we should setup user context
		UserContext.current().initContext("TODO", "TODO", "TODO", "TODO");
		return toListAllMyBucketsResponse(engine.handleRequest(toEngineListAllMyBucketsRequest(listAllMyBuckets)));
    }
	
	private S3ListAllMyBucketsRequest toEngineListAllMyBucketsRequest(ListAllMyBuckets listAllMyBuckets) {
		S3ListAllMyBucketsRequest request = new S3ListAllMyBucketsRequest();
		request.setAccessKey(listAllMyBuckets.getAWSAccessKeyId());
		request.setRequestTimestamp(listAllMyBuckets.getTimestamp());
		request.setSignature(listAllMyBuckets.getSignature());
		return request;
	}
	
	private ListAllMyBucketsResponse toListAllMyBucketsResponse(S3ListAllMyBucketsResponse engineResponse) {
		ListAllMyBucketsResponse response = new ListAllMyBucketsResponse();
		ListAllMyBucketsResult result = new ListAllMyBucketsResult();
		
		S3CanonicalUser ownerEngine = engineResponse.getOwner();
		CanonicalUser owner = new CanonicalUser();
		owner.setID(ownerEngine.getID());
		owner.setDisplayName(ownerEngine.getDisplayName());
		result.setOwner(owner);
		
		S3ListAllMyBucketsEntry[] engineEntries = engineResponse.getBuckets();
		if(engineEntries != null) {
			ListAllMyBucketsEntry[] entries = new ListAllMyBucketsEntry[engineEntries.length];
			for(int i = 0; i < engineEntries.length; i++) {
				entries[i] = new ListAllMyBucketsEntry();
				entries[i].setName(engineEntries[i].getName());
				entries[i].setCreationDate(engineEntries[i].getCreationDate());
			}
			
			ListAllMyBucketsList list = new ListAllMyBucketsList();
			list.setBucket(entries);
			result.setBuckets(list);
		}
		response.setListAllMyBucketsResponse(result);
		return response;
	}
	
	public DeleteBucketResponse deleteBucket(DeleteBucket deleteBucket) {
		// after authentication, we should setup user context
		UserContext.current().initContext("TODO", "TODO", "TODO", "TODO");
		return toDeleteBucketResponse(engine.handleRequest(toEngineDeleteBucketRequest(deleteBucket)));
    }
	
	private S3DeleteBucketRequest toEngineDeleteBucketRequest(DeleteBucket deleteBucket) {
		S3DeleteBucketRequest request = new S3DeleteBucketRequest();
		request.setAccessKey(deleteBucket.getAWSAccessKeyId());
		request.setRequestTimestamp(deleteBucket.getTimestamp());
		request.setSignature(deleteBucket.getSignature());
		request.setBucketName(deleteBucket.getBucket());
		return request;
	}
	
	private DeleteBucketResponse toDeleteBucketResponse(S3Response engineResponse) {
		DeleteBucketResponse response = new DeleteBucketResponse();
		Status status = new Status();
		status.setCode(engineResponse.getResultCode());
		status.setDescription(engineResponse.getResultDescription());
		response.setDeleteBucketResponse(status);
		return response;
	}
     
	public GetObjectResponse getObject(com.amazon.s3.GetObject getObject) {
		// after authentication, we should setup user context
		UserContext.current().initContext("TODO", "TODO", "TODO", "TODO");
		return toGetObjectResponse(engine.handleRequest(toEngineGetObjectRequest(getObject)));
    }
	
	public GetObjectExtendedResponse getObjectExtended(GetObjectExtended getObjectExtended) {
		// after authentication, we should setup user context
		UserContext.current().initContext("TODO", "TODO", "TODO", "TODO");
		return toGetObjectExtendedResponse(engine.handleRequest(toEngineGetObjectRequest(getObjectExtended)));
    }
	
	private S3GetObjectRequest toEngineGetObjectRequest(GetObject getObject) {
		S3GetObjectRequest request = new S3GetObjectRequest();
		
		request.setAccessKey(getObject.getAWSAccessKeyId());
		request.setRequestTimestamp(getObject.getTimestamp());
		request.setSignature(getObject.getSignature());
		request.setBucketName(getObject.getBucket());
		request.setKey(getObject.getKey());
		request.setReturnData(getObject.getGetData());
		request.setReturnMetadata(getObject.getGetMetadata());
		request.setInlineData(getObject.getInlineData());
		return request;
	}
	
	private S3GetObjectRequest toEngineGetObjectRequest(GetObjectExtended getObjectExtended) {
		S3GetObjectRequest request = new S3GetObjectRequest();
		request.setAccessKey(getObjectExtended.getAWSAccessKeyId());
		request.setRequestTimestamp(getObjectExtended.getTimestamp());
		request.setSignature(getObjectExtended.getSignature());
		request.setBucketName(getObjectExtended.getBucket());
		request.setKey(getObjectExtended.getKey());
		request.setReturnData(getObjectExtended.getGetData());
		request.setReturnMetadata(getObjectExtended.getGetMetadata());
		request.setInlineData(getObjectExtended.getInlineData());
		
		request.setByteRangeStart(getObjectExtended.getByteRangeStart());
		request.setByteRangeEnd(getObjectExtended.getByteRangeEnd());
		request.setIfMatch(getObjectExtended.getIfMatch());
		request.setIfModifiedSince(getObjectExtended.getIfModifiedSince());
		request.setIfUnmodifiedSince(getObjectExtended.getIfUnmodifiedSince());
		request.setIfNoneMatch(getObjectExtended.getIfNoneMatch());
		request.setReturnCompleteObjectOnConditionFailure(getObjectExtended.getReturnCompleteObjectOnConditionFailure());
		return request;
	}
	
	private GetObjectResponse toGetObjectResponse(S3GetObjectResponse engineResponse) {
		GetObjectResponse response = new GetObjectResponse();

		GetObjectResult result = new GetObjectResult();
		result.setData(engineResponse.getData());
		result.setETag(engineResponse.getETag());
		result.setLastModified(engineResponse.getLastModified());
		result.setMetadata(toMetadataEntry(engineResponse.getMetaEntries()));
		
		response.setGetObjectResponse(result);
		return response;
	}
	
	private GetObjectExtendedResponse toGetObjectExtendedResponse(S3GetObjectResponse engineResponse) {
		GetObjectExtendedResponse response = new GetObjectExtendedResponse();

		GetObjectResult result = new GetObjectResult();
		result.setData(engineResponse.getData());
		result.setETag(engineResponse.getETag());
		result.setLastModified(engineResponse.getLastModified());
		result.setMetadata(toMetadataEntry(engineResponse.getMetaEntries()));
		
		response.setGetObjectResponse(result);
		return response;
	}
	
	private MetadataEntry[] toMetadataEntry(S3MetaDataEntry[] engineEntries) {
		if(engineEntries != null) {
			MetadataEntry[] entries = new MetadataEntry[engineEntries.length];
			for(int i = 0; i < engineEntries.length; i++) {
				entries[i] = new MetadataEntry();
				entries[i].setName(engineEntries[i].getName());
				entries[i].setValue(engineEntries[i].getValue());
			}
			return entries;
		}
		return null;
	}
 
	public GetObjectAccessControlPolicyResponse getObjectAccessControlPolicy(
		GetObjectAccessControlPolicy getObjectAccessControlPolicy) {
		// after authentication, we should setup user context
		UserContext.current().initContext("TODO", "TODO", "TODO", "TODO");
		return toGetObjectAccessControlPolicyResponse(engine.handleRequest(
			toEngineGetObjectAccessControlPolicyRequest(getObjectAccessControlPolicy)));
    }
	
	private S3GetObjectAccessControlPolicyRequest toEngineGetObjectAccessControlPolicyRequest(
		GetObjectAccessControlPolicy getObjectAccessControlPolicy) {
		S3GetObjectAccessControlPolicyRequest request = new S3GetObjectAccessControlPolicyRequest();
		
		request.setAccessKey(getObjectAccessControlPolicy.getAWSAccessKeyId());
		request.setRequestTimestamp(getObjectAccessControlPolicy.getTimestamp());
		request.setSignature(getObjectAccessControlPolicy.getSignature());
		request.setBucketName(getObjectAccessControlPolicy.getBucket());
		request.setKey(getObjectAccessControlPolicy.getKey());
		return request;
	}
	
	private GetObjectAccessControlPolicyResponse toGetObjectAccessControlPolicyResponse(S3AccessControlPolicy policy) {
		GetObjectAccessControlPolicyResponse response = new GetObjectAccessControlPolicyResponse();
		response.setGetObjectAccessControlPolicyResponse(toAccessControlPolicy(policy));
		return response;
	}
	
	private AccessControlPolicy toAccessControlPolicy(S3AccessControlPolicy enginePolicy) {
		AccessControlPolicy policy = new AccessControlPolicy();
		CanonicalUser owner = new CanonicalUser();
		owner.setID(enginePolicy.getOwner().getID());
		owner.setDisplayName(enginePolicy.getOwner().getDisplayName());
		policy.setOwner(owner);
		
		AccessControlList acl = new AccessControlList();
		acl.setGrant(toGrants(enginePolicy.getGrants()));
		policy.setAccessControlList(acl);
		return policy;
	}
     
	public DeleteObjectResponse deleteObject (DeleteObject deleteObject) {
		// after authentication, we should setup user context
		UserContext.current().initContext("TODO", "TODO", "TODO", "TODO");
		return toDeleteObjectResponse(engine.handleRequest(toEngineDeleteObjectRequest(deleteObject)));
    }
	
	private S3DeleteObjectRequest toEngineDeleteObjectRequest(DeleteObject deleteObject) {
		S3DeleteObjectRequest request = new S3DeleteObjectRequest();
		request.setAccessKey(deleteObject.getAWSAccessKeyId());
		request.setRequestTimestamp(deleteObject.getTimestamp());
		request.setSignature(deleteObject.getSignature());
		request.setBucketName(deleteObject.getBucket());
		request.setKey(deleteObject.getKey());
		return request;
	}
	
	private DeleteObjectResponse toDeleteObjectResponse(S3Response engineResponse) {
		DeleteObjectResponse response = new DeleteObjectResponse();
		Status status = new Status();
		status.setCode(engineResponse.getResultCode());
		status.setDescription(engineResponse.getResultDescription());
		response.setDeleteObjectResponse(status);
		return response;
	}
 
	public SetObjectAccessControlPolicyResponse setObjectAccessControlPolicy(
          SetObjectAccessControlPolicy setObjectAccessControlPolicy) {
		
		S3SetObjectAccessControlPolicyRequest request = new S3SetObjectAccessControlPolicyRequest();
		request.setAccessKey(setObjectAccessControlPolicy.getAWSAccessKeyId());
		request.setRequestTimestamp(setObjectAccessControlPolicy.getTimestamp());
		request.setSignature(setObjectAccessControlPolicy.getSignature());
		request.setBucketName(setObjectAccessControlPolicy.getBucket());
		request.setKey(setObjectAccessControlPolicy.getKey());
		request.setAcl(toEngineAccessControlList(setObjectAccessControlPolicy.getAccessControlList()));
		
		engine.handleRequest(request);
		SetObjectAccessControlPolicyResponse response = new SetObjectAccessControlPolicyResponse();
		return response;
    }
 
	public PutObjectInlineResponse putObjectInline (PutObjectInline putObjectInline) {
		// after authentication, we should setup user context
		UserContext.current().initContext("TODO", "TODO", "TODO", "TODO");
		return toPutObjectInlineResponse(engine.handleRequest(toEnginePutObjectInlineRequest(putObjectInline)));
    }
	
	private S3PutObjectInlineRequest toEnginePutObjectInlineRequest(PutObjectInline putObjectInline) {
		S3PutObjectInlineRequest request = new S3PutObjectInlineRequest();
		request.setAccessKey(putObjectInline.getAWSAccessKeyId());
		request.setRequestTimestamp(putObjectInline.getTimestamp());
		request.setSignature(putObjectInline.getSignature());
		request.setBucketName(putObjectInline.getBucket());
		request.setContentLength(putObjectInline.getContentLength());
		request.setKey(putObjectInline.getKey());
		request.setData(putObjectInline.getData());
		request.setMetaEntries(toEngineMetaEntries(putObjectInline.getMetadata()));
		request.setAcl(toEngineAccessControlList(putObjectInline.getAccessControlList()));
		
		return request;
	}
	
	private S3MetaDataEntry[] toEngineMetaEntries(MetadataEntry[] metaEntries) {
		if(metaEntries != null) {
			S3MetaDataEntry[] engineMetaEntries = new S3MetaDataEntry[metaEntries.length];
			for(int i = 0; i < metaEntries.length; i++) {
				engineMetaEntries[i] = new S3MetaDataEntry(); 
				engineMetaEntries[i].setName(metaEntries[i].getName());
				engineMetaEntries[i].setValue(metaEntries[i].getValue());
			}
			return engineMetaEntries;
		}
		return null;
	}
	
	private S3AccessControlList toEngineAccessControlList(AccessControlList acl) {
		if(acl == null)
			return null;
		
		S3AccessControlList engineAcl = new S3AccessControlList();
		
		Grant[] grants = acl.getGrant();
		if(grants != null) {
			for(Grant grant: grants) {
				S3Grant engineGrant = new S3Grant();

				Grantee grantee = grant.getGrantee();
				if(grantee instanceof CanonicalUser) {
					engineGrant.setGrantee(SAcl.GRANTEE_USER);
					engineGrant.setCanonicalUserID(((CanonicalUser)grantee).getID());
				} else {
					throw new UnsupportedOperationException("Unsupported grantee type: " + grantee.getClass().getCanonicalName()); 
				}
				
				Permission permission = grant.getPermission();
				String permissionValue = permission.getValue();
				if(permissionValue.equalsIgnoreCase("READ")) {
					engineGrant.setPermission(SAcl.PERMISSION_READ);
				} else if(permissionValue.equalsIgnoreCase("WRITE")) {
					engineGrant.setPermission(SAcl.PERMISSION_WRITE);
				} else if(permissionValue.equalsIgnoreCase("READ_ACP")) {
					engineGrant.setPermission(SAcl.PERMISSION_READ_ACL);
				} else if(permissionValue.equalsIgnoreCase("WRITE_ACP")) {
					engineGrant.setPermission(SAcl.PERMISSION_WRITE_ACL);
				} else if(permissionValue.equalsIgnoreCase("FULL_CONTROL")) {
					engineGrant.setPermission(SAcl.PERMISSION_FULL);
				} else {
					throw new UnsupportedOperationException("Unsupported permission: " + permissionValue); 
				}
				engineAcl.addGrant(engineGrant);
			}
		}
		return engineAcl;
	}
	
	private Grant[] toGrants(S3Grant[] engineGrants) {
		if(engineGrants != null) {
			Grant[] grants = new Grant[engineGrants.length];
			for(int i = 0; i < engineGrants.length; i++) {
				grants[i] = new Grant();
				
				Grantee grantee = null; 
				switch(engineGrants[i].getGrantee()) {
				case SAcl.GRANTEE_USER :
					grantee = new CanonicalUser();
					((CanonicalUser)grantee).setID(engineGrants[i].getCanonicalUserID());
					((CanonicalUser)grantee).setDisplayName("TODO");
					break;
					
				case SAcl.GRANTEE_PUBLIC :
				case SAcl.GRANTEE_CLOUD_COMMUNITY :
				default :
					throw new InternalErrorException("Unsupported grantee type");
				}
				grants[i].setGrantee(grantee);
				
				switch(engineGrants[i].getPermission()) {
				case SAcl.PERMISSION_READ:
					grants[i].setPermission(Permission.READ);
					break;
					
				case SAcl.PERMISSION_WRITE :
					grants[i].setPermission(Permission.WRITE);
					break;
					
				case SAcl.PERMISSION_READ_ACL :
					grants[i].setPermission(Permission.READ_ACP);
					break;
					
				case SAcl.PERMISSION_WRITE_ACL :
					grants[i].setPermission(Permission.WRITE_ACP);
					break;
					
				case SAcl.PERMISSION_FULL :
					grants[i].setPermission(Permission.FULL_CONTROL);
					break;
				}
			}
			return grants;
		}
		return null;
	}
	
	private PutObjectInlineResponse toPutObjectInlineResponse(S3PutObjectInlineResponse engineResponse) {
		PutObjectInlineResponse response = new PutObjectInlineResponse();
		
		PutObjectResult result = new PutObjectResult();
		result.setETag(engineResponse.getETag());
		result.setLastModified(engineResponse.getLastModified());
		
		response.setPutObjectInlineResponse(result);
		return response;
	}
}