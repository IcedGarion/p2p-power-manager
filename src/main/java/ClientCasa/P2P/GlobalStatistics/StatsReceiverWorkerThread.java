package ClientCasa.P2P.GlobalStatistics;

import ClientCasa.CasaApp;
import ServerREST.beans.MeanMeasurement;
import Shared.Configuration;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.net.Socket;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
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

	public StatsReceiverWorkerThread(Socket listenSocket, CondominioStats condominioStats) throws JAXBException
	{
		this.listenSocket = listenSocket;
		this.casaId = Configuration.CASA_ID;
		this.condominioStats = condominioStats;

		jaxbContext = JAXBContext.newInstance(MeanMeasurement.class);
		unmarshaller = jaxbContext.createUnmarshaller();

		// logger levels
		LOGGER.setLevel(Configuration.LOGGER_LEVEL);
		for (Handler handler : LOGGER.getHandlers()) { LOGGER.removeHandler(handler);}
		ConsoleHandler handler = new ConsoleHandler();
		handler.setLevel(Configuration.LOGGER_LEVEL);
		LOGGER.addHandler(handler);
		LOGGER.setUseParentHandlers(false);
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
