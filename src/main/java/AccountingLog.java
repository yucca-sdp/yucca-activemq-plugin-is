import org.apache.activemq.broker.ConnectionContext;
import org.apache.activemq.command.ConnectionInfo;
import org.apache.activemq.command.ConsumerInfo;

public class AccountingLog {

	private String protocol="-";
	private String connectionId="-";
	private String uniqueid="-";
	private String tenantcode="-";
	private String destination="-"; // output......
	private String operation="-"; // CONNECT, POST, SEND, ADD CONSUMER...

	private String ipOrigin="-";
	private long elapsed=-1;
	private String errore="-";
	private int numeroEventi=-1; // si recupera da esbin
	private String sensorStream="-" ;// si recupera da esbin
	
	private String username="-";

	
	public AccountingLog(ConnectionContext context) {
		if (!context.getClientId().equals(context.getConnectionId().getValue()))
			this.setConnectionId(context.getConnectionId().getValue()+"|"+context.getClientId());
		else
			this.setConnectionId(context.getConnectionId().getValue());
        this.setProtocol(context.getConnector().toString());
        this.setIpOrigin(context.getConnection().getRemoteAddress());
        this.setUsername(""+context.getUserName());
	}
	
	public AccountingLog()
	{
		
	}

	public String toString() {
		String logAccountingMessage="";

		//id
		logAccountingMessage=logAccountingMessage+"\""+uniqueid.replace("\"", "\"\"")+"\"";
		//forwardedfor
		logAccountingMessage=logAccountingMessage+",\""+connectionId.replace("\"", "\"\"")+"\"";
		//jwt
		logAccountingMessage=logAccountingMessage+",\""+protocol.replace("\"", "\"\"")+"\"";
		
		logAccountingMessage=logAccountingMessage+",\""+username.replace("\"", "\"\"")+"\"";
		
		//path
		logAccountingMessage=logAccountingMessage+",\""+tenantcode.replace("\"", "\"\"")+"\"";
		//apicode
		logAccountingMessage=logAccountingMessage+",\""+destination.replace("\"", "\"\"")+"\"";

		//datasetCode
		logAccountingMessage=logAccountingMessage+",\""+operation.replace("\"", "\"\"")+"\"";

		//tenantCode
		logAccountingMessage=logAccountingMessage+",\""+ipOrigin.replace("\"", "\"\"")+"\"";
		
		
		
		//querString
		logAccountingMessage=logAccountingMessage+",\""+sensorStream.replace("\"", "\"\"")+"\"";
		

		//dataIn
		logAccountingMessage=logAccountingMessage+","+numeroEventi;
		//elapsed
		logAccountingMessage=logAccountingMessage+","+elapsed;
		
		
		
		//error
		logAccountingMessage=logAccountingMessage+",\""+errore+"\"";
		
		return logAccountingMessage;
	}


	public String getProtocol() {
		return protocol;
	}


	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}


	public String getConnectionId() {
		return connectionId;
	}


	public void setConnectionId(String connectionId) {
		this.connectionId = connectionId;
	}


	public String getUniqueid() {
		return uniqueid;
	}


	public void setUniqueid(String uniqueid) {
		this.uniqueid = uniqueid;
	}


	public String getTenantcode() {
		return tenantcode;
	}


	public void setTenantcode(String tenantcode) {
		this.tenantcode = tenantcode;
	}


	public String getDestination() {
		return destination;
	}


	public void setDestination(String destination) {
		this.destination = destination;
	}


	public String getOperation() {
		return operation;
	}


	public void setOperation(String operation) {
		this.operation = operation;
	}


	public String getIpOrigin() {
		return ipOrigin;
	}


	public void setIpOrigin(String ipOrigin) {
		this.ipOrigin = ipOrigin;
	}


	public long getElapsed() {
		return elapsed;
	}


	public void setElapsed(long elapsed) {
		this.elapsed = elapsed;
	}


	public String getErrore() {
		return errore;
	}


	public void setErrore(String errore) {
		this.errore = errore;
	}


	public int getNumeroEventi() {
		return numeroEventi;
	}


	public void setNumeroEventi(int numeroEventi) {
		this.numeroEventi = numeroEventi;
	}


	public String getSensorStream() {
		return sensorStream;
	}


	public void setSensorStream(String sensorStream) {
		this.sensorStream = sensorStream;
	}


	public String getUsername() {
		return username;
	}


	public void setUsername(String username) {
		this.username = username;
	}
	
}
