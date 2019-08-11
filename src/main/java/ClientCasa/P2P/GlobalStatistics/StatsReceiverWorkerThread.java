package ClientCasa.P2P.GlobalStatistics;

import ClientCasa.CasaApp;
import ServerREST.beans.MeanMeasurement;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.net.Socket;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StatsReceiverWorkerThread extends Thread
{
	private static final Logger LOGGER = Logger.getLogger(StatsReceiverWorkerThread.class.getName());
	private MeanMeasurement message;
	private JAXBContext jaxbContext;
	private Unmarshaller unmarshaller;
	private Socket listenSocket;
	private String casaId;
	private CondominioStats condominioStats;

	public StatsReceiverWorkerThread(Socket listenSocket, String casaId, CondominioStats condominioStats) throws JAXBException
	{
		this.listenSocket = listenSocket;
		this.casaId = casaId;
		this.condominioStats = condominioStats;

		jaxbContext = JAXBContext.newInstance(MeanMeasurement.class);
		unmarshaller = jaxbContext.createUnmarshaller();

		// logger levels
		LOGGER.setLevel(CasaApp.LOGGER_LEVEL);
		ConsoleHandler handler = new ConsoleHandler();
		handler.setLevel(CasaApp.LOGGER_LEVEL);
		LOGGER.addHandler(handler);
	}

	public void run()
	{
		// ricevere le statistiche locali da una casa (usando socket + jaxb) e le salva nell'ggetto condiviso CondominioStats

		try
		{
			message = (MeanMeasurement) unmarshaller.unmarshal(listenSocket.getInputStream());
			listenSocket.close();
			LOGGER.log(Level.FINE, "{ " + casaId + " } Statistic received from " + message.getCasaId());

			// salva la statistica appena ricevuta in un oggetto condiviso, così StatsReceiverThread poi le legge
			condominioStats.addCasaStat(message);
		}
		catch(Exception e)
		{
			LOGGER.log(Level.SEVERE, "{ " + casaId + " } Error while receiving stats");
			e.printStackTrace();
		}
	}
}
