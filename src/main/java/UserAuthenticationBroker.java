/*
* Copyright 2004,2005 The Apache Software Foundation.
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

import java.io.File;
import java.lang.management.ManagementFactory;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.activemq.broker.Broker;
import org.apache.activemq.broker.BrokerFilter;
import org.apache.activemq.broker.ConnectionContext;
import org.apache.activemq.broker.ProducerBrokerExchange;
import org.apache.activemq.broker.region.Destination;
import org.apache.activemq.broker.region.MessageReference;
import org.apache.activemq.broker.region.Subscription;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ConnectionInfo;
import org.apache.activemq.command.ConsumerInfo;
import org.apache.activemq.command.DestinationInfo;
import org.apache.activemq.command.Message;
import org.apache.activemq.command.MessagePull;
import org.apache.activemq.command.ProducerInfo;
import org.apache.activemq.command.Response;
import org.apache.activemq.security.SecurityContext;
import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.ConfigurationContextFactory;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.axis2.transport.http.HttpTransportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.apimgt.api.model.xsd.URITemplate;
import org.wso2.carbon.apimgt.impl.dto.xsd.APIKeyValidationInfoDTO;
import org.wso2.carbon.apimgt.keymgt.stub.validator.APIKeyValidationServiceAPIKeyMgtException;
import org.wso2.carbon.apimgt.keymgt.stub.validator.APIKeyValidationServiceAPIManagementException;
import org.wso2.carbon.apimgt.keymgt.stub.validator.APIKeyValidationServiceStub;
import org.wso2.carbon.identity.oauth2.stub.OAuth2TokenValidationServiceStub;
import org.wso2.carbon.identity.oauth2.stub.dto.OAuth2TokenValidationRequestDTO;
import org.wso2.carbon.identity.oauth2.stub.dto.OAuth2TokenValidationRequestDTO_OAuth2AccessToken;
import org.wso2.carbon.identity.oauth2.stub.dto.OAuth2TokenValidationRequestDTO_TokenValidationContextParam;
import org.wso2.carbon.identity.oauth2.stub.dto.OAuth2TokenValidationResponseDTO;
import org.wso2.carbon.um.ws.api.stub.RemoteUserStoreManagerServiceStub;
import org.wso2.carbon.um.ws.api.stub.RemoteUserStoreManagerServiceUserStoreExceptionException;

public class UserAuthenticationBroker extends BrokerFilter implements UserAuthenticationBrokerMBean {

    private static final String PREFIX_CONTEXT_TOPIC = "/api/topic/";


	private static final Logger LOG = LoggerFactory.getLogger(UserAuthenticationBroker.class);
	private static final Logger LOGACCOUNT = LoggerFactory.getLogger("logaccounting");

	
    private static final String SEPARATOR = "-";
    private static final String PREFIX_MB_ROLES = "mb"+SEPARATOR;
    private static final String WILDCARD_MB_ROLES = "_";
    
	//Necessary default properties required
    final String serverUrl;
    final String username;
    final String password;
    final String jksFileLocation;
    final String proxyHostname;
    final int proxyPort;
    final String oauthServerUrl;
    final String oauthUsername;
    final String oauthPassword;

    final int cacheValidationInterval;

    String remoteUserStoreManagerAuthCookie = "";
    ServiceClient remoteUserStoreManagerServiceClient;
    RemoteUserStoreManagerServiceStub remoteUserStoreManagerServiceStub;

    String oauth2TokenValidationAuthCookie = "";
    ServiceClient oauth2TokenValidationService;
    OAuth2TokenValidationServiceStub oAuth2TokenValidationServiceStub;

    String aPIKeyValidationAuthCookie = "";
    ServiceClient aPIKeyValidationService;
    APIKeyValidationServiceStub aPIKeyValidationServiceStub;
    
    ServiceClient simpleRestClient;
    
    //To handle caching
    Map<String, AuthorizationRole[]> userSpecificRoles = new ConcurrentHashMap<String, AuthorizationRole[]>();


	
    public UserAuthenticationBroker(Broker next, String serverUrl, String username,
                                    String password, String jksFileLocation,
                                    int cacheValidationInterval, String proxyHostname, int proxyPort,
                                    String oauthServerUrl, String oauthUsername, String oauthPassword) {
        super(next);
        this.serverUrl = serverUrl;
        this.username = username;
        this.password = password;
        this.jksFileLocation = jksFileLocation;
        this.cacheValidationInterval = cacheValidationInterval;
        this.proxyHostname = proxyHostname;
        this.proxyPort = proxyPort;
        
        this.oauthServerUrl= oauthServerUrl;
        this.oauthUsername = oauthUsername;
        this.oauthPassword = oauthPassword;
        
        createAdminClients();
        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        Task task = new Task();
        
        registerMBean();
        
    }


    private void registerMBean() {
    	// Get the platform MBeanServer
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
  
       // Unique identification of MBeans
       ObjectName name = null;
  
       try {
          // Uniquely identify the MBeans and register them with the platform MBeanServer 
          name = new ObjectName("IdentityServerAuthPluginr:name=UserAuthMBean");
          mbs.registerMBean((UserAuthenticationBrokerMBean)this, name);
       } catch(Exception e) {
          e.printStackTrace();
       }		
	}

    protected SecurityContext checkSecurityContext(ConnectionContext context) throws SecurityException {
        final SecurityContext securityContext = context.getSecurityContext();
        if (securityContext == null) {
            throw new SecurityException("User is not authenticated.");
        }
        return securityContext;
    }


	public void addConnection(ConnectionContext context, ConnectionInfo info)
            throws Exception {
		long startTime = System.currentTimeMillis();
		String authorizedUser = null;
        SecurityContext s = context.getSecurityContext();
        AccountingLog accountingLog = new AccountingLog(context);
        accountingLog.setOperation("CONNECTION");
        if (s == null) {
    		boolean isValidUser = false;
    		LOG.info("Starting authentication for username: "+info.getUserName());
    		if (isBearer(info.getUserName()))
    		{
    			LOG.info("Starting oauth2 authentication");
    			OAuth2TokenValidationRequestDTO dto = new OAuth2TokenValidationRequestDTO();
    			OAuth2TokenValidationRequestDTO_OAuth2AccessToken tokenDto = new OAuth2TokenValidationRequestDTO_OAuth2AccessToken();
    			tokenDto.setIdentifier(extractToken(info.getUserName()));
    			tokenDto.setTokenType("bearer");
    			dto.setAccessToken(tokenDto);
    			OAuth2TokenValidationRequestDTO_TokenValidationContextParam[] arrayCt = new OAuth2TokenValidationRequestDTO_TokenValidationContextParam[1];
    			arrayCt[0] = new OAuth2TokenValidationRequestDTO_TokenValidationContextParam();
    			dto.setContext(arrayCt);
    			OAuth2TokenValidationResponseDTO response =  oAuth2TokenValidationServiceStub.validate(dto);
    			authorizedUser = response.getAuthorizedUser();
    			isValidUser = response.getValid();
    			if (isValidUser)
    				accountingLog.setUsername(authorizedUser);
    		}
    		else {
    			isValidUser = remoteUserStoreManagerServiceStub.authenticate(info.getUserName(), info.getPassword());
    			accountingLog.setUsername(info.getUserName());
    		}
    		if (!isValidUser) {
                context.setSecurityContext(null);
                accountingLog.setErrore("Not a valid user "+info.getUserName() +" connection");
   			 	accountingLog.setElapsed(System.currentTimeMillis() - startTime);
    			 if (!info.getUserName().equals("system"))
	     		 	LOGACCOUNT.info(accountingLog.toString());
    			throw new SecurityException("Not a valid user "+info.getUserName() +" connection");

    		}
    		else
    		{
    			s = new SecurityContext(info.getUserName()) {
                    @Override
                    public Set<Principal> getPrincipals() {
                    	return Collections.emptySet();
                    }
                };
                context.setSecurityContext(s);

    		}
        }
        	
        
		
        try {
	            super.addConnection(context, info);
	            accountingLog.setElapsed(System.currentTimeMillis() - startTime);
	            if (!info.getUserName().equals("system"))
	    		 	LOGACCOUNT.info(accountingLog.toString());
        } catch (Exception e) {
            context.setSecurityContext(null);
            throw e;
        }
        
    }

    public void removeConnection(ConnectionContext context, ConnectionInfo info, Throwable error) throws Exception {
    	boolean isSystem = false;
    	long startTime = System.currentTimeMillis();
    	AccountingLog accountingLog = new AccountingLog(context);
        accountingLog.setOperation("DISCONNECTION");
        if (info.getUserName().equals("system"))
        	isSystem = true;
        
 	 	if (error != null)
	 		accountingLog.setErrore(""+error.getMessage());
    	
        super.removeConnection(context, info, error);
        context.setSecurityContext(null);

    	accountingLog.setElapsed(System.currentTimeMillis() - startTime);
//    	
    	if (!isSystem)
		 	LOGACCOUNT.info(accountingLog.toString());
    }

	
	
	private static boolean isBearer(String username) {
		return username!=null && username.trim().startsWith("Bearer ");
	}


	private static boolean isGuest(String username) {
		return username!=null && username.equals("guest");
	}

	private static String extractToken(String username) {
		if (isGuest(username))
			return null;
		else
			return username.trim().substring(7).trim();
	}

	private static String extractContext(String physicalName) {
		LOG.debug("Search for auxiliary topic (like aux, errors) (4 element after .)");
		StringBuilder build = new StringBuilder();
		String[] destTokenAuth = physicalName.split("\\.");
		int maxSize = destTokenAuth.length>3?3:destTokenAuth.length;
		for (int i = 0; i < maxSize; i++) {
			build.append(destTokenAuth[i]);
			if (i < maxSize-1)
				build.append(".");
		}

		return PREFIX_CONTEXT_TOPIC+build.toString();
	}


	
    public Subscription addConsumer(ConnectionContext context, ConsumerInfo info) throws Exception {

    	long startTime = System.currentTimeMillis();
    	LOG.debug(">>>>>>AddConsumer:"+info.getDestination());
    	final SecurityContext securityContext = checkSecurityContext(context);
    	AccountingLog accountingLog = new AccountingLog(context);
	 	accountingLog.setOperation("SUBSCRIPTION");
    	accountingLog.setDestination(info.getDestination().getPhysicalName());
    	
        if (isBrokerAccess(context, info.getDestination())) {
            return super.addConsumer(context, info);
        }
    	
		if (isUserAuthorized(context.getUserName(), info.getDestination(),Operation.READ)){
		 	Subscription addC = super.addConsumer(context, info);
        	accountingLog.setElapsed(System.currentTimeMillis() - startTime);
        	if (!context.getUserName().equals("system"))
    		 	LOGACCOUNT.info(accountingLog.toString());
		 	return addC;
		}
	 	accountingLog.setErrore("Not a valid user "+context.getUserName() +" to subscribe : " + info.toString() + ".");
	 	accountingLog.setElapsed(System.currentTimeMillis() - startTime);
	 	if (!context.getUserName().equals("system"))
		 	LOGACCOUNT.info(accountingLog.toString());
        throw new SecurityException("Not a valid user "+context.getUserName() +" to subscribe : " + info.toString() + ".");
        
    }


	


	@Override
	public void addProducer(ConnectionContext context, ProducerInfo info)
			throws Exception {
		long startTime = System.currentTimeMillis();
    	LOG.debug(">>>>>>AddProducer:"+info.getDestination());
    	final SecurityContext securityContext = checkSecurityContext(context);
    	if (isBrokerAccess(context, info.getDestination()))
        {
		 	super.addProducer(context, info);
            return;
        }

		
		AuthorizationRole[] roleNames = getRoleListOfUser(context.getUserName());

        if (roleNames!=null)
        {
        	for (int i = 0; i < roleNames.length; i++) {
				AuthorizationRole authorizationRole = roleNames[i];
				if (isAuthorized(authorizationRole,Operation.WRITE,info.getDestination()))
						{
				 			super.addProducer(context, info);
		                    return;
						}
			}
        }
        throw new SecurityException("Not a valid user "+context.getUserName() +" to produce : " + info.toString() + ".");
	}
	

    public void send(ProducerBrokerExchange producerExchange, Message messageSend)
            throws Exception {
    	
    	long startTime = System.currentTimeMillis();
    	LOG.trace(">>>>>>send:"+messageSend.getDestination());
        final SecurityContext securityContext = checkSecurityContext(producerExchange.getConnectionContext());
    	AccountingLog accountingLog = new AccountingLog(producerExchange.getConnectionContext());
	 	accountingLog.setOperation("SEND");
    	accountingLog.setDestination(messageSend.getDestination().getPhysicalName());
    	accountingLog.setUniqueid(messageSend.getMessageId().toString());

        if (isBrokerAccess(producerExchange.getConnectionContext(), messageSend.getDestination()))
        {
        	super.send(producerExchange,messageSend);
            return;
        }	
    	
    	AuthorizationRole[] roleNames = getRoleListOfUser(producerExchange.getConnectionContext().getUserName());

        if (roleNames!=null)
        {
        	for (int i = 0; i < roleNames.length; i++) {
				AuthorizationRole authorizationRole = roleNames[i];
				if (isAuthorized(authorizationRole,Operation.WRITE,messageSend.getDestination())){
			        super.send(producerExchange,messageSend);
					accountingLog.setElapsed(System.currentTimeMillis() - startTime);
					if (!producerExchange.getConnectionContext().getUserName().equals("system"))
		    		 	LOGACCOUNT.info(accountingLog.toString());
                    return;
				}
			}
        }
	 	accountingLog.setErrore("Not a valid user " + producerExchange.getConnectionContext().getUserName() + " to produce : " + messageSend.getDestination().toString() + ".");
	 	accountingLog.setElapsed(System.currentTimeMillis() - startTime);
	 	if (!producerExchange.getConnectionContext().getUserName().equals("system"))
		 	LOGACCOUNT.info(accountingLog.toString());
        throw new SecurityException("Not a valid user " + producerExchange.getConnectionContext().getUserName() + " to produce : " + messageSend.getDestination().toString() + ".");
    	
    }

    
    @Override
    public Destination addDestination(ConnectionContext context,
    		ActiveMQDestination destination, boolean createIfTemporary)
    		throws Exception {
    	
    	LOG.debug(">>>>>>AddDestination:"+destination);
    	final SecurityContext securityContext = checkSecurityContext(context);
        Destination existing = this.getDestinationMap().get(destination);
        if (existing != null) {
        	return super.addDestination(context, destination, createIfTemporary);
        }
        if (isBrokerAccess(context, destination))
        {
            return super.addDestination(context, destination, createIfTemporary);
        }
        if (isUserAuthorized(context.getUserName(), destination,Operation.ADMIN)){
			return super.addDestination(context, destination, createIfTemporary);
		}
        throw new SecurityException("Not a valid user "+context.getUserName() +" to admin : " + destination.toString() + ".");

    	
    }
    
    @Override
    public void addDestinationInfo(ConnectionContext context,
    		DestinationInfo info) throws Exception {

    	LOG.debug(">>>>>>AddDestinationInfpo:"+info.getDestination());
    	final SecurityContext securityContext = checkSecurityContext(context);
        Destination existing = this.getDestinationMap().get(info.getDestination());
        if (existing != null) {
            super.addDestinationInfo(context, info);
            return;
        }
        if (isBrokerAccess(context, info.getDestination()))
        {
            super.addDestinationInfo(context, info);
            return;
        }
        if (isUserAuthorized(context.getUserName(), info.getDestination(),Operation.ADMIN)){
        	super.addDestinationInfo(context, info);
	    	return;
		}

        throw new SecurityException("Not a valid user "+context.getUserName() +" to admin : " + info.getDestination().toString() + ".");    	
    	
    	
    }
    
    @Override
    public void removeDestination(ConnectionContext context,
    		ActiveMQDestination destination, long timeout) throws Exception {
    	LOG.debug(">>>>>>RemoveDestinatrion:"+destination);
    	final SecurityContext securityContext = checkSecurityContext(context);
    	
        Destination existing = this.getDestinationMap().get(destination);
        if (existing != null) {
            super.removeDestination(context, destination, timeout);
            return;
        }
    	
        if (isBrokerAccess(context, destination))
        {
            super.removeDestination(context, destination, timeout);
            return;
        }

    	
    	AuthorizationRole[] roleNames = getRoleListOfUser(context.getUserName());

        if (roleNames!=null)
        {
        	for (int i = 0; i < roleNames.length; i++) {
				AuthorizationRole authorizationRole = roleNames[i];
				if (isAuthorized(authorizationRole,Operation.ADMIN,destination))
						{
					    	super.removeDestination(context, destination, timeout);
						}
			}
        }
        throw new SecurityException("Not a valid user "+context.getUserName() +" to admin : " + destination.toString() + ".");

    }
    
    @Override
    public void removeDestinationInfo(ConnectionContext context,
    		DestinationInfo info) throws Exception {
    	LOG.debug(">>>>>>RemoveDestinaiotnINfo:"+info.getDestination());
    	final SecurityContext securityContext = checkSecurityContext(context);
        Destination existing = this.getDestinationMap().get(info.getDestination());
        if (existing != null) {
            super.removeDestinationInfo(context, info);
            return;
        }
    	
        if (isBrokerAccess(context, info.getDestination()))
        {
            super.removeDestinationInfo(context, info);
            return;
        }

    	
    	AuthorizationRole[] roleNames = getRoleListOfUser(context.getUserName());

        if (roleNames!=null)
        {
        	for (int i = 0; i < roleNames.length; i++) {
				AuthorizationRole authorizationRole = roleNames[i];
				if (isAuthorized(authorizationRole,Operation.ADMIN,info.getDestination()))
						{
							super.removeDestinationInfo(context, info);
							return;
						}
			}
        }
        throw new SecurityException("Not a valid user "+context.getUserName() +" to admin : " + info.getDestination().toString() + ".");    	
    	
    }
    
    
	private boolean isBrokerAccess(ConnectionContext context, ActiveMQDestination dest) {
		
		if (dest==null || dest.getPhysicalName().startsWith("ActiveMQ.Advisory"))
			return true;
		if (context.getSecurityContext()!=null && context.getSecurityContext().isBrokerContext())
			return true;
		if (context.getUserName()!=null && context.getUserName().equals("system"))
			return true;
		return false;
	}
	
	/***
	 * Logic used:
	 * 1. Guest is authorized only if authenticationType is None.
	 * 2. Bearer token is authorized if authenticationType is None or key is subscribed to api
	 * 3. Username is authorized if authenticationType is None or has role on Identity server 
	 * 
	 * @param username
	 * @param destination
	 * @param operation
	 * @return
	 * @throws RemoteException
	 * @throws APIKeyValidationServiceAPIKeyMgtException
	 * @throws APIKeyValidationServiceAPIManagementException
	 * @throws Exception
	 * @throws RemoteUserStoreManagerServiceUserStoreExceptionException
	 */
	private boolean isUserAuthorized(String username, ActiveMQDestination destination,Operation operation  )
			throws RemoteException, APIKeyValidationServiceAPIKeyMgtException,
			APIKeyValidationServiceAPIManagementException, Exception,
			RemoteUserStoreManagerServiceUserStoreExceptionException {
		
		// always true
		LOG.info("Calling api manager -> username["+username+"],destination["+destination.getPhysicalName()+"],operation["+operation.name+"]");
		URITemplate[] templates = aPIKeyValidationServiceStub.getAllURITemplates(extractContext(destination.getPhysicalName()), "1.0");
		if (templates!=null && templates.length>0)
		{
			if ("None".equals(templates[0].getAuthType()))
				return true;
		}
		
		if (isBearer(username))
		{
			LOG.info("Starting oauth2 Validation for addConsumer");
			APIKeyValidationInfoDTO dto = aPIKeyValidationServiceStub.validateKey(extractContext(destination.getPhysicalName()), "1.0", 
					extractToken(username), "Any", null, null, "GET");
			if (dto!=null && dto.getAuthorized())
			{
				return true;				
			}
		}
		else {
        
	        AuthorizationRole[] roleNames = getRoleListOfUser(username);
	
	        if (roleNames!=null)
	        {
	        	for (int i = 0; i < roleNames.length; i++) {
					AuthorizationRole authorizationRole = roleNames[i];
					if (isAuthorized(authorizationRole,operation,destination))
					{
						return true;					
					}
				}
	        }
		}
		return false;
	}
    
	private boolean isAuthorized(AuthorizationRole authorizationRole,
			Operation operation, ActiveMQDestination destination) {
		if (authorizationRole.getOperation() == operation || authorizationRole.getOperation() == Operation.ALL)
		{
			if (authorizationRole.getType().name().equalsIgnoreCase(destination.getDestinationTypeAsString()))
			{
				String[] destTokenAuth = authorizationRole.getDestinationWildCards().split("\\.");
				String[] destTokenToAuth = destination.getPhysicalName().split("\\.");

				return isEqualWildCards(destTokenAuth, destTokenToAuth);
			}
		}

		return false;
	}


	private static boolean isEqualWildCards(String[] destTokenAuth,
			String[] destTokenToAuth) {
		for (int i = 0; i < destTokenAuth.length; i++) {
			String partAuth = destTokenAuth[i];
			if (partAuth.equals(WILDCARD_MB_ROLES))
			{
				if (destTokenToAuth.length>i)
					return true;
				else
					return false;
			}
			if (!partAuth.equals(destTokenToAuth[i]))
			{
				return false;
			}
		}
		return true;
	}


	
	
	private AuthorizationRole[] getRoleListOfUser(String userName)
			throws RemoteException,
			RemoteUserStoreManagerServiceUserStoreExceptionException {
		
		return getRoleListOfUser(userName, false);
		
	}
	private AuthorizationRole[] getRoleListOfUser(String userName, boolean forceUpdate)
			throws RemoteException,
			RemoteUserStoreManagerServiceUserStoreExceptionException {
		AuthorizationRole[] roleNames;

        if (!forceUpdate && userSpecificRoles.containsKey(userName)) {
            roleNames = userSpecificRoles.get(userName);
        } else {
			LOG.info("Roles not cached or force update, call to wso2 identity server!");

            String[] allRoleNames = remoteUserStoreManagerServiceStub.getRoleListOfUser(userName);
            
            ArrayList<AuthorizationRole> roles = new ArrayList<AuthorizationRole>();
            
            if (allRoleNames!=null)
            {
	            for (String roleName : allRoleNames) {
					if (roleName.startsWith(PREFIX_MB_ROLES))
					{
						String roleNameWithoutPrefix = roleName.substring(PREFIX_MB_ROLES.length());
						AuthorizationRole authRole = new AuthorizationRole(roleNameWithoutPrefix);
						if (authRole.isValid())
							roles.add(authRole);
					}
				}
            }
            roleNames = roles.toArray(new AuthorizationRole[0]);
            userSpecificRoles.put(userName, roleNames);
        }
		return roleNames;
	}

    

    private void createAdminClients() {

        /**
         * trust store path.  this must contains server's  certificate or Server's CA chain
         */
        String trustStore = jksFileLocation + File.separator + "wso2carbon.jks";
        /**
         * Call to https://localhost:9443/services/   uses HTTPS protocol.
         * Therefore we to validate the server certificate or CA chain. The server certificate is looked up in the
         * trust store.
         * Following code sets what trust-store to look for and its JKs password.
         */
        System.setProperty("javax.net.ssl.trustStore", trustStore);
        System.setProperty("javax.net.ssl.trustStorePassword", "wso2carbon");
        /**
         * Axis2 configuration context
         */
        ConfigurationContext configContext;

        try {
            /**
             * Create a configuration context. A configuration context contains information for
             * axis2 environment. This is needed to create an axis2 service client
             */
            configContext = ConfigurationContextFactory.createConfigurationContextFromFileSystem(null, null);
            /**
             * end point url with service name
             */
            // RemoteUserStoreManager
            String remoteUserStoreManagerServiceEndPoint = serverUrl + "/services/" + "RemoteUserStoreManagerService";
            remoteUserStoreManagerServiceStub = new RemoteUserStoreManagerServiceStub(configContext, remoteUserStoreManagerServiceEndPoint);
            remoteUserStoreManagerServiceClient = remoteUserStoreManagerServiceStub._getServiceClient();
            Options optionRemoteUser = remoteUserStoreManagerServiceClient.getOptions();
            setProxyToOptions(optionRemoteUser, username, password);
            remoteUserStoreManagerAuthCookie = (String) remoteUserStoreManagerServiceStub._getServiceClient().getServiceContext()
                    .getProperty(HTTPConstants.COOKIE_STRING);

            
            String oAuth2TokenValidationServiceEndPoint = oauthServerUrl + "/services/" + "OAuth2TokenValidationService";
            oAuth2TokenValidationServiceStub = new OAuth2TokenValidationServiceStub(configContext, oAuth2TokenValidationServiceEndPoint);
            oauth2TokenValidationService = oAuth2TokenValidationServiceStub._getServiceClient();
            Options optionOauth2Validation = oauth2TokenValidationService.getOptions();
            setProxyToOptions(optionOauth2Validation, oauthUsername, oauthPassword);
            oauth2TokenValidationAuthCookie = (String) oAuth2TokenValidationServiceStub._getServiceClient().getServiceContext()
                    .getProperty(HTTPConstants.COOKIE_STRING);

            
            String aPIKeyValidationServiceEndPoint  = oauthServerUrl + "/services/" + "APIKeyValidationService";
            aPIKeyValidationServiceStub = new APIKeyValidationServiceStub(configContext, aPIKeyValidationServiceEndPoint);
            aPIKeyValidationService = aPIKeyValidationServiceStub._getServiceClient();
            Options optionApiKeyValidation = aPIKeyValidationService.getOptions();
            setProxyToOptions(optionApiKeyValidation, oauthUsername, oauthPassword);
            aPIKeyValidationAuthCookie = (String) aPIKeyValidationServiceStub._getServiceClient().getServiceContext()
                    .getProperty(HTTPConstants.COOKIE_STRING);

            
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


	private void setProxyToOptions(Options option,String username, String password) {
		/**
		 * Setting a authenticated cookie that is received from Carbon server.
		 * If you have authenticated with Carbon server earlier, you can use that cookie, if
		 * it has not been expired
		 */
		option.setProperty(HTTPConstants.COOKIE_STRING, null);        
		/**
		 *  Setting proxy property if exists
		 */
		if (proxyHostname!=null && !proxyHostname.trim().isEmpty())
		{
		    HttpTransportProperties.ProxyProperties proxyProperties = new HttpTransportProperties.ProxyProperties();
		    proxyProperties.setProxyName(proxyHostname);
		    proxyProperties.setProxyPort(proxyPort);   
		    option.setProperty(HTTPConstants.PROXY,proxyProperties);
		}
		/**
		 * Setting basic auth headers for authentication for carbon server
		 */
		HttpTransportProperties.Authenticator auth = new HttpTransportProperties.Authenticator();
		auth.setUsername(username);
		auth.setPassword(password);
		auth.setPreemptiveAuthentication(true);
		option.setProperty(HTTPConstants.AUTHENTICATE, auth);
		option.setManageSession(true);
	}



    class Task implements Runnable {

        @Override
        public void run() {

            for (Map.Entry<String, AuthorizationRole[]> entry : userSpecificRoles.entrySet()) {
                String userName = entry.getKey();
                try {
                	getRoleListOfUser(userName, true);
                } catch (RemoteException e) {
                    e.printStackTrace();
                } catch (RemoteUserStoreManagerServiceUserStoreExceptionException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    
    class AuthorizationRole {
    
    	public AuthorizationRole(String roleNameWithoutPrefix) {
    		try {
    		String[] parts = roleNameWithoutPrefix.split("["+SEPARATOR+"]",3);
    		if (parts.length!=3)
    		{
    			LOG.error(">>>>>>No valid role:"+roleNameWithoutPrefix);
    			this.setValid(false);
    			return;
    		}
			this.type = DestinationType.valueOf(parts[0].toUpperCase());
    		this.operation = Operation.valueOf(parts[1].toUpperCase());
    		this.destinationWildCards = parts[2];
    		if (destinationWildCards.length()<0)
        		this.setValid(false);
    			
    		this.setValid(true);
    		
    		} catch (Exception e) {
    			LOG.error(">>>>>>Exception on valid role:"+roleNameWithoutPrefix,e);
    			this.setValid(false);
    		}
		}
		
    	private DestinationType type;
    	private Operation operation;
    	private String destinationWildCards;
    	private boolean valid;
		public DestinationType getType() {
			return type;
		}
		public void setType(DestinationType type) {
			this.type = type;
		}
		public Operation getOperation() {
			return operation;
		}
		public void setOperation(Operation operation) {
			this.operation = operation;
		}
		public String getDestinationWildCards() {
			return destinationWildCards;
		}
		public void setDestinationWildCards(String destinationWildCards) {
			this.destinationWildCards = destinationWildCards;
		}
		public boolean isValid() {
			return valid;
		}
		public void setValid(boolean isValid) {
			this.valid = isValid;
		}
    	
    	@Override
    	public String toString() {
    		return type.name+SEPARATOR+operation.name+SEPARATOR+destinationWildCards+"("+valid+")";
    	}
    	
    }

    private static enum DestinationType {

        QUEUE("queue"),
        TOPIC("topic"),
        TEMPQUEUE("tempqueue"),
        TEMPTOPIC("temptopic");
        
        private String name;
        
        private DestinationType(String name) {
        	this.name = name;
        }
        
        
    }
    
    private static enum Operation {

        READ("read"),
        WRITE("write"),
        ADMIN("admin"),
        ALL("all");
        
        private String name;
        
        private Operation(String name) {
        	this.name = name;
        }
        
        
    }

	@Override
	public void resetCache(String username) {
        try {
        	getRoleListOfUser(username, true);
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (RemoteUserStoreManagerServiceUserStoreExceptionException e) {
            e.printStackTrace();
        }
	}
}
