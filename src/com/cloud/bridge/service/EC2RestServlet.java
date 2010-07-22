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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.log4j.Logger;

import com.amazon.ec2.DescribeAvailabilityZonesResponse;
import com.amazon.ec2.DescribeImageAttributeResponse;
import com.amazon.ec2.DescribeInstanceAttributeResponse;
import com.amazon.ec2.DescribeInstancesResponse;
import com.amazon.ec2.DescribeImagesResponse;
import com.amazon.ec2.DescribeSnapshotsResponse;
import com.amazon.ec2.DescribeVolumesResponse;
import com.amazon.ec2.AttachVolumeResponse;
import com.amazon.ec2.DetachVolumeResponse;
import com.amazon.ec2.CreateVolumeResponse;
import com.amazon.ec2.DeleteVolumeResponse;
import com.amazon.ec2.RunInstancesResponse;
import com.amazon.ec2.RebootInstancesResponse;
import com.amazon.ec2.StopInstancesResponse;
import com.amazon.ec2.StartInstancesResponse;
import com.amazon.ec2.TerminateInstancesResponse;
import com.amazon.ec2.ModifyImageAttributeResponse;
import com.amazon.ec2.ResetImageAttributeResponse;
import com.amazon.ec2.CreateImageResponse;
import com.amazon.ec2.RegisterImageResponse;
import com.amazon.ec2.DeregisterImageResponse;
import com.amazon.ec2.CreateSnapshotResponse;
import com.amazon.ec2.DeleteSnapshotResponse;

import com.cloud.bridge.model.UserCredentials;
import com.cloud.bridge.persist.PersistContext;
import com.cloud.bridge.persist.dao.UserCredentialsDao;
import com.cloud.bridge.service.UserContext;
import com.cloud.bridge.service.core.ec2.EC2CreateImage;
import com.cloud.bridge.service.core.ec2.EC2CreateVolume;
import com.cloud.bridge.service.core.ec2.EC2DescribeAvailabilityZones;
import com.cloud.bridge.service.core.ec2.EC2DescribeImages;
import com.cloud.bridge.service.core.ec2.EC2DescribeInstances;
import com.cloud.bridge.service.core.ec2.EC2DescribeSnapshots;
import com.cloud.bridge.service.core.ec2.EC2DescribeVolumes;
import com.cloud.bridge.service.core.ec2.EC2Engine;
import com.cloud.bridge.service.core.ec2.EC2Image;
import com.cloud.bridge.service.core.ec2.EC2Instance;
import com.cloud.bridge.service.core.ec2.EC2RebootInstances;
import com.cloud.bridge.service.core.ec2.EC2RegisterImage;
import com.cloud.bridge.service.core.ec2.EC2RunInstances;
import com.cloud.bridge.service.core.ec2.EC2StartInstances;
import com.cloud.bridge.service.core.ec2.EC2StopInstances;
import com.cloud.bridge.service.core.ec2.EC2Volume;
import com.cloud.bridge.service.exception.EC2ServiceException;
import com.cloud.bridge.service.exception.NoSuchObjectException;
import com.cloud.bridge.service.exception.PermissionDeniedException;
import com.cloud.bridge.util.AuthenticationUtils;
import com.cloud.bridge.util.ConfigurationHelper;
import com.cloud.bridge.util.EC2RestAuth;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMFactory;
import org.apache.axis2.databinding.ADBException;
import org.apache.axis2.databinding.utils.writer.MTOMAwareXMLSerializer;


public class EC2RestServlet extends HttpServlet {

	private static final long serialVersionUID = -6168996266762804888L;
	
	public static final Logger logger = Logger.getLogger(EC2RestServlet.class);
	
	private OMFactory factory = OMAbstractFactory.getOMFactory();
	private XMLOutputFactory xmlOutFactory = XMLOutputFactory.newInstance();
	
	private String pathToKeystore   = null;
	private String keystorePassword = null;
	private String wsdlVersion      = null;

    
	/**
	 * We build the path to where the keystore holding the WS-Security X509 certificates
	 * are stored.
	 */
	public void init( ServletConfig config ) throws ServletException {
       File propertiesFile = ConfigurationHelper.findConfigurationFile("ec2-service.properties");
       Properties EC2Prop = null;
       
       if (null != propertiesFile) {
   		   logger.info("Use EC2 properties file: " + propertiesFile.getAbsolutePath());
   	       EC2Prop = new Properties();
    	   try {
			   EC2Prop.load( new FileInputStream( propertiesFile ));
		   } catch (FileNotFoundException e) {
			   logger.warn("Unable to open properties file: " + propertiesFile.getAbsolutePath(), e);
		   } catch (IOException e) {
			   logger.warn("Unable to read properties file: " + propertiesFile.getAbsolutePath(), e);
		   }
	       String keystore  = EC2Prop.getProperty( "keystore" );
	       keystorePassword = EC2Prop.getProperty( "keystorePass" );
	   	   wsdlVersion      = EC2Prop.getProperty( "WSDLVersion", "2009-11-30" );
	       
	       String installedPath = System.getenv("CATALINA_HOME");
	       if (installedPath == null) installedPath = System.getenv("CATALINA_BASE");
	       if (installedPath == null) installedPath = System.getProperty("catalina.home");
	       pathToKeystore = new String( installedPath + File.separator + "webapps" + File.separator + "gate" + File.separator + "WEB-INF" + File.separator + "classes" + File.separator + keystore );
       }
    }
	
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
	    doGetOrPost(req, resp);
    }
	
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
	    doGetOrPost(req, resp);
    }

    protected void doGetOrPost(HttpServletRequest request, HttpServletResponse response) {
    	String action = request.getParameter( "Action" );
    	logRequest(request);
    	
    	// -> unauthenticated calls, should still be done over HTTPS
	    if (action.equalsIgnoreCase( "SetUserKeys" )) {
	        setUserKeys(request, response);
	        return;
	    }
	    if (action.equalsIgnoreCase( "CloudEC2Version" )) {
	        cloudEC2Version(request, response);
	        return;
	    }

	    // -> authenticated calls
        try {
    	    if (!authenticateRequest( request, response )) return;

    	         if (action.equalsIgnoreCase( "AllocateAddress"           )) /* not yet implemented */ ;   		
    	    else if (action.equalsIgnoreCase( "AssociateAddress"          )) /* not yet implemented */ ;  
    	    else if (action.equalsIgnoreCase( "AttachVolume"              )) attachVolume(request, response );
    	    else if (action.equalsIgnoreCase( "AuthorizeSecurityGroupIngress" )) /* not yet implemented */ ;  
    	    else if (action.equalsIgnoreCase( "CreateImage"               )) createImage(request, response);
    	    else if (action.equalsIgnoreCase( "CreateSecurityGroup"       )) /* not yet implemented */ ;  
    	    else if (action.equalsIgnoreCase( "CreateSnapshot"            )) createSnapshot(request, response); 
    	    else if (action.equalsIgnoreCase( "CreateVolume"              )) createVolume(request, response);  
    	    else if (action.equalsIgnoreCase( "DeleteSecurityGroup"       )) /* not yet implemented */ ;  
    	    else if (action.equalsIgnoreCase( "DeleteSnapshot"            )) deleteSnapshot(request, response); 
    	    else if (action.equalsIgnoreCase( "DeleteVolume"              )) deleteVolume(request, response);   
    	    else if (action.equalsIgnoreCase( "DeregisterImage"           )) deregisterImage(request, response);    
    	    else if (action.equalsIgnoreCase( "DescribeAddresses"         )) /* not yet implemented */ ;  
    	    else if (action.equalsIgnoreCase( "DescribeAvailabilityZones" )) describeAvailabilityZones(request, response); 
    	    else if (action.equalsIgnoreCase( "DescribeImageAttribute"    )) describeImageAttribute(request, response);  
    	    else if (action.equalsIgnoreCase( "DescribeImages"            )) describeImages(request, response);  
    	    else if (action.equalsIgnoreCase( "DescribeInstanceAttribute" )) describeInstanceAttribute(request, response);  
    	    else if (action.equalsIgnoreCase( "DescribeInstances"         )) describeInstances(request, response);  
    	    else if (action.equalsIgnoreCase( "DescribeSecurityGroups"    )) /* not yet implemented */  ;  
    	    else if (action.equalsIgnoreCase( "DescribeSnapshots"         )) describeSnapshots(request, response);  
    	    else if (action.equalsIgnoreCase( "DescribeVolumes"           )) describeVolumes(request, response); 
    	    else if (action.equalsIgnoreCase( "DetachVolume"              )) detachVolume(request, response);  
    	    else if (action.equalsIgnoreCase( "DisassociateAddress"       )) /* not yet implemented */ ;  
    	    else if (action.equalsIgnoreCase( "ModifyImageAttribute"      )) modifyImageAttribute(request, response);  
    	    else if (action.equalsIgnoreCase( "RebootInstances"           )) rebootInstances(request, response);  
    	    else if (action.equalsIgnoreCase( "RegisterImage"             )) registerImage(request, response);  
    	    else if (action.equalsIgnoreCase( "ReleaseAddress"            )) /* not yet implemented */ ;  
    	    else if (action.equalsIgnoreCase( "ResetImageAttribute"       )) resetImageAttribute(request, response);  
    	    else if (action.equalsIgnoreCase( "RevokeSecurityGroupIngress")) /* not yet implemented */ ;  
    	    else if (action.equalsIgnoreCase( "RunInstances"              )) runInstances(request, response);   
    	    else if (action.equalsIgnoreCase( "StartInstances"            )) startInstances(request, response);  
    	    else if (action.equalsIgnoreCase( "StopInstances"             )) stopInstances(request, response); 
    	    else if (action.equalsIgnoreCase( "TerminateInstances"        )) terminateInstances(request, response); 
    	    else if (action.equalsIgnoreCase( "SetCertificate"            )) setCertificate(request, response);
       	    else if (action.equalsIgnoreCase( "DeleteCertificate"         )) deleteCertificate(request, response);
    	    else {
        		logger.error("Unsupported action " + action);
        		response.setStatus(501);
            	endResponse(response, "Unsupported - " + action );
    	    }
    	         
        } catch( EC2ServiceException e ) {
    		logger.error("EC2ServiceException: " + e.getMessage(), e);
    		response.setStatus( e.getErrorCode());
        	endResponse(response, e.toString());
        	
        } catch( PermissionDeniedException e ) {
    		logger.error("Unexpected exception: " + e.getMessage(), e);
    		response.setStatus(403);
        	endResponse(response, "Access denied");
        	
        } catch( Exception e ) {
    		logger.error("Unexpected exception: " + e.getMessage(), e);
    		response.setStatus(500);
        	endResponse(response, e.toString());
        	
        } finally {
        	try {
				response.flushBuffer();
			} catch (IOException e) {
	    		logger.error("Unexpected exception " + e.getMessage(), e);
			}
        }       
    }
   
    /**
     * Provide an easy way to determine the version of the implementation running.
     * 
     * This is an unauthenticated REST call.
     */
    private void cloudEC2Version( HttpServletRequest request, HttpServletResponse response ) {
        String version = new String( "<?xml version=\"1.0\" encoding=\"utf-8\"?><CloudEC2Version>1.03</CloudEC2Version>" );       		
		response.setStatus(200);
        endResponse(response, version);
    }
    
    /**
     * This request registers the Cloud.com account holder to the EC2 service.   The Cloud.com
     * account holder saves his API access and secret keys with the EC2 service so that 
     * the EC2 service can make Cloud.com API calls on his behalf.   The given API access
     * and secret key are saved into the "usercredentials" database table.   
     * 
     * This is an unauthenticated REST call.   The only required parameters are 'accesskey' and
     * 'secretkey'. 
     * 
     * To verify that the given keys represent an existing account they are used to execute the
     * Cloud.com's listAccounts API function.   If the keys do not represent a valid account the
     * listAccounts function will fail.
     * 
     * A user can call this REST function any number of times, on each call the Cloud.com secret
     * key is simply over writes any previously stored value.
     * 
     * As with all REST calls HTTPS should be used to ensure their security.
     */
    private void setUserKeys( HttpServletRequest request, HttpServletResponse response ) {
    	String[] accessKey = null;
    	String[] secretKey = null;
    	
    	try {
		    // -> all these parameters are required
            accessKey = request.getParameterValues( "accesskey" );
		    if ( null == accessKey || 0 == accessKey.length ) { 
		         response.sendError(530, "Missing accesskey parameter" ); 
		         return; 
		    }

            secretKey = request.getParameterValues( "secretkey" );
            if ( null == secretKey || 0 == secretKey.length ) {
                 response.sendError(530, "Missing secretkey parameter" ); 
                 return; 
            }
        } catch( Exception e ) {
		    logger.error("SetUserKeys exception " + e.getMessage(), e);
    		response.setStatus(500);
        	endResponse(response, "SetUserKeys exception " + e.getMessage());
		    return;
        }

        try {
            // -> use the keys to see if the account actually exists
    	    ServiceProvider.getInstance().getEC2Engine().validateAccount( accessKey[0], secretKey[0] );
    	    UserCredentialsDao credentialDao = new UserCredentialsDao();
    	    credentialDao.setUserKeys( accessKey[0], secretKey[0] ); 
    	    
        } catch( Exception e ) {
   		    logger.error("SetUserKeys " + e.getMessage(), e);
    		response.setStatus(401);
        	endResponse(response, e.toString());
        	return;
        }
    	response.setStatus(200);	
    }
    
    /**
     * The SOAP API for EC2 uses WS-Security to sign all client requests.  This requires that 
     * the client have a public/private key pair and the public key defined by a X509 certificate.
     * Thus in order for a Cloud.com account holder to use the EC2's SOAP API he must register
     * his X509 certificate with the EC2 service.   This function allows the Cloud.com account
     * holder to "load" his X509 certificate into the service.   Note, that the SetUserKeys REST
     * function must be called before this call.
     * 
     * This is an authenticated REST call and as such must contain all the required REST parameters
     * including: Signature, Timestamp, Expires, etc.   The signature is calculated using the
     * Cloud.com account holder's API access and secret keys and the Amazon defined EC2 signature
     * algorithm.
     * 
     * A user can call this REST function any number of times, on each call the X509 certificate
     * simply over writes any previously stored value.
     */
    private void setCertificate( HttpServletRequest request, HttpServletResponse response ) 
        throws Exception { 
    	try {
    	    // [A] Pull the cert and cloud AccessKey from the request
            String[] certificate = request.getParameterValues( "cert" );
    	    if (null == certificate || 0 == certificate.length) {
	    		response.sendError(530, "Missing cert parameter" );
    		    return;
    	    }
    	    logger.debug( "SetCertificate cert: [" + certificate[0] + "]" );
    	    
            String [] accessKey = request.getParameterValues( "AWSAccessKeyId" );
		    if ( null == accessKey || 0 == accessKey.length ) { 
		         response.sendError(530, "Missing AWSAccessKeyId parameter" ); 
		         return; 
		    }

    	   	// [B] Open our keystore
    	    FileInputStream fsIn = new FileInputStream( pathToKeystore );
    	    KeyStore certStore = KeyStore.getInstance( "JKS" );
    	    certStore.load( fsIn, keystorePassword.toCharArray());
    	    
    	    // -> use the Cloud API key to save the cert in the keystore
    	    // -> write the cert into the keystore on disk
    	    Certificate userCert = null;
    	    CertificateFactory cf = CertificateFactory.getInstance( "X.509" );

    	    ByteArrayInputStream bs = new ByteArrayInputStream( certificate[0].getBytes());
    	    while (bs.available() > 0) userCert = cf.generateCertificate(bs);
      	    certStore.setCertificateEntry( accessKey[0], userCert );

    	    FileOutputStream fsOut = new FileOutputStream( pathToKeystore );
    	    certStore.store( fsOut, keystorePassword.toCharArray());
    	    
    	    // [C] Associate the cert's uniqueId with the Cloud API keys
            String uniqueId = AuthenticationUtils.X509CertUniqueId( userCert );
            logger.debug( "SetCertificate, uniqueId: " + uniqueId );
    	    UserCredentialsDao credentialDao = new UserCredentialsDao();
    	    credentialDao.setCertificateId( accessKey[0], uniqueId ); 
    		response.setStatus(200);	
    	    
    	} catch( NoSuchObjectException e ) {
    		logger.error("SetCertificate exception " + e.getMessage(), e);
    		response.sendError(404, "SetCertificate exception " + e.getMessage());
		
        } catch( Exception e ) {
    		logger.error("SetCertificate exception " + e.getMessage(), e);
    		response.sendError(500, "SetCertificate exception " + e.getMessage());
        }
    }
 
    /**
     * The SOAP API for EC2 uses WS-Security to sign all client requests.  This requires that 
     * the client have a public/private key pair and the public key defined by a X509 certificate.
     * This REST call allows a Cloud.com account holder to remove a previouly "loaded" X509
     * certificate out of the EC2 service.
     * 
     * This is an unauthenticated REST call and as such must contain all the required REST parameters
     * including: Signature, Timestamp, Expires, etc.   The signature is calculated using the
     * Cloud.com account holder's API access and secret keys and the Amazon defined EC2 signature
     * algorithm.
     */
    private void deleteCertificate( HttpServletRequest request, HttpServletResponse response ) 
        throws Exception { 
	    try {
            String [] accessKey = request.getParameterValues( "AWSAccessKeyId" );
		    if ( null == accessKey || 0 == accessKey.length ) { 
		         response.sendError(530, "Missing AWSAccessKeyId parameter" ); 
		         return; 
		    }

	        // -> delete the specified entry and save back to disk
	        FileInputStream fsIn = new FileInputStream( pathToKeystore );
	        KeyStore certStore = KeyStore.getInstance( "JKS" );
	        certStore.load( fsIn, keystorePassword.toCharArray());

	        if ( certStore.containsAlias( accessKey[0] )) {
 	             certStore.deleteEntry( accessKey[0] );
 	             FileOutputStream fsOut = new FileOutputStream( pathToKeystore );
	             certStore.store( fsOut, keystorePassword.toCharArray());
	             
	     	     // -> dis-associate the cert's uniqueId with the Cloud API keys
	     	     UserCredentialsDao credentialDao = new UserCredentialsDao();
	     	     credentialDao.setCertificateId( accessKey[0], null ); 
		         response.setStatus(200);	
	        }
	        else response.setStatus(404);
	        
    	} catch( NoSuchObjectException e ) {
    		logger.error("SetCertificate exception " + e.getMessage(), e);
    		response.sendError(404, "SetCertificate exception " + e.getMessage());

        } catch( Exception e ) {
		    logger.error("DeleteCertificate exception " + e.getMessage(), e);
		    response.sendError(500, "DeleteCertificate exception " + e.getMessage());
        }
    }

    /**
     * The approach taken here is to map these REST calls into the same objects used 
     * to implement the matching SOAP requests (e.g., AttachVolume).   This is done by parsing
     * out the URL parameters and loading them into the relevant EC2XXX object(s).   Once
     * the parameters are loaded the appropriate EC2Engine function is called to perform
     * the requested action.   The result of the EC2Engine function is a standard 
     * Amazon WSDL defined object (e.g., AttachVolumeResponse Java object).   Finally the
     * serialize method is called on the returned response object to obtain the extected
     * response XML.
     */
    private void attachVolume( HttpServletRequest request, HttpServletResponse response ) 
        throws ADBException, XMLStreamException, IOException {
		EC2Volume EC2request = new EC2Volume();
		
		// -> all these parameters are required
        String[] volumeId = request.getParameterValues( "VolumeId" );
		if ( null != volumeId && 0 < volumeId.length ) 
			 EC2request.setId( volumeId[0] );
		else { response.sendError(530, "Missing VolumeId parameter" ); return; }

        String[] instanceId = request.getParameterValues( "InstanceId" );
        if ( null != instanceId && 0 < instanceId.length ) 
        	 EC2request.setInstanceId( instanceId[0] );
		else { response.sendError(530, "Missing InstanceId parameter" ); return; }

        String[] device = request.getParameterValues( "Device" );
        if ( null != device && 0 < device.length ) 
        	 EC2request.setDevice( device[0] );
		else { response.sendError(530, "Missing Device parameter" ); return; }
		
		// -> execute the request
		AttachVolumeResponse EC2response = EC2SoapServiceImpl.toAttachVolumeResponse( ServiceProvider.getInstance().getEC2Engine().attachVolume( EC2request ));

		// -> serialize using the apache's Axiom classes
		OutputStream os = response.getOutputStream();
		response.setStatus(200);	
	    response.setContentType("text/xml; charset=UTF-8");
		XMLStreamWriter xmlWriter = xmlOutFactory.createXMLStreamWriter( os );
		MTOMAwareXMLSerializer MTOMWriter = new MTOMAwareXMLSerializer( xmlWriter );
        EC2response.serialize( null, factory, MTOMWriter );
        xmlWriter.flush();
        xmlWriter.close();
        os.close();
    }
        
    private void detachVolume( HttpServletRequest request, HttpServletResponse response ) 
        throws ADBException, XMLStreamException, IOException {
		EC2Volume EC2request = new EC2Volume();
		
        String[] volumeId = request.getParameterValues( "VolumeId" );
		if ( null != volumeId && 0 < volumeId.length ) 
			 EC2request.setId( volumeId[0] );
		else { response.sendError(530, "Missing VolumeId parameter" ); return; }

        String[] instanceId = request.getParameterValues( "InstanceId" );
        if ( null != instanceId && 0 < instanceId.length ) 
        	 EC2request.setInstanceId( instanceId[0] );

        String[] device = request.getParameterValues( "Device" );
        if ( null != device && 0 < device.length ) 
        	 EC2request.setDevice( device[0] );
		
		// -> execute the request
		DetachVolumeResponse EC2response = EC2SoapServiceImpl.toDetachVolumeResponse( ServiceProvider.getInstance().getEC2Engine().detachVolume( EC2request ));

		// -> serialize using the apache's Axiom classes
		OutputStream os = response.getOutputStream();
		response.setStatus(200);	
	    response.setContentType("text/xml; charset=UTF-8");
		XMLStreamWriter xmlWriter = xmlOutFactory.createXMLStreamWriter( os );
		MTOMAwareXMLSerializer MTOMWriter = new MTOMAwareXMLSerializer( xmlWriter );
        EC2response.serialize( null, factory, MTOMWriter );
        xmlWriter.flush();
        xmlWriter.close();
        os.close();
    }

    private void deleteVolume( HttpServletRequest request, HttpServletResponse response ) 
        throws ADBException, XMLStreamException, IOException {
		EC2Volume EC2request = new EC2Volume();
		
        String[] volumeId = request.getParameterValues( "VolumeId" );
		if ( null != volumeId && 0 < volumeId.length ) 
			 EC2request.setId( volumeId[0] );
		else { response.sendError(530, "Missing VolumeId parameter" ); return; }

		// -> execute the request
		DeleteVolumeResponse EC2response = EC2SoapServiceImpl.toDeleteVolumeResponse( ServiceProvider.getInstance().getEC2Engine().deleteVolume( EC2request ));

		// -> serialize using the apache's Axiom classes
		OutputStream os = response.getOutputStream();
		response.setStatus(200);	
	    response.setContentType("text/xml; charset=UTF-8");
		XMLStreamWriter xmlWriter = xmlOutFactory.createXMLStreamWriter( os );
		MTOMAwareXMLSerializer MTOMWriter = new MTOMAwareXMLSerializer( xmlWriter );
        EC2response.serialize( null, factory, MTOMWriter );
        xmlWriter.flush();
        xmlWriter.close();
        os.close();
    }

    private void createVolume( HttpServletRequest request, HttpServletResponse response ) 
        throws ADBException, XMLStreamException, IOException {
    	EC2CreateVolume EC2request = new EC2CreateVolume();
		
        String[] size = request.getParameterValues( "Size" );
		if ( null != size && 0 < size.length ) 
			 EC2request.setSize( size[0] );
		else { response.sendError(530, "Missing Size parameter" ); return; }
		
        String[] zoneName = request.getParameterValues( "AvailabilityZone" );
        if ( null != zoneName && 0 < zoneName.length ) 
        	 EC2request.setZoneName( zoneName[0] );
		else { response.sendError(530, "Missing AvailabilityZone parameter" ); return; }

		// -> execute the request
		CreateVolumeResponse EC2response = EC2SoapServiceImpl.toCreateVolumeResponse( ServiceProvider.getInstance().getEC2Engine().handleRequest( EC2request ));

		// -> serialize using the apache's Axiom classes
		OutputStream os = response.getOutputStream();
		response.setStatus(200);	
	    response.setContentType("text/xml; charset=UTF-8");
		XMLStreamWriter xmlWriter = xmlOutFactory.createXMLStreamWriter( os );
		MTOMAwareXMLSerializer MTOMWriter = new MTOMAwareXMLSerializer( xmlWriter );
        EC2response.serialize( null, factory, MTOMWriter );
        xmlWriter.flush();
        xmlWriter.close();
        os.close();
    }

    private void deleteSnapshot( HttpServletRequest request, HttpServletResponse response ) 
        throws ADBException, XMLStreamException, IOException {
		String snapshotId = null;
		
        String[] snapSet = request.getParameterValues( "SnapshotId" );
		if ( null != snapSet && 0 < snapSet.length ) 
			 snapshotId = snapSet[0];
		else { response.sendError(530, "Missing SnapshotId parameter" ); return; }
		
		// -> execute the request
		DeleteSnapshotResponse EC2response = EC2SoapServiceImpl.toDeleteSnapshotResponse( ServiceProvider.getInstance().getEC2Engine().deleteSnapshot( snapshotId ));

		// -> serialize using the apache's Axiom classes
		OutputStream os = response.getOutputStream();
		response.setStatus(200);	
	    response.setContentType("text/xml; charset=UTF-8");
		XMLStreamWriter xmlWriter = xmlOutFactory.createXMLStreamWriter( os );
		MTOMAwareXMLSerializer MTOMWriter = new MTOMAwareXMLSerializer( xmlWriter );
        EC2response.serialize( null, factory, MTOMWriter );
        xmlWriter.flush();
        xmlWriter.close();
        os.close();
    }

    private void createSnapshot( HttpServletRequest request, HttpServletResponse response ) 
        throws ADBException, XMLStreamException, IOException {
		String volumeId = null;
		
        String[] volSet = request.getParameterValues( "VolumeId" );
		if ( null != volSet && 0 < volSet.length ) 
			 volumeId = volSet[0];
		else { response.sendError(530, "Missing VolumeId parameter" ); return; }
		
		// -> execute the request
		EC2Engine engine = ServiceProvider.getInstance().getEC2Engine();
        CreateSnapshotResponse EC2response = EC2SoapServiceImpl.toCreateSnapshotResponse( engine.createSnapshot( volumeId ), engine.getAccountName());

		// -> serialize using the apache's Axiom classes
		OutputStream os = response.getOutputStream();
		response.setStatus(200);	
	    response.setContentType("text/xml; charset=UTF-8");
		XMLStreamWriter xmlWriter = xmlOutFactory.createXMLStreamWriter( os );
		MTOMAwareXMLSerializer MTOMWriter = new MTOMAwareXMLSerializer( xmlWriter );
        EC2response.serialize( null, factory, MTOMWriter );
        xmlWriter.flush();
        xmlWriter.close();
        os.close();
    }
    
    private void deregisterImage( HttpServletRequest request, HttpServletResponse response ) 
        throws ADBException, XMLStreamException, IOException {
		EC2Image image = new EC2Image();
		
        String[] imageId = request.getParameterValues( "ImageId" );
		if ( null != imageId && 0 < imageId.length ) 
			 image.setId( imageId[0] );
		else { response.sendError(530, "Missing ImageId parameter" ); return; }
		
		// -> execute the request
		DeregisterImageResponse EC2response = EC2SoapServiceImpl.toDeregisterImageResponse( ServiceProvider.getInstance().getEC2Engine().deregisterImage( image ));

		// -> serialize using the apache's Axiom classes
		OutputStream os = response.getOutputStream();
		response.setStatus(200);	
	    response.setContentType("text/xml; charset=UTF-8");
		XMLStreamWriter xmlWriter = xmlOutFactory.createXMLStreamWriter( os );
		MTOMAwareXMLSerializer MTOMWriter = new MTOMAwareXMLSerializer( xmlWriter );
        EC2response.serialize( null, factory, MTOMWriter );
        xmlWriter.flush();
        xmlWriter.close();
        os.close();
    }
 
    private void createImage( HttpServletRequest request, HttpServletResponse response ) 
        throws ADBException, XMLStreamException, IOException {
    	EC2CreateImage EC2request = new EC2CreateImage();
		
        String[] instanceId = request.getParameterValues( "InstanceId" );
		if ( null != instanceId && 0 < instanceId.length ) 
			 EC2request.setInstanceId( instanceId[0] );
		else { response.sendError(530, "Missing InstanceId parameter" ); return; }
		
        String[] name = request.getParameterValues( "Name" );
        if ( null != name && 0 < name.length ) 
        	 EC2request.setName( name[0] );
		else { response.sendError(530, "Missing Name parameter" ); return; }

        String[] description = request.getParameterValues( "Description" );
        if ( null != description && 0 < description.length ) 
        	 EC2request.setDescription( description[0] );

		// -> execute the request
        CreateImageResponse EC2response = EC2SoapServiceImpl.toCreateImageResponse( ServiceProvider.getInstance().getEC2Engine().handleRequest( EC2request ));

		// -> serialize using the apache's Axiom classes
		OutputStream os = response.getOutputStream();
		response.setStatus(200);	
	    response.setContentType("text/xml; charset=UTF-8");
		XMLStreamWriter xmlWriter = xmlOutFactory.createXMLStreamWriter( os );
		MTOMAwareXMLSerializer MTOMWriter = new MTOMAwareXMLSerializer( xmlWriter );
        EC2response.serialize( null, factory, MTOMWriter );
        xmlWriter.flush();
        xmlWriter.close();
        os.close();
    }

    private void registerImage( HttpServletRequest request, HttpServletResponse response ) 
        throws ADBException, XMLStreamException, IOException {
    	EC2RegisterImage EC2request = new EC2RegisterImage();
		
        String[] location = request.getParameterValues( "ImageLocation" );
		if ( null != location && 0 < location.length ) 
			 EC2request.setLocation( location[0] );
		else { response.sendError(530, "Missing ImageLocation parameter" ); return; }

        String[] cloudRedfined = request.getParameterValues( "Architecture" );
		if ( null != cloudRedfined && 0 < cloudRedfined.length ) 
			 EC2request.setArchitecture( cloudRedfined[0] );
		else { response.sendError(530, "Missing Architecture parameter" ); return; }

        String[] name = request.getParameterValues( "Name" );
        if ( null != name && 0 < name.length ) 
        	 EC2request.setName( name[0] );

        String[] description = request.getParameterValues( "Description" );
        if ( null != description && 0 < description.length ) 
        	 EC2request.setDescription( description[0] );

		// -> execute the request
        RegisterImageResponse EC2response = EC2SoapServiceImpl.toRegisterImageResponse( ServiceProvider.getInstance().getEC2Engine().handleRequest( EC2request ));

		// -> serialize using the apache's Axiom classes
		OutputStream os = response.getOutputStream();
		response.setStatus(200);	
	    response.setContentType("text/xml; charset=UTF-8");
		XMLStreamWriter xmlWriter = xmlOutFactory.createXMLStreamWriter( os );
		MTOMAwareXMLSerializer MTOMWriter = new MTOMAwareXMLSerializer( xmlWriter );
        EC2response.serialize( null, factory, MTOMWriter );
        xmlWriter.flush();
        xmlWriter.close();
        os.close();
    }

    private void modifyImageAttribute( HttpServletRequest request, HttpServletResponse response ) 
        throws ADBException, XMLStreamException, IOException {
		EC2Image image = new EC2Image();
		
		// -> its interesting to note that the SOAP API docs has description but the REST API docs do not
        String[] imageId = request.getParameterValues( "ImageId" );
		if ( null != imageId && 0 < imageId.length ) 
			 image.setId( imageId[0] );
		else { response.sendError(530, "Missing ImageId parameter" ); return; }

        String[] description = request.getParameterValues( "Description" );
		if ( null != description && 0 < description.length ) 
			 image.setDescription( description[0] );
		else { response.sendError(530, "Missing Description parameter" ); return; }

		// -> execute the request
		ModifyImageAttributeResponse EC2response = EC2SoapServiceImpl.toModifyImageAttributeResponse( ServiceProvider.getInstance().getEC2Engine().modifyImageAttribute( image ));

		// -> serialize using the apache's Axiom classes
		OutputStream os = response.getOutputStream();
		response.setStatus(200);	
	    response.setContentType("text/xml; charset=UTF-8");
		XMLStreamWriter xmlWriter = xmlOutFactory.createXMLStreamWriter( os );
		MTOMAwareXMLSerializer MTOMWriter = new MTOMAwareXMLSerializer( xmlWriter );
        EC2response.serialize( null, factory, MTOMWriter );
        xmlWriter.flush();
        xmlWriter.close();
        os.close();
    }

    private void resetImageAttribute( HttpServletRequest request, HttpServletResponse response ) 
        throws ADBException, XMLStreamException, IOException {
		EC2Image image = new EC2Image();
		
        String[] imageId = request.getParameterValues( "ImageId" );
		if ( null != imageId && 0 < imageId.length ) 
			 image.setId( imageId[0] );
		else { response.sendError(530, "Missing ImageId parameter" ); return; }
		
		// -> execute the request
		image.setDescription( "" );
		ResetImageAttributeResponse EC2response = EC2SoapServiceImpl.toResetImageAttributeResponse( ServiceProvider.getInstance().getEC2Engine().modifyImageAttribute( image ));

		// -> serialize using the apache's Axiom classes
		OutputStream os = response.getOutputStream();
		response.setStatus(200);	
	    response.setContentType("text/xml; charset=UTF-8");
		XMLStreamWriter xmlWriter = xmlOutFactory.createXMLStreamWriter( os );
		MTOMAwareXMLSerializer MTOMWriter = new MTOMAwareXMLSerializer( xmlWriter );
        EC2response.serialize( null, factory, MTOMWriter );
        xmlWriter.flush();
        xmlWriter.close();
        os.close();
    }

    private void runInstances( HttpServletRequest request, HttpServletResponse response ) 
        throws ADBException, XMLStreamException, IOException {
		EC2RunInstances EC2request = new EC2RunInstances();
		
		// -> so in the Amazon docs for this REST call there is no userData even though there is in the SOAP docs
        String[] imageId = request.getParameterValues( "ImageId" );
		if ( null != imageId && 0 < imageId.length ) 
			 EC2request.setTemplateId( imageId[0] );
		else { response.sendError(530, "Missing ImageId parameter" ); return; }

        String[] minCount = request.getParameterValues( "MinCount" );
		if ( null != minCount && 0 < minCount.length ) 
			 EC2request.setMinCount( Integer.parseInt( minCount[0] ));
		else { response.sendError(530, "Missing MinCount parameter" ); return; }

        String[] maxCount = request.getParameterValues( "MaxCount" );
		if ( null != maxCount && 0 < maxCount.length ) 
			 EC2request.setMaxCount( Integer.parseInt( maxCount[0] ));
		else { response.sendError(530, "Missing MaxCount parameter" ); return; }

        String[] instanceType = request.getParameterValues( "InstanceType" );
		if ( null != instanceType && 0 < instanceType.length ) 
			 EC2request.setInstanceType( instanceType[0] );

        String[] zoneName = request.getParameterValues( "Placement.AvailabilityZone" );
		if ( null != zoneName && 0 < zoneName.length ) 
			 EC2request.setZoneName( zoneName[0] );

		// -> execute the request
		EC2Engine engine = ServiceProvider.getInstance().getEC2Engine();
		RunInstancesResponse EC2response = EC2SoapServiceImpl.toRunInstancesResponse( engine.handleRequest( EC2request ), engine.getAccountName());

		// -> serialize using the apache's Axiom classes
		OutputStream os = response.getOutputStream();
		response.setStatus(200);	
	    response.setContentType("text/xml; charset=UTF-8");
		XMLStreamWriter xmlWriter = xmlOutFactory.createXMLStreamWriter( os );
		MTOMAwareXMLSerializer MTOMWriter = new MTOMAwareXMLSerializer( xmlWriter );
        EC2response.serialize( null, factory, MTOMWriter );
        xmlWriter.flush();
        xmlWriter.close();
        os.close();
    }

    private void rebootInstances( HttpServletRequest request, HttpServletResponse response ) 
        throws ADBException, XMLStreamException, IOException {
        EC2RebootInstances EC2request = new EC2RebootInstances();
        int count = 0;

        // -> load in all the "InstanceId.n" parameters if any
        Enumeration names = request.getParameterNames();
        while( names.hasMoreElements()) {
            String key = (String)names.nextElement();
            if (key.startsWith("InstanceId")) {
                String[] value = request.getParameterValues( key );
                if (null != value && 0 < value.length) {
                	EC2request.addInstanceId( value[0] );
                	count++;
                }
            }
        }	
        if (0 == count) { response.sendError(530, "Missing InstanceId parameter" ); return; }
    
        // -> execute the request
        RebootInstancesResponse EC2response = EC2SoapServiceImpl.toRebootInstancesResponse( ServiceProvider.getInstance().getEC2Engine().handleRequest( EC2request ));

        // -> serialize using the apache's Axiom classes
        OutputStream os = response.getOutputStream();
        response.setStatus(200);	
        response.setContentType("text/xml; charset=UTF-8");
        XMLStreamWriter xmlWriter = xmlOutFactory.createXMLStreamWriter( os );
        MTOMAwareXMLSerializer MTOMWriter = new MTOMAwareXMLSerializer( xmlWriter );
        EC2response.serialize( null, factory, MTOMWriter );
        xmlWriter.flush();
        xmlWriter.close();
        os.close();
    }

    private void startInstances( HttpServletRequest request, HttpServletResponse response ) 
        throws ADBException, XMLStreamException, IOException {
        EC2StartInstances EC2request = new EC2StartInstances();
        int count = 0;

        // -> load in all the "InstanceId.n" parameters if any
        Enumeration names = request.getParameterNames();
        while( names.hasMoreElements()) {
	        String key = (String)names.nextElement();
	        if (key.startsWith("InstanceId")) {
	            String[] value = request.getParameterValues( key );
	            if (null != value && 0 < value.length) {
	            	EC2request.addInstanceId( value[0] );
	            	count++;
	            }
	        }
        }	
        if (0 == count) { response.sendError(530, "Missing InstanceId parameter" ); return; }

        // -> execute the request
        StartInstancesResponse EC2response = EC2SoapServiceImpl.toStartInstancesResponse( ServiceProvider.getInstance().getEC2Engine().handleRequest( EC2request ));

        // -> serialize using the apache's Axiom classes
        OutputStream os = response.getOutputStream();
        response.setStatus(200);	
        response.setContentType("text/xml; charset=UTF-8");
        XMLStreamWriter xmlWriter = xmlOutFactory.createXMLStreamWriter( os );
        MTOMAwareXMLSerializer MTOMWriter = new MTOMAwareXMLSerializer( xmlWriter );
        EC2response.serialize( null, factory, MTOMWriter );
        xmlWriter.flush();
        xmlWriter.close();
        os.close();
    }

    private void stopInstances( HttpServletRequest request, HttpServletResponse response ) 
        throws ADBException, XMLStreamException, IOException {
	    EC2StopInstances EC2request = new EC2StopInstances();
	    int count = 0;
	
	    // -> load in all the "InstanceId.n" parameters if any
	    Enumeration names = request.getParameterNames();
	    while( names.hasMoreElements()) {
		    String key = (String)names.nextElement();
		    if (key.startsWith("InstanceId")) {
		        String[] value = request.getParameterValues( key );
		        if (null != value && 0 < value.length) {
		        	EC2request.addInstanceId( value[0] );
		        	count++;
		        }
		    }
	    }	
        if (0 == count) { response.sendError(530, "Missing InstanceId parameter" ); return; }

	    // -> execute the request
	    StopInstancesResponse EC2response = EC2SoapServiceImpl.toStopInstancesResponse( ServiceProvider.getInstance().getEC2Engine().handleRequest( EC2request ));

	    // -> serialize using the apache's Axiom classes
	    OutputStream os = response.getOutputStream();
	    response.setStatus(200);	
        response.setContentType("text/xml; charset=UTF-8");
	    XMLStreamWriter xmlWriter = xmlOutFactory.createXMLStreamWriter( os );
	    MTOMAwareXMLSerializer MTOMWriter = new MTOMAwareXMLSerializer( xmlWriter );
        EC2response.serialize( null, factory, MTOMWriter );
        xmlWriter.flush();
        xmlWriter.close();
        os.close();
    }

    private void terminateInstances( HttpServletRequest request, HttpServletResponse response ) 
        throws ADBException, XMLStreamException, IOException {
        EC2StopInstances EC2request = new EC2StopInstances();
        int count = 0;

        // -> load in all the "InstanceId.n" parameters if any
        Enumeration names = request.getParameterNames();
        while( names.hasMoreElements()) {
	        String key = (String)names.nextElement();
	        if (key.startsWith("InstanceId")) {
	            String[] value = request.getParameterValues( key );
	            if (null != value && 0 < value.length) {
	            	EC2request.addInstanceId( value[0] );
	            	count++;
	            }
	        }
        }		
        if (0 == count) { response.sendError(530, "Missing InstanceId parameter" ); return; }

        // -> execute the request
		EC2request.setDestroyInstances( true );
        TerminateInstancesResponse EC2response = EC2SoapServiceImpl.toTermInstancesResponse( ServiceProvider.getInstance().getEC2Engine().handleRequest( EC2request ));

        // -> serialize using the apache's Axiom classes
        OutputStream os = response.getOutputStream();
        response.setStatus(200);	
        response.setContentType("text/xml; charset=UTF-8");
        XMLStreamWriter xmlWriter = xmlOutFactory.createXMLStreamWriter( os );
        MTOMAwareXMLSerializer MTOMWriter = new MTOMAwareXMLSerializer( xmlWriter );
        EC2response.serialize( null, factory, MTOMWriter );
        xmlWriter.flush();
        xmlWriter.close();
        os.close();
    }

    /**
     * We are reusing the SOAP code to process this request.   We then use Axiom to serialize the
     * resulting EC2 Amazon object into XML to return to the client.
     */
    private void describeAvailabilityZones( HttpServletRequest request, HttpServletResponse response ) 
        throws ADBException, XMLStreamException, IOException {
		EC2DescribeAvailabilityZones EC2request = new EC2DescribeAvailabilityZones();
		
		// -> load in all the "ZoneName.n" parameters if any
		Enumeration names = request.getParameterNames();
		while( names.hasMoreElements()) {
			String key = (String)names.nextElement();
			if (key.startsWith("ZoneName")) {
			    String[] value = request.getParameterValues( key );
			    if (null != value && 0 < value.length) EC2request.addZone( value[0] );
			}
		}		
		// -> execute the request
		DescribeAvailabilityZonesResponse EC2response = EC2SoapServiceImpl.toDescribeAvailabilityZonesResponse( ServiceProvider.getInstance().getEC2Engine().handleRequest( EC2request ));

		// -> serialize using the apache's Axiom classes
		OutputStream os = response.getOutputStream();
		response.setStatus(200);	
	    response.setContentType("text/xml; charset=UTF-8");
		XMLStreamWriter xmlWriter = xmlOutFactory.createXMLStreamWriter( os );
		MTOMAwareXMLSerializer MTOMWriter = new MTOMAwareXMLSerializer( xmlWriter );
        EC2response.serialize( null, factory, MTOMWriter );
        xmlWriter.flush();
        xmlWriter.close();
        os.close();
    }

    private void describeImages( HttpServletRequest request, HttpServletResponse response ) 
        throws ADBException, XMLStreamException, IOException {
		EC2DescribeImages EC2request = new EC2DescribeImages();
		
		// -> load in all the "ImageId.n" parameters if any, and ignore all other parameters
		Enumeration names = request.getParameterNames();
		while( names.hasMoreElements()) {
			String key = (String)names.nextElement();
			if (key.startsWith("ImageId")) {
			    String[] value = request.getParameterValues( key );
			    if (null != value && 0 < value.length) EC2request.addImageSet( value[0] );
			}
		}		
		// -> execute the request
		EC2Engine engine = ServiceProvider.getInstance().getEC2Engine();
		DescribeImagesResponse EC2response = EC2SoapServiceImpl.toDescribeImagesResponse( engine.handleRequest( EC2request ), engine.getAccountName());

		// -> serialize using the apache's Axiom classes
		OutputStream os = response.getOutputStream();
		response.setStatus(200);	
	    response.setContentType("text/xml; charset=UTF-8");
		XMLStreamWriter xmlWriter = xmlOutFactory.createXMLStreamWriter( os );
		MTOMAwareXMLSerializer MTOMWriter = new MTOMAwareXMLSerializer( xmlWriter );
        EC2response.serialize( null, factory, MTOMWriter );
        xmlWriter.flush();
        xmlWriter.close();
        os.close();
    }
    
    private void describeImageAttribute( HttpServletRequest request, HttpServletResponse response ) 
        throws ADBException, XMLStreamException, IOException {
		EC2DescribeImages EC2request = new EC2DescribeImages();
		
		// -> only works for queries about descriptions
        String[] descriptions = request.getParameterValues( "Description" );
	    if ( null != descriptions && 0 < descriptions.length ) {
	         String[] value = request.getParameterValues( "ImageId" );
	    	 EC2request.addImageSet( value[0] );
		}	
		else {
			 response.sendError(501, "Unsupported - only description supported" ); 
			 return;
		}

		// -> execute the request
		DescribeImageAttributeResponse EC2response = EC2SoapServiceImpl.toDescribeImageAttributeResponse( ServiceProvider.getInstance().getEC2Engine().handleRequest( EC2request ));

		// -> serialize using the apache's Axiom classes
		OutputStream os = response.getOutputStream();
		response.setStatus(200);	
	    response.setContentType("text/xml; charset=UTF-8");
		XMLStreamWriter xmlWriter = xmlOutFactory.createXMLStreamWriter( os );
		MTOMAwareXMLSerializer MTOMWriter = new MTOMAwareXMLSerializer( xmlWriter );
        EC2response.serialize( null, factory, MTOMWriter );
        xmlWriter.flush();
        xmlWriter.close();
        os.close();
    }

    private void describeInstances( HttpServletRequest request, HttpServletResponse response ) 
        throws ADBException, XMLStreamException, IOException {
		EC2DescribeInstances EC2request = new EC2DescribeInstances();
		
		// -> load in all the "InstanceId.n" parameters if any
		Enumeration names = request.getParameterNames();
		while( names.hasMoreElements()) {
			String key = (String)names.nextElement();
			if (key.startsWith("InstanceId")) {
			    String[] value = request.getParameterValues( key );
			    if (null != value && 0 < value.length) EC2request.addInstanceId( value[0] );
			}
		}		
		// -> execute the request
		EC2Engine engine = ServiceProvider.getInstance().getEC2Engine();
		DescribeInstancesResponse EC2response = EC2SoapServiceImpl.toDescribeInstancesResponse( engine.handleRequest( EC2request ), engine.getAccountName());

		// -> serialize using the apache's Axiom classes
		OutputStream os = response.getOutputStream();
		response.setStatus(200);	
	    response.setContentType("text/xml; charset=UTF-8");
		XMLStreamWriter xmlWriter = xmlOutFactory.createXMLStreamWriter( os );
		MTOMAwareXMLSerializer MTOMWriter = new MTOMAwareXMLSerializer( xmlWriter );
        EC2response.serialize( null, factory, MTOMWriter );
        xmlWriter.flush();
        xmlWriter.close();
        os.close();
    }
    
    private void describeInstanceAttribute( HttpServletRequest request, HttpServletResponse response ) 
        throws ADBException, XMLStreamException, IOException {
    	EC2DescribeInstances EC2request = new EC2DescribeInstances();
    	String instanceType = null;
	
    	// -> we are only handling queries about the "Attribute=instanceType"
		Enumeration names = request.getParameterNames();
		while( names.hasMoreElements()) {
			String key = (String)names.nextElement();
			if (key.startsWith("Attribute")) {
			    String[] value = request.getParameterValues( key );
			    if (null != value && 0 < value.length && value[0].equalsIgnoreCase( "instanceType" )) { 
			    	instanceType = value[0];
			    	break;
			    }
			}
		}		
		if ( null != instanceType ) {
	         String[] value = request.getParameterValues( "InstanceId" );
	    	 EC2request.addInstanceId( value[0] );
		}
		else {
			 response.sendError(501, "Unsupported - only instanceType supported" ); 
			 return;
		}
     
	    // -> execute the request
	    DescribeInstanceAttributeResponse EC2response = EC2SoapServiceImpl.toDescribeInstanceAttributeResponse( ServiceProvider.getInstance().getEC2Engine().handleRequest( EC2request ));

	    // -> serialize using the apache's Axiom classes
	    OutputStream os = response.getOutputStream();
	    response.setStatus(200);	
        response.setContentType("text/xml; charset=UTF-8");
	    XMLStreamWriter xmlWriter = xmlOutFactory.createXMLStreamWriter( os );
	    MTOMAwareXMLSerializer MTOMWriter = new MTOMAwareXMLSerializer( xmlWriter );
        EC2response.serialize( null, factory, MTOMWriter );
        xmlWriter.flush();
        xmlWriter.close();
        os.close();
    }

    private void describeSnapshots( HttpServletRequest request, HttpServletResponse response ) 
        throws ADBException, XMLStreamException, IOException {
	    EC2DescribeSnapshots EC2request = new EC2DescribeSnapshots();
	
	    // -> load in all the "SnapshotId.n" parameters if any, and ignore any other parameters
	    Enumeration names = request.getParameterNames();
	    while( names.hasMoreElements()) {
		    String key = (String)names.nextElement();
		    if (key.startsWith("SnapshotId")) {
		        String[] value = request.getParameterValues( key );
		        if (null != value && 0 < value.length) EC2request.addSnapshotId( value[0] );
		    }
	    }		
	    // -> execute the request
		EC2Engine engine = ServiceProvider.getInstance().getEC2Engine();
	    DescribeSnapshotsResponse EC2response = EC2SoapServiceImpl.toDescribeSnapshotsResponse( engine.handleRequest( EC2request ), engine.getAccountName());

	    // -> serialize using the apache's Axiom classes
	    OutputStream os = response.getOutputStream();
	    response.setStatus(200);	
        response.setContentType("text/xml; charset=UTF-8");
	    XMLStreamWriter xmlWriter = xmlOutFactory.createXMLStreamWriter( os );
	    MTOMAwareXMLSerializer MTOMWriter = new MTOMAwareXMLSerializer( xmlWriter );
        EC2response.serialize( null, factory, MTOMWriter );
        xmlWriter.flush();
        xmlWriter.close();
        os.close();
    }

    private void describeVolumes( HttpServletRequest request, HttpServletResponse response ) 
        throws ADBException, XMLStreamException, IOException {
        EC2DescribeVolumes EC2request = new EC2DescribeVolumes();

        // -> load in all the "VolumeId.n" parameters if any
        Enumeration names = request.getParameterNames();
        while( names.hasMoreElements()) {
	        String key = (String)names.nextElement();
	        if (key.startsWith("VolumeId")) {
	            String[] value = request.getParameterValues( key );
	            if (null != value && 0 < value.length) EC2request.addVolumeId( value[0] );
	        }
        }		
        // -> execute the request
        DescribeVolumesResponse EC2response = EC2SoapServiceImpl.toDescribeVolumesResponse( ServiceProvider.getInstance().getEC2Engine().handleRequest( EC2request ));

        // -> serialize using the apache's Axiom classes
        OutputStream os = response.getOutputStream();
        response.setStatus(200);	
        response.setContentType("text/xml; charset=UTF-8");
        XMLStreamWriter xmlWriter = xmlOutFactory.createXMLStreamWriter( os );
        MTOMAwareXMLSerializer MTOMWriter = new MTOMAwareXMLSerializer( xmlWriter );
        EC2response.serialize( null, factory, MTOMWriter );
        xmlWriter.flush();
        xmlWriter.close();
        os.close();
    }

    /**
     * This function implements the EC2 REST authentication algorithm.   It uses the given
     * "AWSAccessKeyId" parameter to look up the Cloud.com account holder's secret key which is
     * used as input to the signature calculation.  In addition, it tests the given "Expires"
     * parameter to see if the signature has expired and if so the request fails.
     */
    private boolean authenticateRequest( HttpServletRequest request, HttpServletResponse response ) 
        throws SignatureException, IOException, InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException, ParseException {
     	String cloudSecretKey = null;    
    	String cloudAccessKey = null;
    	String signature      = null;
    	String sigMethod      = null;           

    	// [A] Basic parameters required for an authenticated rest request
    	//  -> note that the Servlet engine will un-URL encode all parameters we extract via "getParameterValues()" calls
        String[] awsAccess = request.getParameterValues( "AWSAccessKeyId" );
		if ( null != awsAccess && 0 < awsAccess.length ) 
			 cloudAccessKey = awsAccess[0];
		else { response.sendError(530, "Missing AWSAccessKeyId parameter" ); return false; }

        String[] clientSig = request.getParameterValues( "Signature" );
		if ( null != clientSig && 0 < clientSig.length ) 
			 signature = clientSig[0];
		else { response.sendError(530, "Missing Signature parameter" ); return false; }

        String[] method = request.getParameterValues( "SignatureMethod" );
		if ( null != method && 0 < method.length ) {
			 sigMethod = method[0];
			 if (!sigMethod.equals( "HmacSHA256" ) && !sigMethod.equals( "HmacSHA1" )) {
			     response.sendError(531, "Unsupported SignatureMethod value: " + sigMethod + " expecting: HmacSHA256 or HmacSHA1" ); 
			     return false;
			 }
		}
		else { response.sendError(530, "Missing SignatureMethod parameter" ); return false; }

        String[] version = request.getParameterValues( "Version" );
		if ( null != version && 0 < version.length ) {
			 if (!version[0].equals( wsdlVersion )) {
			 	 response.sendError(531, "Unsupported Version value: " + version[0] + " expecting: " + wsdlVersion ); 
			 	 return false;
			 }
		}
		else { response.sendError(530, "Missing Version parameter" ); return false; }

        String[] sigVersion = request.getParameterValues( "SignatureVersion" );
		if ( null != sigVersion && 0 < sigVersion.length ) {
			 if (!sigVersion[0].equals( "2" )) {
				 response.sendError(531, "Unsupported SignatureVersion value: " + sigVersion[0] + " expecting: 2" ); 
				 return false;
			 }
		}
		else { response.sendError(530, "Missing SignatureVersion parameter" ); return false; }

		// -> can have only one but not both { Expires | Timestamp } headers
        String[] expires = request.getParameterValues( "Expires" );
		if ( null != expires && 0 < expires.length ) {
			 // -> contains the date and time at which the signature included in the request EXPIRES
		     if (hasSignatureExpired( expires[0] )) {
				 response.sendError(531, "Expires parameter indicates signature has expired: " + expires[0] ); 
				 return false;
			 }
		}
		else { 
			 // -> contains the date and time at which the request is SIGNED
             String[] time = request.getParameterValues( "Timestamp" );
		     if ( null == time || 0 == time.length ) {
                  response.sendError(530, "Missing Timestamp and Expires parameter, one is required" ); 
                  return false; 
             }
		} 

		
		// [B] Use the cloudAccessKey to get the users secret key in the db
	    UserCredentialsDao credentialDao = new UserCredentialsDao();
	    UserCredentials cloudKeys = credentialDao.getByAccessKey( cloudAccessKey ); 
	    if ( null == cloudKeys ) {
	    	 logger.debug( cloudAccessKey + " is not defined in the EC2 service - call SetUserKeys" );
	         response.sendError(404, cloudAccessKey + " is not defined in the EC2 service - call SetUserKeys" ); 
	         return false; 
	    }
		else cloudSecretKey = cloudKeys.getSecretKey(); 

		
		// [C] Verify the signature
		//  -> getting the query-string in this way maintains its URL encoding
	   	EC2RestAuth restAuth = new EC2RestAuth();
    	restAuth.setHostHeader( request.getHeader( "Host" ));
    	restAuth.setHTTPRequestURI( request.getRequestURI());
    	restAuth.setQueryString( request.getQueryString());
    	
		if ( restAuth.verifySignature( request.getMethod(), cloudSecretKey, signature, sigMethod )) {
		     UserContext.current().initContext( cloudAccessKey, cloudSecretKey, "", "REST request" );
		     return true;
		}
		else throw new PermissionDeniedException("Invalid signature");
    }

    /**
     * We check this to reduce replay attacks.
     * 
     * @param timeStamp
     * @return true - if the request is not longer valid, false otherwise
     * @throws ParseException
     */
    private boolean hasSignatureExpired( String timeStamp ) {
        Calendar cal = EC2RestAuth.parseDateString( timeStamp );
        if (null == cal) return false; 
        
        Date expiredTime = cal.getTime();          
    	Date today       = new Date();   // -> gets set to time of creation
        if ( 0 >= expiredTime.compareTo( today )) { 
        	 logger.debug( "timestamp given: [" + timeStamp + "], now: [" + today.toString() + "]" );
        	 return true;
        }
        else return false;
    }
    
    private static void endResponse(HttpServletResponse response, String content) {
    	try {
            byte[] data = content.getBytes();
            response.setContentLength(data.length);
            OutputStream os = response.getOutputStream();
            os.write(data);
            os.close();
            
    	} catch(Throwable e) {
    		logger.error("Unexpected exception " + e.getMessage(), e);
    	}
    }

    private void logRequest(HttpServletRequest request) {
    	if(logger.isInfoEnabled()) {
    		logger.info("EC2 Request method: " + request.getMethod());
    		logger.info("Request contextPath: " + request.getContextPath());
    		logger.info("Request pathInfo: " + request.getPathInfo());
    		logger.info("Request pathTranslated: " + request.getPathTranslated());
    		logger.info("Request queryString: " + request.getQueryString());
    		logger.info("Request requestURI: " + request.getRequestURI());
    		logger.info("Request requestURL: " + request.getRequestURL());
    		logger.info("Request servletPath: " + request.getServletPath());
    		Enumeration headers = request.getHeaderNames();
    		if(headers != null) {
    			while(headers.hasMoreElements()) {
    				Object headerName = headers.nextElement();
    	    		logger.info("Request header " + headerName + ":" + request.getHeader((String)headerName));
    			}
    		}
    		
    		Enumeration params = request.getParameterNames();
    		if(params != null) {
    			while(params.hasMoreElements()) {
    				Object paramName = params.nextElement();
    	    		logger.info("Request parameter " + paramName + ":" + 
    	    			request.getParameter((String)paramName));
    			}
    		}
    	}
    }
}