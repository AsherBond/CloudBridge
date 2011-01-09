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
package com.cloud.bridge.service.controller.s3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

import javax.activation.DataHandler;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.DatatypeConverter;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMFactory;
import org.apache.axis2.databinding.utils.writer.MTOMAwareXMLSerializer;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.amazon.s3.CopyObjectResponse;
import com.amazon.s3.GetObjectAccessControlPolicyResponse;
import com.cloud.bridge.model.SAcl;
import com.cloud.bridge.model.SBucket;
import com.cloud.bridge.model.SHost;
import com.cloud.bridge.persist.dao.MultipartLoadDao;
import com.cloud.bridge.persist.dao.SBucketDao;
import com.cloud.bridge.persist.dao.UserCredentialsDao;
import com.cloud.bridge.service.S3BucketAdapter;
import com.cloud.bridge.service.S3Constants;
import com.cloud.bridge.service.S3RestServlet;
import com.cloud.bridge.service.S3SoapServiceImpl;
import com.cloud.bridge.service.ServiceProvider;
import com.cloud.bridge.service.ServletAction;
import com.cloud.bridge.service.UserContext;
import com.cloud.bridge.service.core.s3.S3AccessControlPolicy;
import com.cloud.bridge.service.core.s3.S3AuthParams;
import com.cloud.bridge.service.core.s3.S3CanonicalUser;
import com.cloud.bridge.service.core.s3.S3ConditionalHeaders;
import com.cloud.bridge.service.core.s3.S3CopyObjectRequest;
import com.cloud.bridge.service.core.s3.S3CopyObjectResponse;
import com.cloud.bridge.service.core.s3.S3DeleteObjectRequest;
import com.cloud.bridge.service.core.s3.S3Engine;
import com.cloud.bridge.service.core.s3.S3GetObjectAccessControlPolicyRequest;
import com.cloud.bridge.service.core.s3.S3GetObjectRequest;
import com.cloud.bridge.service.core.s3.S3GetObjectResponse;
import com.cloud.bridge.service.core.s3.S3ListBucketObjectEntry;
import com.cloud.bridge.service.core.s3.S3MetaDataEntry;
import com.cloud.bridge.service.core.s3.S3MultipartPart;
import com.cloud.bridge.service.core.s3.S3PutObjectInlineRequest;
import com.cloud.bridge.service.core.s3.S3PutObjectInlineResponse;
import com.cloud.bridge.service.core.s3.S3PutObjectRequest;
import com.cloud.bridge.service.core.s3.S3Response;
import com.cloud.bridge.service.core.s3.S3SetObjectAccessControlPolicyRequest;
import com.cloud.bridge.service.exception.NoSuchObjectException;
import com.cloud.bridge.service.exception.PermissionDeniedException;
import com.cloud.bridge.util.Converter;
import com.cloud.bridge.util.DateHelper;
import com.cloud.bridge.util.HeaderParam;
import com.cloud.bridge.util.ServletRequestDataSource;
import com.cloud.bridge.util.Tuple;

/**
 * @author Kelven Yang
 */
public class S3ObjectAction implements ServletAction {
    protected final static Logger logger = Logger.getLogger(S3ObjectAction.class);

    private DocumentBuilderFactory dbf = null;
	private OMFactory factory = OMAbstractFactory.getOMFactory();
	private XMLOutputFactory xmlOutFactory = XMLOutputFactory.newInstance();
    
	public S3ObjectAction() {
		dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware( true );

	}

	public void execute(HttpServletRequest request, HttpServletResponse response) 
	    throws IOException, XMLStreamException 
	{
		String method      = request.getMethod();
		String queryString = request.getQueryString();
		String copy        = null;
		
	    response.addHeader( "x-amz-request-id", UUID.randomUUID().toString());	

	    if ( method.equalsIgnoreCase( "GET" )) 
	    {		    	 
			 if ( queryString != null && queryString.length() > 0 ) 
			 {
				       if (queryString.startsWith("acl"))      executeGetObjectAcl(request, response);
				  else if (queryString.startsWith("uploadId")) executeListUploadParts(request, response);
			 } 
			 else executeGetObject(request, response);
		}
		else if (method.equalsIgnoreCase( "PUT" )) 
		{			
			 if ( queryString != null && queryString.length() > 0 ) 
			 {
				       if (queryString.startsWith("acl"))        executePutObjectAcl(request, response);
				  else if (queryString.startsWith("partNumber")) executeUploadPart(request, response);
			 } 
			 else if ( null != (copy = request.getHeader( "x-amz-copy-source" ))) 
			 {
				  executeCopyObject(request, response, copy.trim());
			 }
 		     else executePutObject(request, response);
		}
		else if (method.equalsIgnoreCase( "DELETE" )) 
		{
			 if ( queryString != null && queryString.length() > 0 ) 
			 {
			      if (queryString.startsWith("uploadId")) executeAbortMultipartUpload(request, response);			     
			 } 
			 else executeDeleteObject(request, response);
		}
		else if (method.equalsIgnoreCase( "HEAD" )) 
		{
			 executeHeadObject(request, response);
		}
		else if (method.equalsIgnoreCase( "POST" )) 
		{	
			 if ( queryString != null && queryString.length() > 0 ) 
			 {
			           if (queryString.startsWith("uploads"))  executeInitiateMultipartUpload(request, response);	
			      else if (queryString.startsWith("uploadId")) executeCompleteMultipartUpload(request, response);			     
			 } 
			 else executePostObject(request, response);
		}
		else throw new IllegalArgumentException( "Unsupported method in REST request");
	}

	
	private void executeCopyObject(HttpServletRequest request, HttpServletResponse response, String copy) 
	    throws IOException, XMLStreamException 
	{
        S3CopyObjectRequest engineRequest = new S3CopyObjectRequest();
        String versionId = null;
        
		String bucketName = (String)request.getAttribute(S3Constants.BUCKET_ATTR_KEY);
		String key        = (String)request.getAttribute(S3Constants.OBJECT_ATTR_KEY);
		String sourceBucketName = null;
		String sourceKey        = null;

		// [A] Parse the x-amz-copy-source header into usable pieces
		// -> is there a ?versionId= value
		int index = copy.indexOf( '?' );
		if (-1 != index)
		{
			versionId = copy.substring( index+1 );
			if (versionId.startsWith( "versionId=" )) engineRequest.setVersion( versionId.substring( 10 ));
			copy = copy.substring( 0, index );
		}
		
		// -> the value of copy should look like: "/bucket-name/object-name"
		index = copy.indexOf( '/' );
		if ( 0 != index )
			 throw new IllegalArgumentException( "Invalid x-amz-copy-sourse header value [" + copy + "]" );
		else copy = copy.substring( 1 );
		
		index = copy.indexOf( '/' );
		if ( -1 == index )
			 throw new IllegalArgumentException( "Invalid x-amz-copy-sourse header value [" + copy + "]" );
		
		sourceBucketName = copy.substring( 0, index );
		sourceKey        = copy.substring( index+1 );
			
		
		// [B] Set the object used in the SOAP request so it can do the bulk of the work for us
        engineRequest.setSourceBucketName( sourceBucketName );
        engineRequest.setSourceKey( sourceKey );
        engineRequest.setDestinationBucketName( bucketName );
        engineRequest.setDestinationKey( key );
    
        engineRequest.setDataDirective( request.getHeader( "x-amz-metadata-directive" ));
		engineRequest.setMetaEntries( extractMetaData( request ));
		engineRequest.setCannedAccess( request.getHeader( "x-amz-acl" ));
		engineRequest.setConditions( conditionalRequest( request, true ));
		
		
		// [C] Do the actual work and return the result
		S3CopyObjectResponse engineResponse = ServiceProvider.getInstance().getS3Engine().handleRequest( engineRequest );		
		
        versionId = engineResponse.getCopyVersion();
        if (null != versionId) response.addHeader( "x-amz-copy-source-version-id", versionId );
        versionId = engineResponse.getPutVersion();
        if (null != versionId) response.addHeader( "x-amz-version-id", versionId );
	     
		// -> serialize using the apache's Axiom classes
		CopyObjectResponse allBuckets = S3SoapServiceImpl.toCopyObjectResponse( engineResponse );

		OutputStream os = response.getOutputStream();
		response.setStatus(200);	
	    response.setContentType("text/xml; charset=UTF-8");
		XMLStreamWriter xmlWriter = xmlOutFactory.createXMLStreamWriter( os );
		String documentStart = new String( "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" );
		os.write( documentStart.getBytes());
		MTOMAwareXMLSerializer MTOMWriter = new MTOMAwareXMLSerializer( xmlWriter );
        allBuckets.serialize( new QName( "http://s3.amazonaws.com/doc/2006-03-01/", "CopyObjectResponse", "ns1" ), factory, MTOMWriter );
        xmlWriter.flush();
        xmlWriter.close();
        os.close();
	}

	private void executeGetObjectAcl(HttpServletRequest request, HttpServletResponse response) throws IOException 
	{
		String bucketName = (String)request.getAttribute(S3Constants.BUCKET_ATTR_KEY);
		String key        = (String)request.getAttribute(S3Constants.OBJECT_ATTR_KEY);

		S3GetObjectAccessControlPolicyRequest engineRequest = new S3GetObjectAccessControlPolicyRequest();
		engineRequest.setBucketName( bucketName );
		engineRequest.setKey( key );

		S3AccessControlPolicy engineResponse = ServiceProvider.getInstance().getS3Engine().handleRequest(engineRequest);
		
		// -> serialize using the apache's Axiom classes
		GetObjectAccessControlPolicyResponse onePolicy = S3SoapServiceImpl.toGetObjectAccessControlPolicyResponse( engineResponse );
	
		try {
		    OutputStream os = response.getOutputStream();
		    response.setStatus(200);	
	        response.setContentType("text/xml; charset=UTF-8");
		    XMLStreamWriter xmlWriter = xmlOutFactory.createXMLStreamWriter( os );
		    String documentStart = new String( "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" );
		    os.write( documentStart.getBytes());
		    MTOMAwareXMLSerializer MTOMWriter = new MTOMAwareXMLSerializer( xmlWriter );
            onePolicy.serialize( new QName( "http://s3.amazonaws.com/doc/2006-03-01/", "GetObjectAccessControlPolicyResponse", "ns1" ), factory, MTOMWriter );
            xmlWriter.flush();
            xmlWriter.close();
            os.close();
		}
		catch( XMLStreamException e ) {
			throw new IOException( e.toString());
		}
	}
	
	private void executePutObjectAcl(HttpServletRequest request, HttpServletResponse response) throws IOException 
	{
		S3PutObjectRequest putRequest = null;
		
		// -> reuse the Access Control List parsing code that was added to support DIME
		String bucketName = (String)request.getAttribute(S3Constants.BUCKET_ATTR_KEY);
		String key        = (String)request.getAttribute(S3Constants.OBJECT_ATTR_KEY);
		try {
		    putRequest = S3RestServlet.toEnginePutObjectRequest( request.getInputStream());
		}
		catch( Exception e ) {
			throw new IOException( e.toString());
		}
		
		// -> reuse the SOAP code to save the passed in ACLs
		S3SetObjectAccessControlPolicyRequest engineRequest = new S3SetObjectAccessControlPolicyRequest();
		engineRequest.setBucketName( bucketName );
		engineRequest.setKey( key );
		engineRequest.setAcl( putRequest.getAcl());
		
	    S3Response engineResponse = ServiceProvider.getInstance().getS3Engine().handleRequest(engineRequest);	
	    response.setStatus( engineResponse.getResultCode());
	}

	private void executeGetObject(HttpServletRequest request, HttpServletResponse response) throws IOException 
	{
		String   bucket    = (String) request.getAttribute(S3Constants.BUCKET_ATTR_KEY);
		String   key       = (String) request.getAttribute(S3Constants.OBJECT_ATTR_KEY);
		String[] paramList = null;
		
		S3GetObjectRequest engineRequest = new S3GetObjectRequest();
		engineRequest.setBucketName(bucket);
		engineRequest.setKey(key);
		engineRequest.setInlineData(true);
		engineRequest.setReturnData(true);	
		//engineRequest.setReturnMetadata(true);
		engineRequest = setRequestByteRange( request, engineRequest );

		// -> is this a request for a specific version of the object?  look for "versionId=" in the query string
		String queryString = request.getQueryString();
		if (null != queryString) {
			paramList = queryString.split( "[&=]" );
		    if (null != paramList) engineRequest.setVersion( returnParameter( paramList, "versionId" ));
		}

		S3GetObjectResponse engineResponse = ServiceProvider.getInstance().getS3Engine().handleRequest( engineRequest );			
		response.setStatus( engineResponse.getResultCode());
		
		String deleteMarker = engineResponse.getDeleteMarker();
		if ( null != deleteMarker ) {
			 response.addHeader( "x-amz-delete-marker", "true" );	
			 response.addHeader( "x-amz-version-id", deleteMarker );
		}
		else {
		     String version = engineResponse.getVersion();
		     if (null != version) response.addHeader( "x-amz-version-id", version );
		}
		
		
		// -> was the get conditional?
		if (!conditionPassed( request, response, engineResponse.getLastModified().getTime(), engineResponse.getETag())) 
			return;
	

		// -> is there data to return
		// -> from the Amazon REST documentation it appears that Meta data is only returned as part of a HEAD request
		//returnMetaData( engineResponse, response );
			
		DataHandler dataHandler = engineResponse.getData();
		if (dataHandler != null) {
			response.addHeader("ETag", engineResponse.getETag());
			response.addHeader("Last-Modified", DateHelper.getDateDisplayString(
				DateHelper.GMT_TIMEZONE, engineResponse.getLastModified().getTime(), "E, d MMM yyyy HH:mm:ss z"));

			response.setContentLength((int)engineResponse.getContentLength());			
			S3RestServlet.writeResponse(response, dataHandler.getInputStream());
		}
	}

	private void executePutObject(HttpServletRequest request, HttpServletResponse response) throws IOException  
	{
		String continueHeader = request.getHeader( "Expect" );
		if (continueHeader != null && continueHeader.equalsIgnoreCase("100-continue")) {
			S3RestServlet.writeResponse(response, "HTTP/1.1 100 Continue\r\n");
		}

		String contentType = request.getHeader( "Content-Type" );
		long contentLength = Converter.toLong(request.getHeader("Content-Length"), 0);

		String bucket = (String) request.getAttribute(S3Constants.BUCKET_ATTR_KEY);
		String key    = (String) request.getAttribute(S3Constants.OBJECT_ATTR_KEY);
		S3PutObjectInlineRequest engineRequest = new S3PutObjectInlineRequest();
		engineRequest.setBucketName(bucket);
		engineRequest.setKey(key);
		engineRequest.setContentLength(contentLength);
		engineRequest.setMetaEntries( extractMetaData( request ));
		engineRequest.setCannedAccess( request.getHeader( "x-amz-acl" ));

		DataHandler dataHandler = new DataHandler(new ServletRequestDataSource(request));
		engineRequest.setData(dataHandler);

		S3PutObjectInlineResponse engineResponse = ServiceProvider.getInstance().getS3Engine().handleRequest(engineRequest);
		response.setHeader("ETag", engineResponse.getETag());
		String version = engineResponse.getVersion();
		if (null != version) response.addHeader( "x-amz-version-id", version );		
	}

	/**
	 * Once versioining is turned on then to delete an object requires specifying a version 
	 * parameter.   A deletion marker is set once versioning is turned on in a bucket.
	 */
	private void executeDeleteObject(HttpServletRequest request, HttpServletResponse response) throws IOException 
	{
		String   bucket    = (String) request.getAttribute(S3Constants.BUCKET_ATTR_KEY);
		String   key       = (String) request.getAttribute(S3Constants.OBJECT_ATTR_KEY);
		String[] paramList = null;
			
		S3DeleteObjectRequest engineRequest = new S3DeleteObjectRequest();
		engineRequest.setBucketName(bucket);
		engineRequest.setKey(key);

		// -> is this a request for a specific version of the object?  look for "versionId=" in the query string
		String queryString = request.getQueryString();
		if (null != queryString) {
			paramList = queryString.split( "[&=]" );
		    if (null != paramList) engineRequest.setVersion( returnParameter( paramList, "versionId" ));
		}
		
		S3Response engineResponse = ServiceProvider.getInstance().getS3Engine().handleRequest( engineRequest );
		
		response.setStatus( engineResponse.getResultCode());	
		String version = engineRequest.getVersion();
		if (null != version) response.addHeader( "x-amz-version-id", version );		
	}

	private void executeHeadObject(HttpServletRequest request, HttpServletResponse response) throws IOException 
	{
		String   bucket    = (String) request.getAttribute(S3Constants.BUCKET_ATTR_KEY);
		String   key       = (String) request.getAttribute(S3Constants.OBJECT_ATTR_KEY);
		String[] paramList = null;
			
		S3GetObjectRequest engineRequest = new S3GetObjectRequest();
		engineRequest.setBucketName(bucket);
		engineRequest.setKey(key);
		engineRequest.setInlineData(true);    // -> need to set so we get ETag etc returned
		engineRequest.setReturnData(true);
		engineRequest.setReturnMetadata(true);
		engineRequest = setRequestByteRange( request, engineRequest );
		
		// -> is this a request for a specific version of the object?  look for "versionId=" in the query string
		String queryString = request.getQueryString();
		if (null != queryString) {
			paramList = queryString.split( "[&=]" );
		    if (null != paramList) engineRequest.setVersion( returnParameter( paramList, "versionId" ));
		}

		S3GetObjectResponse engineResponse = ServiceProvider.getInstance().getS3Engine().handleRequest( engineRequest );		
		response.setStatus( engineResponse.getResultCode());
		
		String deleteMarker = engineResponse.getDeleteMarker();
		if ( null != deleteMarker ) {
			 response.addHeader( "x-amz-delete-marker", "true" );	
			 response.addHeader( "x-amz-version-id", deleteMarker );
		}
		else {
		     String version = engineResponse.getVersion();
		     if (null != version) response.addHeader( "x-amz-version-id", version );
		}
		
		// -> was the head request conditional?
		if (!conditionPassed( request, response, engineResponse.getLastModified().getTime(), engineResponse.getETag())) 
			return;	
		
		
		// -> for a head request we return everything except the data
		returnMetaData( engineResponse, response );
		
		DataHandler dataHandler = engineResponse.getData();
		if (dataHandler != null) {
			response.addHeader("ETag", engineResponse.getETag());
			response.addHeader("Last-Modified", DateHelper.getDateDisplayString(
				DateHelper.GMT_TIMEZONE, engineResponse.getLastModified().getTime(), "E, d MMM yyyy HH:mm:ss z"));

			response.setContentLength((int)engineResponse.getContentLength());			
		}	
	}

	// There is a problem with POST since the 'Signature' and 'AccessKey' parameters are not
	// determined until we hit this function (i.e., they are encoded in the body of the message
	// they are not HTTP request headers).  All the values we used to get in the request headers 
	// are not encoded in the request body.
    //
	public void executePostObject( HttpServletRequest request, HttpServletResponse response ) throws IOException 
	{
		String bucket = (String) request.getAttribute(S3Constants.BUCKET_ATTR_KEY);
		String contentType  = request.getHeader( "Content-Type" );
		int boundaryIndex   = contentType.indexOf( "boundary=" );
		String boundary     = "--" + (contentType.substring( boundaryIndex + 9 ));
		String lastBoundary = boundary + "--";
		
		InputStreamReader isr = new InputStreamReader( request.getInputStream());
		BufferedReader br = new BufferedReader( isr );
		
		StringBuffer temp = new StringBuffer();
		String oneLine = null;
		String name = null;
		String value = null;
		String metaName = null;   // -> after stripped off the x-amz-meta-
		boolean isMetaTag = false;
		int countMeta = 0;
		int state = 0;
		
		// [A] First parse all the parts out of the POST request and message body
		// -> bucket name is still encoded in a Host header
	   	S3AuthParams params = new S3AuthParams();
		List<S3MetaDataEntry> metaSet = new ArrayList<S3MetaDataEntry>();  
		S3PutObjectInlineRequest engineRequest = new S3PutObjectInlineRequest();
		engineRequest.setBucketName( bucket );
		
		// -> the last body part contains the content that is used to write the S3 object, all
		//    other body parts are header values
		while( null != (oneLine = br.readLine())) 
		{
			if ( oneLine.startsWith( lastBoundary )) 
			{
				 // -> this is the data of the object to put
				 if (0 < temp.length()) 
				 {
				     value = temp.toString();
				     temp.setLength( 0 );
				     
				 	 engineRequest.setContentLength( value.length());	
				 	 engineRequest.setDataAsString( value );
				 }
				 break;
			}
			else if ( oneLine.startsWith( boundary )) 
			{
				 // -> this is the header data
				 if (0 < temp.length()) 
				 {
				     value = temp.toString().trim();
				     temp.setLength( 0 );				     
				     //System.out.println( "param: " + name + " = " + value );
				     
				          if (name.equalsIgnoreCase( "key" )) {
				        	  engineRequest.setKey( value );
				          }
				     else if (name.equalsIgnoreCase( "x-amz-acl" )) {
				    	      engineRequest.setCannedAccess( value );
				     }
				     else if (isMetaTag) {
			            	  S3MetaDataEntry oneMeta = new S3MetaDataEntry();
			            	  oneMeta.setName( metaName );
			            	  oneMeta.setValue( value );
			            	  metaSet.add( oneMeta );
			            	  countMeta++;
			            	  metaName = null;
				     }
				       
				     // -> build up the headers so we can do authentication on this POST
				     HeaderParam oneHeader = new HeaderParam();
				     oneHeader.setName( name );
				     oneHeader.setValue( value );
				     params.addHeader( oneHeader );
				 }
				 state = 1;
			}
			else if (1 == state && 0 == oneLine.length()) 
			{
				 // -> data of a body part starts here 
				 state = 2;
			}
			else if (1 == state) 
			{
				 // -> the name of the 'name-value' pair is encoded in the Content-Disposition header
				 if (oneLine.startsWith( "Content-Disposition: form-data;")) 
				 {
					 isMetaTag = false;
					 int nameOffset = oneLine.indexOf( "name=" );
					 if (-1 != nameOffset) 
					 {
						 name = oneLine.substring( nameOffset+5 );
						 if (name.startsWith( "\"" )) name = name.substring( 1 );
						 if (name.endsWith( "\"" ))   name = name.substring( 0, name.length()-1 );
						 name = name.trim();
						 
						 if (name.startsWith( "x-amz-meta-" )) {
							 metaName  = name.substring( 11 );
							 isMetaTag = true;
						 }
					 }
				 }
			}
			else if (2 == state) 
			{
				 // -> the body parts data may take up multiple lines
                 //System.out.println( oneLine.length() + " body data: " + oneLine );
                 temp.append( oneLine );
			}
//			else System.out.println( oneLine.length() + " preamble: " + oneLine );
		}
		
		
		// [B] Authenticate the POST request after we have all the headers
		try {
		    S3RestServlet.authenticateRequest( request, params );
		}
		catch( Exception e ) {
			throw new IOException( e.toString());
		}
		
		// [C] Perform the request
        if (0 < countMeta) engineRequest.setMetaEntries( metaSet.toArray(new S3MetaDataEntry[0]));
		S3PutObjectInlineResponse engineResponse = ServiceProvider.getInstance().getS3Engine().handleRequest( engineRequest );
		response.setHeader("ETag", engineResponse.getETag());
		String version = engineResponse.getVersion();
		if (null != version) response.addHeader( "x-amz-version-id", version );		
	}

	/**
	 * Save all the information about the multipart upload request in the database so once it is finished
	 * (in the future) we can create the real S3 object.
	 * 
	 * @throws IOException
	 */
	private void executeInitiateMultipartUpload( HttpServletRequest request, HttpServletResponse response ) throws IOException
    {
		// -> this request is via a POST which typically has its auth parameters inside the message
		try {
	        S3RestServlet.authenticateRequest( request, S3RestServlet.extractRequestHeaders( request ));
	    }
		catch( Exception e ) {
			throw new IOException( e.toString());
		}

		String   bucket = (String) request.getAttribute(S3Constants.BUCKET_ATTR_KEY);
		String   key    = (String) request.getAttribute(S3Constants.OBJECT_ATTR_KEY);
		String   cannedAccess = request.getHeader( "x-amz-acl" );
		S3MetaDataEntry[] meta = extractMetaData( request );
        
        // -> the S3 engine has easy access to all the privileged checking code
		S3PutObjectInlineRequest engineRequest = new S3PutObjectInlineRequest();
		engineRequest.setBucketName(bucket);
		engineRequest.setKey(key);
		engineRequest.setCannedAccess( cannedAccess );
		engineRequest.setMetaEntries( meta );
		S3PutObjectInlineResponse engineResponse = ServiceProvider.getInstance().getS3Engine().initiateMultipartUpload( engineRequest ); 
		int result = engineResponse.getResultCode();
		response.setStatus( result );
        if (200 != result) return;
        
        // -> there is no SOAP version of this function
		StringBuffer xml = new StringBuffer();
        xml.append( "<?xml version=\"1.0\" encoding=\"utf-8\"?>" );
        xml.append( "<InitiateMultipartUploadResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">" );
        xml.append( "<Bucket>" ).append( bucket ).append( "</Bucket>" );
        xml.append( "<Key>" ).append( key ).append( "</Key>" );
        xml.append( "<UploadId>" ).append( engineResponse.getUploadId()).append( "</UploadId>" );
        xml.append( "</InitiateMultipartUploadResult>" );
      
	    response.setContentType("text/xml; charset=UTF-8");
    	S3RestServlet.endResponse(response, xml.toString());
	}
	
	private void executeUploadPart( HttpServletRequest request, HttpServletResponse response ) throws IOException 
	{
		String continueHeader = request.getHeader( "Expect" );
		if (continueHeader != null && continueHeader.equalsIgnoreCase("100-continue")) {
			S3RestServlet.writeResponse(response, "HTTP/1.1 100 Continue\r\n");
		}
		
		String   bucket = (String) request.getAttribute(S3Constants.BUCKET_ATTR_KEY);
		String   key    = (String) request.getAttribute(S3Constants.OBJECT_ATTR_KEY);
		int partNumber  = -1;
		int uploadId    = -1;

		long contentLength = Converter.toLong(request.getHeader("Content-Length"), 0);

		String md5 = request.getHeader( "Content-MD5" );

		String temp = request.getParameter("uploadId");
    	if (null != temp) uploadId = Integer.parseInt( temp );

		temp = request.getParameter("partNumber");
    	if (null != temp) partNumber = Integer.parseInt( temp );
    	if (partNumber < 1 || partNumber > 10000) {
			logger.error("uploadPart invalid part number " + partNumber );	
			response.setStatus(416);
            return;
    	}
    	
    	// -> verification
		try {
    	    MultipartLoadDao uploadDao = new MultipartLoadDao();
    	    if (null == uploadDao.multipartExits( uploadId )) {
    	    	response.setStatus(404);
    	    	return;
    	    }
    	    
    	    // -> another requirement is that only the upload initiator can upload parts
    	    String initiator = uploadDao.getInitiator( uploadId );
    	    if (null == initiator || !initiator.equals( UserContext.current().getAccessKey())) {
    	    	response.setStatus(403);
    	    	return;   	    	
    	    }
		}
		catch( Exception e ) {
		    logger.error("executeUploadPart failed due to " + e.getMessage(), e);	
			response.setStatus(500);
			return;
		}

		S3PutObjectInlineRequest engineRequest = new S3PutObjectInlineRequest();
		engineRequest.setBucketName(bucket);
		engineRequest.setKey(key);
		engineRequest.setContentLength(contentLength);
		DataHandler dataHandler = new DataHandler(new ServletRequestDataSource(request));
		engineRequest.setData(dataHandler);

		S3PutObjectInlineResponse engineResponse = ServiceProvider.getInstance().getS3Engine().saveUploadPart( engineRequest, uploadId, partNumber ); 
		if (null != engineResponse.getETag()) response.setHeader("ETag", engineResponse.getETag());
		response.setStatus(engineResponse.getResultCode());
	}
	
	/**
	 * This function is required to both parsing XML on the request and return XML as part of its result.
	 * 
	 * @param request
	 * @param response
	 * @throws IOException
	 */
	private void executeCompleteMultipartUpload( HttpServletRequest request, HttpServletResponse response ) throws IOException
	{
		// [A] This request is via a POST which typically has its auth parameters inside the message
		try {
	        S3RestServlet.authenticateRequest( request, S3RestServlet.extractRequestHeaders( request ));
	    }
		catch( Exception e ) {
			throw new IOException( e.toString());
		}

		String bucket = (String) request.getAttribute(S3Constants.BUCKET_ATTR_KEY);
		String key    = (String) request.getAttribute(S3Constants.OBJECT_ATTR_KEY);
		S3MultipartPart[] parts = null;
		S3MetaDataEntry[] meta  = null;
		String cannedAccess = null;
		int uploadId    = -1;
		
        //  -> Amazon defines to keep connection alive by sending whitespace characters until done
        OutputStream os = response.getOutputStream();

		String temp = request.getParameter("uploadId");
    	if (null != temp) uploadId = Integer.parseInt( temp );
    	
		
		// [B] Look up all the uploaded body parts and related info
		try {
    	    MultipartLoadDao uploadDao = new MultipartLoadDao();
    	    if (null == uploadDao.multipartExits( uploadId )) {
    	    	response.setStatus(404);
    			returnErrorXML( 404, "NotFound", os );
    	    	return;
    	    }
    	    
    	    // -> another requirement is that only the upload initiator can upload parts
    	    String initiator = uploadDao.getInitiator( uploadId );
    	    if (null == initiator || !initiator.equals( UserContext.current().getAccessKey())) {
    	    	response.setStatus(403);
    			returnErrorXML( 403, "Forbidden", os );
    	    	return;   	    	
    	    }
    	    
    	    parts = uploadDao.getParts( uploadId, 10000, 0 );
    	    meta  = uploadDao.getMeta( uploadId );
    	    cannedAccess = uploadDao.getCannedAccess( uploadId );
		}
		catch( Exception e ) {
		    logger.error("executeCompleteMultipartUpload failed due to " + e.getMessage(), e);	
			response.setStatus(500);
			returnErrorXML( 500, "InternalError", os );
			return;
		}
		
		
		// [C] Parse the given XML body part and perform error checking
		Tuple<Integer,String> match = verifyParts( request.getInputStream(), parts );
		if (200 != match.getFirst().intValue()) {
			response.setStatus(match.getFirst().intValue());
			returnErrorXML( match.getFirst().intValue(), match.getSecond(), os );
			return;
		}

		
		// [D] Ask the engine to create a newly re-constituted object
		S3PutObjectInlineRequest engineRequest = new S3PutObjectInlineRequest();
		engineRequest.setBucketName(bucket);
		engineRequest.setKey(key);
		engineRequest.setMetaEntries(meta);
		engineRequest.setCannedAccess(cannedAccess);

		S3PutObjectInlineResponse engineResponse = ServiceProvider.getInstance().getS3Engine().concatentateMultipartUploads( response, engineRequest, parts, os );
		int result = engineResponse.getResultCode();
		// -> free all multipart state since we now have one concatentated object
		if (200 == result) executeAbortMultipartUpload( request, response );
		
		// -> if all successful then clean up all left over parts
		if ( 200 == result ) 
	    {
 	 	     StringBuffer xml = new StringBuffer();
             xml.append( "<?xml version=\"1.0\" encoding=\"utf-8\"?>" );
             xml.append( "<CompleteMultipartUploadResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">" );
             xml.append( "<Location>" ).append( "http://" + bucket + ".s3.amazonaws.com/" + key ).append( "</Location>" );
             xml.append( "<Bucket>" ).append( bucket ).append( "</Bucket>" );
             xml.append( "<Key>" ).append( key ).append( "</Key>" );
             xml.append( "<ETag>" ).append( engineResponse.getETag()).append( "</<ETag>" );
             xml.append( "</CompleteMultipartUploadResult>" );
             os.write( xml.toString().getBytes());
             os.close();
	    }
		else returnErrorXML( result, null, os );
	}
	
	private void executeAbortMultipartUpload( HttpServletRequest request, HttpServletResponse response ) throws IOException 
	{
		String bucket = (String) request.getAttribute(S3Constants.BUCKET_ATTR_KEY);
		int uploadId  = -1;

		String temp = request.getParameter("uploadId");
    	if (null != temp) uploadId = Integer.parseInt( temp );
    	
    	try {
			int result = ServiceProvider.getInstance().getS3Engine().freeUploadParts( bucket, uploadId ); 
            response.setStatus( result );
	    }
		catch( Exception e ) {
		    logger.error("Multipart Upload cleanup failed due to " + e.getMessage(), e);	
		    response.setStatus(500);
		}
	}
	
	private void executeListUploadParts( HttpServletRequest request, HttpServletResponse response ) throws IOException 
	{
		String bucketName = (String) request.getAttribute(S3Constants.BUCKET_ATTR_KEY);
		String key    = (String) request.getAttribute(S3Constants.OBJECT_ATTR_KEY);
		String owner  = null;
		String initiator =  null;
		S3MultipartPart[] parts = null;
		int remaining   = 0;
		int uploadId    = -1;
		int maxParts    = 1000;
		int partMarker  = 0;
		int nextMarker  = 0;

		String temp = request.getParameter("uploadId");
    	if (null != temp) uploadId = Integer.parseInt( temp );

		temp = request.getParameter("max-parts");
    	if (null != temp) {
    		maxParts = Integer.parseInt( temp );
    		if (maxParts > 1000 || maxParts < 0) maxParts = 1000;
    	}

		temp = request.getParameter("part-number-marker");
    	if (null != temp) partMarker = Integer.parseInt( temp );

    	
		// -> does the bucket exist, we may need it to verify access permissions
		SBucketDao bucketDao = new SBucketDao();
		SBucket bucket = bucketDao.getByName(bucketName);
		if (bucket == null) {
			logger.error( "listUploadParts failed since " + bucketName + " does not exist" );
	    	response.setStatus(404);
	    	return;
		}
	
    	try {
	        MultipartLoadDao uploadDao = new MultipartLoadDao();
	        if (null == (owner = uploadDao.multipartExits( uploadId ))) {
	    	   response.setStatus(404);
	    	   return;
	        }
	        
    	    // -> the multipart initiator or bucket owner can do this action
    	    initiator = uploadDao.getInitiator( uploadId );
    	    if (null == initiator || !initiator.equals( UserContext.current().getAccessKey())) 
    	    {
    	    	try {
    	    	    // -> write permission on a bucket allows a PutObject / DeleteObject action on any object in the bucket
    			    S3Engine.accessAllowed( "SBucket", bucket.getId(), SAcl.PERMISSION_WRITE );
    	    	}
    	    	catch (PermissionDeniedException e) {
    	    		response.setStatus(403);
    	    		return;
    	    	}
    	    }
	    
	        parts = uploadDao.getParts( uploadId, maxParts, partMarker );
	        remaining = uploadDao.numParts( uploadId, partMarker+maxParts );
    	}
		catch( Exception e ) {
			logger.error("List Uploads failed due to " + e.getMessage(), e);	
			response.setStatus(500);
		}

	        
		StringBuffer xml = new StringBuffer();
	    xml.append( "<?xml version=\"1.0\" encoding=\"utf-8\"?>" );
	    xml.append( "<ListPartsResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">" );
	    xml.append( "<Bucket>" ).append( bucket ).append( "</Bucket>" );
	    xml.append( "<Key>" ).append( key ).append( "</Key>" );
	    xml.append( "<UploadId>" ).append( uploadId ).append( "</UploadId>" );
	      
	    // -> currently we just have the access key and have no notion of a display name
	    xml.append( "<Initiator>" );
	    xml.append( "<ID>" ).append( initiator ).append( "</ID>" );
	    xml.append( "<DisplayName></DisplayName>" );
	    xml.append( "</Initiator>" );
	    xml.append( "<Owner>" );
	    xml.append( "<ID>" ).append( owner ).append( "</ID>" );
	    xml.append( "<DisplayName></DisplayName>" );
	    xml.append( "</Owner>" );       
	 
		StringBuffer partsList = new StringBuffer();
	    for( int i=0; i < parts.length; i++ ) 
	    {
	        S3MultipartPart onePart = parts[i];
	        if (null == onePart) break;
	        	
	        nextMarker = onePart.getPartNumber();
	        partsList.append( "<Part>" );
	        partsList.append( "<PartNumber>" ).append( nextMarker ).append( "</PartNumber>" );
	        partsList.append( "<LastModified>" ).append( DatatypeConverter.printDateTime( onePart.getLastModified())).append( "</LastModified>" );
	        partsList.append( "<ETag>" ).append( onePart.getETag()).append( "</ETag>" );
	        partsList.append( "<Size>" ).append( onePart.getSize()).append( "</Size>" );
	        partsList.append( "</Part>" );        	
	    }  
	        
	    xml.append( "<StorageClass>STANDARD</StorageClass>" );
	    xml.append( "<PartNumberMarker>" ).append( partMarker ).append( "</PartNumberMarker>" );
	    xml.append( "<NextPartNumberMarker>" ).append( nextMarker ).append( "</NextPartNumberMarker>" );
	    xml.append( "<MaxParts>" ).append( maxParts ).append( "</MaxParts>" );   
	    xml.append( "<IsTruncated>" ).append((0 < remaining ? "true" : "false" )).append( "</IsTruncated>" );

	    xml.append( partsList.toString());
	    xml.append( "</ListPartsResult>" );
	      
		response.setStatus(200);
		response.setContentType("text/xml; charset=UTF-8");
	    S3RestServlet.endResponse(response, xml.toString());
	}
	
	/**
	 * Support the "Range: bytes=0-399" header with just one byte range.
	 * @param request
	 * @param engineRequest
	 * @return
	 */
	private S3GetObjectRequest setRequestByteRange( HttpServletRequest request, S3GetObjectRequest engineRequest ) 
	{
		String temp = request.getHeader( "Range" );
		if (null == temp) return engineRequest;
		
		int offset = temp.indexOf( "=" );
		if (-1 != offset) 
		{
			String range = temp.substring( offset+1 );
		
		    String[] parts = range.split( "-" );
		    if (2 >= parts.length) {
		    	// -> the end byte is inclusive
			    engineRequest.setByteRangeStart( Long.parseLong(parts[0]));
			    engineRequest.setByteRangeEnd(   Long.parseLong(parts[1])+1);
		    }
		}	
		return engineRequest;
	}
	
	private S3ConditionalHeaders conditionalRequest( HttpServletRequest request, boolean isCopy ) 
	{
		S3ConditionalHeaders headers = new S3ConditionalHeaders();
		
		if (isCopy) {
			headers.setModifiedSince( request.getHeader( "x-amz-copy-source-if-modified-since" ));	
			headers.setUnModifiedSince( request.getHeader( "x-amz-copy-source-if-unmodified-since" ));	
			headers.setMatch( request.getHeader( "x-amz-copy-source-if-match" ));	
			headers.setNoneMatch( request.getHeader( "x-amz-copy-source-if-none-match" ));	
		}
		else {
		    headers.setModifiedSince( request.getHeader( "If-Modified-Since" ));
		    headers.setUnModifiedSince( request.getHeader( "If-Unmodified-Since" ));
		    headers.setMatch( request.getHeader( "If-Match" ));
		    headers.setNoneMatch( request.getHeader( "If-None-Match" ));
		}
        return headers;
	}
	
	private boolean conditionPassed( HttpServletRequest request, HttpServletResponse response, Date lastModified, String ETag ) 
	{	
		S3ConditionalHeaders ifCond = conditionalRequest( request, false );
		
		if (0 > ifCond.ifModifiedSince( lastModified )) {
			response.setStatus( 304 );
			return false;
		}
		if (0 > ifCond.ifUnmodifiedSince( lastModified )) {
			response.setStatus( 412 );
			return false;
		}
		if (0 > ifCond.ifMatchEtag( ETag )) {
			response.setStatus( 412 );
			return false;
		}
		if (0 > ifCond.ifNoneMatchEtag( ETag )) {
			response.setStatus( 412 );
			return false;
		}	
		return true;
	}
	
	/**
	 * Return the saved object's meta data back to the client as HTTP "x-amz-meta-" headers.
	 * This function is constructing an HTTP header and these headers have a defined syntax
	 * as defined in rfc2616.   Any characters that could cause an invalid HTTP header will
	 * prevent that meta data from being returned via the REST call (as is defined in the Amazon
	 * spec).   These characters can be defined if using the SOAP API as well as the REST API.
	 * 
	 * @param engineResponse
	 * @param response
	 */
	private void returnMetaData( S3GetObjectResponse engineResponse, HttpServletResponse response ) 
	{ 
		boolean ignoreMeta   = false;
		int     ignoredCount = 0;
		
	    S3MetaDataEntry[] metaSet = engineResponse.getMetaEntries();
	    for( int i=0; null != metaSet && i < metaSet.length; i++ ) 
	    {
		   String name      = metaSet[i].getName();
		   String value     = metaSet[i].getValue();
		   byte[] nameBytes = name.getBytes();
		   ignoreMeta = false;
		   
		   // -> cannot have control characters (octets 0 - 31) and DEL (127), in an HTTP header
		   for( int j=0; j < name.length(); j++ ) {
			   if ((0 <= nameBytes[j] && 31 >= nameBytes[j]) || 127 == nameBytes[j]) {
				   ignoreMeta = true;
				   break;
			   }
		   }
		   
		   // -> cannot have HTTP separators in an HTTP header 
		   if (-1 != name.indexOf('(')  || -1 != name.indexOf(')') || -1 != name.indexOf('@')  || 
			   -1 != name.indexOf('<')  || -1 != name.indexOf('>') || -1 != name.indexOf('\"') ||
			   -1 != name.indexOf('[')  || -1 != name.indexOf(']') || -1 != name.indexOf('=')  ||	   
			   -1 != name.indexOf(',')  || -1 != name.indexOf(';') || -1 != name.indexOf(':')  ||
			   -1 != name.indexOf('\\') || -1 != name.indexOf('/') || -1 != name.indexOf(' ')  ||
			   -1 != name.indexOf('{')  || -1 != name.indexOf('}') || -1 != name.indexOf('?')  ||
			   -1 != name.indexOf('\t')
			  ) ignoreMeta = true;
		   
		   
		   if ( ignoreMeta )
			    ignoredCount++;
		   else response.addHeader( "x-amz-meta-" + name, value );
	    }
	    
	    if (0 < ignoredCount) response.addHeader( "x-amz-missing-meta", new String( "" + ignoredCount ));
	}		

	/**
	 * Extract the name and value of all meta data so it can be written with the
	 * object that is being 'PUT'.
	 * 
	 * @param request
	 * @return
	 */
	private S3MetaDataEntry[] extractMetaData( HttpServletRequest request ) 
	{
		List<S3MetaDataEntry> metaSet = new ArrayList<S3MetaDataEntry>();  
		int count = 0;
		
		Enumeration headers = request.getHeaderNames();
        while( headers.hasMoreElements()) 
        {
	        String key = (String)headers.nextElement();
	        if (key.startsWith( "x-amz-meta-" )) 
	        {
	        	String name  = key.substring( 11 );
	            String value = request.getHeader( key );
	            if (null != value) {
	            	S3MetaDataEntry oneMeta = new S3MetaDataEntry();
	            	oneMeta.setName( name );
	            	oneMeta.setValue( value );
	            	metaSet.add( oneMeta );
	            	count++;
	            }
	        }
        }		

        if ( 0 < count )
        	 return metaSet.toArray(new S3MetaDataEntry[0]);
        else return null;
	}
	
	/**
	 * @param paramList - name - value pairs with name at odd indexes
	 * @param find      - name string to return first found
	 * @return the value matching the found name 
	 */
	private String returnParameter( String[] paramList, String find ) 
	{
		int i=0;
		
		if (paramList == null) return null;
		
		while( i+2 <= paramList.length ) {
           if (paramList[i].equalsIgnoreCase( find )) return paramList[ i+1 ];
           i += 2;
        }
		return null;
	}
	
	private void returnErrorXML( int errorCode, String errorDescription, OutputStream os ) throws IOException
	{
 	    StringBuffer xml = new StringBuffer();
 	    
	    xml.append( "<?xml version=\"1.0\" encoding=\"utf-8\"?>" );
        xml.append( "<Error>" );
        
        if ( null != errorDescription )
             xml.append( "<Code>" ).append( errorDescription ).append( "</Code>" );
        else xml.append( "<Code>" ).append( errorCode ).append( "</Code>" );
        
        xml.append( "<Message>" ).append( "" ).append( "</Message>" );
        xml.append( "<RequestId>" ).append( "" ).append( "</RequestId>" );
        xml.append( "<HostId>" ).append( "" ).append( "</<HostId>" );
        xml.append( "</Error>" );	
        
        os.write( xml.toString().getBytes());
        os.close();
	}
	
	/**
	 * The Complete Multipart Upload function pass in the request body a list of
	 * all uploaded body parts.   It is required that we verify that list matches
	 * what was uploaded.
	 * 
	 * @param is
	 * @param parts
	 * @return error code, and error string
	 * @throws ParserConfigurationException, IOException, SAXException 
	 */
    private Tuple<Integer,String> verifyParts( InputStream is, S3MultipartPart[] parts ) 
    {
    	try {
		    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		    dbf.setNamespaceAware( true );
		
		    DocumentBuilder db  = dbf.newDocumentBuilder();
		    Document        doc = db.parse( is );
		    Node            parent     = null;
		    Node            contents   = null;
		    NodeList        children   = null;
		    String          temp       = null;
		    String          element    = null;
		    String          eTag       = null;
		    int             lastNumber = -1;
		    int             partNumber = -1;
		    int             count      = 0;

		    // -> handle with and without a namespace
		    NodeList nodeSet = doc.getElementsByTagNameNS( "http://s3.amazonaws.com/doc/2006-03-01/", "Part" );		
		    count = nodeSet.getLength();
		    if (0 == count) {
		    	nodeSet = doc.getElementsByTagName( "Part" );
			    count = nodeSet.getLength();
		    }
		    if (count != parts.length) return new Tuple<Integer, String>(400, "InvalidPart");

		    // -> get a list of all the children elements of the 'Part' parent element
		    for( int i=0; i < count; i++ )
		    {
			   partNumber = -1;
			   eTag       = null;
			   parent     = nodeSet.item(i);
				 
			   if (null != (children = parent.getChildNodes()))
			   {
				  int numChildren = children.getLength();
				  for( int j=0; j < numChildren; j++ )
				  {
					  contents = children.item( j );
					  element  = contents.getNodeName().trim();
					  if ( element.endsWith( "PartNumber" ))
					  {
						   temp = contents.getFirstChild().getNodeValue();
						   if (null != temp) partNumber = Integer.parseInt( temp );
						   //System.out.println( "part: " + partNumber );
					  }
					  else if (element.endsWith( "ETag" ))
					  {
						   eTag = contents.getFirstChild().getNodeValue();
						   //System.out.println( "etag: " + eTag );
					  }
				  }
			   }
				 
			   // -> do the parts given in the call XML match what was previously uploaded?
			   if (lastNumber >= partNumber) {
		           return new Tuple<Integer, String>(400, "InvalidPartOrder"); 
			   }
			   if (partNumber != parts[i].getPartNumber() || eTag == null || !eTag.equalsIgnoreCase( parts[i].getETag())) {
		           return new Tuple<Integer, String>(400, "InvalidPart");
		       }
				 
			   lastNumber = partNumber;
		    }
		    return new Tuple<Integer, String>(200, "Success");
    	}
    	catch( Exception e ) {
    		return new Tuple<Integer, String>(500, e.toString());
    	}
    }
}
