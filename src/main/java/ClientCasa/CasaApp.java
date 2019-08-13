package ClientCasa;

import ClientCasa.LocalStatistics.MeanThread;
import ClientCasa.LocalStatistics.SimulatorBuffer;
import ClientCasa.LocalStatistics.smartMeter.SmartMeterSimulator;
import ClientCasa.P2P.Boost.PowerBoost;
import ClientCasa.P2P.Boost.PowerBoostThread;
import ClientCasa.P2P.GlobalStatistics.Election.Election;
import ClientCasa.P2P.GlobalStatistics.Election.ElectionThread;
import ClientCasa.P2P.GlobalStatistics.StatsReceiverThread;
import ServerREST.beans.Casa;
import ServerREST.beans.Condominio;
import Shared.Configuration;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CasaApp
{
	private static final Logger LOGGER = Logger.getLogger(CasaApp.class.getName());

	// chiamato quando avvengono altre stampe, per riportare il menu' davanti
	public static void refreshMenu()
	{
		System.out.println("\nMENU'\n======================================\n\n0) POWER BOOST\n\n1) EXIT\n\n======================================\n\n");
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//	PROGRAMMA PRINCIPALE CASE	//
	public static void main(String[] args) throws JAXBException
	{
		//////////////
		/*	SETUP	*/
		JAXBContext jaxbContext = JAXBContext.newInstance(Condominio.class);
		Marshaller marshaller = jaxbContext.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
		Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
		URL url;
		HttpURLConnection conn;
		Level loggerLevel = null;
		String serverURL = "", casaId = "", casaIp = "";
		int casaStatsPort = 0, casaElectionPort = 0, casaBoostPort = 0;

		// CONFIGURATIONS setup
		try
		{
			Configuration.loadProperties();
			loggerLevel = Configuration.LOGGER_LEVEL;
			serverURL = Configuration.SERVER_URL;
			casaId = Configuration.CASA_ID;
			casaIp = Configuration.CASA_IP;
			casaStatsPort = Configuration.CASA_STATS_PORT;
			casaElectionPort = Configuration.CASA_ELECTION_PORT;
			casaBoostPort = Configuration.CASA_BOOST_PORT;
		}
		catch(Exception e)
		{
			LOGGER.log(Level.SEVERE, "Error in reading configuration file: " + Configuration.CONFIGURATION_FILE);
			System.exit(1);
		}

		// LOGGER setup
		LOGGER.setLevel(loggerLevel);
		ConsoleHandler handler = new ConsoleHandler();
		handler.setLevel(loggerLevel);
		LOGGER.addHandler(handler);
		LOGGER.setUseParentHandlers(false);

		LOGGER.log(Level.INFO, "{ " + casaId + " } Started Casa Application with ID " + casaId);

		///////////////////////////////////////////////////////////
		/*	AVVIA THREAD SIMULATORE / SMART METER + MEAN THREAD	*/
		SimulatorBuffer myBuffer = new SimulatorBuffer();
		SmartMeterSimulator simulator = new SmartMeterSimulator(myBuffer);
		simulator.start();
		LOGGER.log(Level.FINE, "{ " + casaId + " } Smart meted launched");

		// avvia thread che invia periodicamente le medie
		MeanThread mean = new MeanThread(myBuffer);
		mean.start();
		LOGGER.log(Level.FINE, "{ " + casaId+ " } Local statistic (MeanThread) thread launched");


		///////////////////////////////////////////////////
		/*	REGISTRA LA CASA AL SERVER AMMINISTRATORE	*/
		Casa myCasa = new Casa(casaId, casaIp, casaStatsPort, casaElectionPort, casaBoostPort);

		// POST /condominio/add: inserisce nuova casa
		try
		{
			url = new URL(serverURL + "/condominio/add");
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("content-type", "application/xml");
			conn.setDoOutput(true);

			// invia casa come xml body
			marshaller.marshal(myCasa, conn.getOutputStream());

			assert conn.getResponseCode() == 201: "CasaApp: Condominio register failed ( " + conn.getResponseCode() + " " + conn.getResponseMessage() + " )";
			LOGGER.log(Level.INFO, "{ " + casaId + " } Casa registered to Admin Server with code: " + conn.getResponseCode() + " " + conn.getResponseMessage());
		}
		catch(Exception e)
		{
			LOGGER.log(Level.SEVERE, "Failed to connect to Admin Server ( POST " + serverURL + "/condominio/add )");
		}

		///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
		/*	PARTE RETE P2P	*/
		// Prepara thread elezione e oggetto condiviso Election da passargli; lo passa anche a statsReceiver perche' deve sapere stato elezione
		// ( per sapere chi e' coord e quindi chi manda le stats)
		Election election = new Election(casaElectionPort);

		// lancia thread "ascoltatore" elezione bully: riceve msg e risponde a dovere secondo alg BULLY
		ElectionThread electionThread = new ElectionThread(casaElectionPort, election);
		electionThread.start();
		LOGGER.log(Level.FINE, "{ " + casaId + " } Election thread launched");

		// lancia thread che riceve le statistiche
		StatsReceiverThread statsReceiver = new StatsReceiverThread(casaStatsPort, election);
		statsReceiver.start();
		LOGGER.log(Level.FINE, "{ " + casaId + " } Global statistics (StatsReceiver) thread launched");

		// lancia thread che riceve richieste di power boost e si coordina
		PowerBoost powerBoostState = new PowerBoost(casaBoostPort, simulator);
		PowerBoostThread powerBoostThread = new PowerBoostThread(casaBoostPort, powerBoostState);
		powerBoostThread.start();
		LOGGER.log(Level.FINE, "{ " + casaId+ " } Power Boost thread launched");

		///////////////////////////////////////////////////////////////////////////////////////////////////////////////
		/*	INTERFACCIA CLI BOOST + EXIT	*/
		BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
		String choice;
		while(true)
		{
			try
			{
				refreshMenu();
				choice = input.readLine();

				// EXIT
				// termina tutti i thread + informa il server che la casa sta per uscire
				if(choice.equals("1"))
				{
					// post per cancellare la casa
					url = new URL(serverURL + "/condominio/delete");
					conn = (HttpURLConnection) url.openConnection();
					conn.setRequestMethod("POST");
					conn.setRequestProperty("content-type", "application/xml");
					conn.setDoOutput(true);
					marshaller.marshal(myCasa, conn.getOutputStream());
					assert conn.getResponseCode() == 204 : "Error in removing casa " + casaId;


					// se sta per uscire ed era il coordinatore delle stat globali, dice a tutti che molla (nuova elezione poi)
					if(election.getState().equals(Election.ElectionOutcome.COORD))
					{
						election.coordLeaving();
					}

					// termina i suoi thread
					// TODO: check se va terminato prima un thread di un altro (election / stat receiver? )
					mean.interrupt();
					simulator.interrupt();
					statsReceiver.interrupt();
					electionThread.interrupt();

					LOGGER.log(Level.INFO, "{ " + casaId + " } Stopping CasaApp...");
					break;
				}
				// POWER BOOST
				else if(choice.equals("0"))
				{
					LOGGER.log(Level.INFO, "{ " + casaId + " } Power boost requested... (Please wait)");
					powerBoostState.requestPowerBoost();
				} else
				{
					System.out.println("Inserire 0/1");
				}
			}
			catch(Exception e)
			{
				LOGGER.log(Level.INFO, "{ " + casaId + " } Error in choice...");
				e.printStackTrace();
			}
		}

		// Terminano anche tutti i thread lanciati: SmartMeterSimulator, MeanThread, e P2pThread
		System.exit(0);
	}
}
