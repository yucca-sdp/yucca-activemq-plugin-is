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


import org.apache.activemq.broker.Broker;
import org.apache.activemq.broker.BrokerPlugin;

public class UserAuthenticationPlugin implements BrokerPlugin {

    String proxyHostname= null;
    int proxyPort=0;
	
    int cacheValidationInterval;

// Configuration for basic authentication and rbac authorization (Wso2 Identity Server)
    String serverUrl;
    String username;
    String password;
    String jksFileLocation;
    
 // Configuration for oauth2 authentication and authorization (Wso2 Api Mananger)
    String oauthServerUrl;
    String oauthUsername;
    String oauthPassword;
    
    
    

    public Broker installPlugin(Broker broker) throws Exception {
    	
    	
    	return new UserAuthenticationBroker(broker, 
    			serverUrl, username, password, jksFileLocation,
        		cacheValidationInterval, proxyHostname,proxyPort,
        		oauthServerUrl, oauthUsername, oauthPassword);
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getJksFileLocation() {
        return jksFileLocation;
    }

    public void setJksFileLocation(String jksFileLocation) {
        this.jksFileLocation = jksFileLocation;
    }

//    public String getGlobalPublisherRole() {
//        return globalPublisherRole;
//    }
//
//    public void setGlobalPublisherRole(String globalPublisherRole) {
//        this.globalPublisherRole = globalPublisherRole;
//    }
//
//    public String getGlobalSubscriberRole() {
//        return globalSubscriberRole;
//    }
//
//    public void setGlobalSubscriberRole(String globalSubscriberRole) {
//        this.globalSubscriberRole = globalSubscriberRole;
//    }
//
//    public String getGlobalPublisherSubscriberRole() {
//        return globalPublisherSubscriberRole;
//    }
//
//    public void setGlobalPublisherSubscriberRole(String globalPublisherSubscriberRole) {
//        this.globalPublisherSubscriberRole = globalPublisherSubscriberRole;
//    }

    public int getCacheValidationInterval() {
        return cacheValidationInterval;
    }

    public void setCacheValidationInterval(int cacheValidationInterval) {
        this.cacheValidationInterval = cacheValidationInterval;
    }

	public String getProxyHostname() {
		return proxyHostname;
	}

	public void setProxyHostname(String proxyHostname) {
		this.proxyHostname = proxyHostname;
	}

	public int getProxyPort() {
		return proxyPort;
	}

	public void setProxyPort(int proxyPort) {
		this.proxyPort = proxyPort;
	}

	public String getOauthServerUrl() {
		return oauthServerUrl;
	}

	public void setOauthServerUrl(String oauthServerUrl) {
		this.oauthServerUrl = oauthServerUrl;
	}

	public String getOauthUsername() {
		return oauthUsername;
	}

	public void setOauthUsername(String oauthUsername) {
		this.oauthUsername = oauthUsername;
	}

	public String getOauthPassword() {
		return oauthPassword;
	}

	public void setOauthPassword(String oauthPassword) {
		this.oauthPassword = oauthPassword;
	}

}
