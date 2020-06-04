# Project Title
**Yucca Smart Data Platform** è una piattaforma cloud aperta e precompetitiva della Regione Piemonte, realizzata dal CSI Piemonte con tecnologie open source.
# Getting Started
Il prodotto **activemq-plugin-is** è un plugin per ActiveMQ che consente di gestire l'autenticazione e l'autorizzazione degli utenti sul broker tramite configurazioni presenti su un WSO2 Identity server e WSO2 Api manager.

# Prerequisites
I prerequisiti per l'installazione del prodotto sono i seguenti:
## Software
- [OpenJDK 8](https://openjdk.java.net/install/) o equivalenti
- [Apache Maven 3](https://maven.apache.org/download.cgi)

# Configurations
Il plugin ha due differenti motori di autenticazione e di profilazione:
1. Username e password con RBAC usando un Identitiy Server.
2. Oauth2 con sottoscrizione, usando un API Mananger.
## Username e password con RBAC usando un Identitiy Server
Ruoli di default:
1. mb-topic-all-_
2. mb-queue-all-_
3. mb-topic-read-output._
4. mb-topic-admin-output._

Creare un utente e un ruolo con le stesse credenziali e assegnare:
system -> 1,2
guest -> 3,4
(ActiveMq.Advisor è già gestito dal plugin)
Passaggi da aggiungere alla creazione di un Tenant:
1. Creare un untente nell'Identity Server con username identica al codice tenant, password da generare.
2. Creare un ruolo:
	- mb-topic-all-input.*codice tenant*
3. Assegnare i seguenti ruoli all'utente appena creato:
	- mb-topic-admin-output._
	- mb-topic-read-output._
	- mb-topic-all-input.*codice tenant*
	
## Oauth2 con sottoscrizione, usando un API Mananger
Questo plugin può autorizzare gli accesi in lettura a un topic tramite un endpoint OAuth2.
Nessuna operazione è richiesta durante la creazione di un tenant.
Per inviare le credenziali OAuth impostare:
- username: "bearer: xxxxxxxxxxxxxxxxx"
- password: non richiesta

# Installing
## Istruzioni per la compilazione
1. Da riga di comando eseguire `mvn -Dmaven.test.skip=true clean package`
2. La compilazione genera le seguenti unità di installazione:
    - activemq-plugin-2.2.jar

## Istruzioni per l'installazione
1. Aggiungere nella directory Activemq/lib/extra le librie:
	- axis2-1.6.1.wso2v4
	- axiom-1.2.11.wso2v1.jar
	- org.wso2.carbon.um.ws.api.stub-4.2.1.jar
	- wsdl4j-1.6.2.wso2v2.jar
	- neethi-2.0.4.wso2v3.jar
	- org.wso2.securevault-1.0.0.jar
	- XmlSchema-1.4.7.wso2v1.jar
	- activemq-plugin-2.2.jar (questo è il file risultato della compilazione)
2. Aggiungere wso2carbon.jks dal truststore dell'identity server o usare la ca in /lib/extra
3. Modficare il file file di configurazione activemq.xml file con:
    	<plugins>
    		<bean id="userAuthenticationPlugin" class="UserAuthenticationPlugin" xmlns="http://www.springframework.org/schema/beans">
    			<property name="serverUrl">
    				<value>serverurl</value>
    			</property>
    			<property name="username">
    				<value>admin</value>
    			</property>
    			<property name="password">
    				<value>xxxxx</value>
    			</property>
    			<property name="jksFileLocation">
    				<value>/appserv/amq59/apache-activemq-5.9.0-01/lib/extra</value>
    			</property>
    			<property name="cacheValidationInterval">
    				<value>5</value>
    			</property>
    		</bean>
    		[...]
    	</plugins>
4. Aggiungere utenti e ruoli nell'Identity Server

# Versioning
Per la gestione del codice sorgente viene utilizzata la metodologia [Semantic Versioning](https://semver.org/).
# Authors
Gli autori della piattaforma Yucca sono:
- [Alessandro Franceschetti](mailto:alessandro.franceschetti@csi.it)
- [Claudio Parodi](mailto:claudio.parodi@csi.it)
# Copyrights
(C) Copyright 2019 Regione Piemonte
# License
Questo software è distribuito con licenza [EUPL-1.2-or-later](https://joinup.ec.europa.eu/collection/eupl/eupl-text-11-12)

Consulta il file [LICENSE.txt](LICENSE.txt) per i dettagli sulla licenza.
