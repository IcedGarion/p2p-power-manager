# TODO now

- power boost semplice con una casa sola (non 2 alla volta)
--- in corso: vedi i TODO in PowerBoostWorkerThread



- Power boost 2 alla volta

- un costruttore overload da qualche parte e' fatto male

(una cosa da sistemare indietro, poi quando avrai finito election):
--------- "una stat non deve contribuire piu di una volta nel calcolo globale -->> vai in CondominioStats e 
	   fai che quando arriva una nuova stat da x ma x esistevia gia (a questo giro di tot) allora non fa niente, invece di aggiornare!

(guarda appunti e sistema caso election non coord: da aggiungere cose)




# DOMANDE FATTE / COSE DA SISTEMARE POI
- pool di thread opzionale, non serve per forza
- bully algo va bene per elezione, tanto uscite sono controllate:
  nel caso di uscita, avvisa il server e tutte le case (che si scaricano di nuovo la lista)
  
  MA POI VA RI-INDETTA ELEZIONE! 
  DEVONO CAPIRE CHE SI E' IN "NEED_ELECTION" QUANDO UNO ESCE.... TIMEOUT SE NON RISPONDE NESSUN COORD? VEDI BULLY  
	DA IMPLEMENTARE IN UN RAMO DI STATSRECEIVERSERVERTHREAD: se non sei coord (ma neanche in need_election, perche' c'e' gia' stata),
	dovresti comunque cercare di pingare il coord per ssapere se indirne una nuova


  ( QUANDO CASA ESCE, CHE SUCCEDE A STATS SENDER/ RECEIVER? CONTINUA A FUNZIONARE OPPURE SI BLOCCA IN ATTESA DELL'ULTIMA CASA
  ( CHE NON MANDERA' MAI LA SUA STATISTICA?? -> ANDREBBE TOLTA LA CASA ANCHE DALL'OGGETTO CONDIVISO (HASHMAP)
  ( ANDREBBE RI-AGGIORNATO IL CONDOMINIO E PASSATO AL STATSRECEIVER (seno' crede che ci sia ancora una casa in piu)
   -> dovrebbe essere ok questa parte, perche' si scarica condominio gia' di suo ogni volta che manda / riceve stats


- invece per power boost va usato algoritmo mutua esclusione distribuita (ricart&agrawala) o ring



# COSE DA TENERE SEMPRE A MENTE
- Quando mandi una richiesta al server, se non leggi la risposta (url-rest)
(conn.getResponseMessage / Code) e' come se non l'avessi inviata... va ignorata boh
- Quando sei in debug, alcune richieste non arrivano a chi ascolta.
- marshalling con socket deve chiudere la socket... altrimenti unmarshaller si blocca senza dire niente
- Comunicazioni in broadcast non devono mai essere sequenziali! lancia thread che invia, ogni volta


# REFACTOR
- si puo' togliere RECEIVER_ID da P2PMessage
- Classe REST per mandare TUTTE le richieste GET/POST al server (capita spesso e usano in tanti)
- StatLocaliService ha un lock obj che puo' diventare sync (si puo togliere e metti sync method)
- AdminApp e' un orrore, serve refactor e metodi comuni

- Aggiungi LOG ovunque (Service e Apps)
- Togli eventuali System.out.println()
- SYNCHRONIZED da aggiungere in posti (es. in services)
  Metodi sync invece che sync statement nei services? anche nelle letture?
- FILE CONFIG (tipo SERVER_URL in giro ovunque)
- TOGLI / CONTROLLA i TODO e FIXME
- suppress log per esame






**CORRENTE EXTRA**
TOKEN RING
con wait e notify va gestita una coda di attesa:
tanti richiedono BOOST ma solo 2 alla volta possono usarlo.
Quando uno finisce, rilascia lock e entra il prossimo
(Syncro per chiamare localmente un metodo)


=================================================================================================

**DOMANDE**


**ESAME**
- schema architettura da spiegare, con anche casi limite
- esecuzione per vedere se va tutto
- guarda codice, parti sync


==================================================================================================
**DOCUMENTAZIONE**

*APPUNTI / SCELTE*
- POST create casa non ritorna niente, anche se su progetto dice
  che dovrebbe tornare l'elenco delle case (come GET), perche'
  il metodo Response.created() accetta solo URI e non un oggetto,
  quindi non si puo' ritornare il condominio.... a meno di cambiare
  response code della POST da created() a ok();

- Codice simulatore e' stato modificato aggiungendo dichiarazione di package


*CLASSI*

**ServerAmministratore**
Fa partire il server REST, e lancia il thread di invio statistiche.


**CondominioService**
Gestisce inserimento / cancellazione / GET di tutte le info sulle case.
Assegnato al PATH /condominio

- GET /condominio: restituisce l'elenco delle case (200 OK)
  getCaseList(): non e' synchronized perche', a un livello piu' sotto, viene chiamato
  Condominio.getInstance() ed e' gia' synchronized.

- POST /condominio/add: permette di aggiungere una nuova casa al condominio
  409 conflict se esiste gia'; 201 created se ok

  addCasa(Casa c): ha un synchronized statement che fa check se esiste gia' la stessa casa
  nel condiminio + aggiunge (se non presente)
  A livelli piu' bassi accede a Condominio.getInstance() e Condominio.getByName() -> Condominio.getCaseList() (synchronized)

- DELETE /condominio/delete: rimuove una casa dal condominio
  404 not found se non esiste; 204 no content se ok
  simile a POST.



**CasaApp**
Fa partire SmartMeterSimulator; si registra al server amministratore (a condominio); chiede elenco case da salvarsi;
gestisce rete p2p; ha interfaccia per power boost e per uscire da Condominio.

- SmartMeterSimulator periodicamente chiama SimulatorBuffer.addMeasurement().
  Questo metodo si salva in un buffer interno le misurazioni; inoltre dialoga col server REST per inviare queste
  statistiche.


**STATISTICHE**
MeanThread lanciato da CasaApp calcola periodicamente la media di 24 misurazioni, (cancella le prime 12)
e invia a StatisticheLocali la media calcolata, con timestamp minore e maggiore fra i 24 considerati.

- MeanThread manda statistica locale a StatisticheService.
  Se e' la prima volta (StatisticheLocali non ha in memoria id casa), crea mapping vuoto fra id casa e lista MeanMeasurement.
  Altrimenti aggiunge in coda alla lista (MAP) MeanMeasurement la nuova Measurement ricevuta.

  Cosi' si ha:
  <idCasa>
    <MeanMeasurement> ... </>
    <MeanMeasurement> ... </>
  </idCasa>
 
  <idCasa>
  ... ecc


**StatisticheLocali**
Come Condominio, e' il contenitore (singleton) per accedere alle statistiche locali delle varie case.
Contiene una lista di CasaMeasurement: ogni elemento di questo oggetto ha un ID casa e poi una lista
di MeanMeasurement, cioe' una lista di medie calcolate. (Calcolo fatto da CasaApp - MeanThread)

(Vedi variante HashMap)


**Scambio Statistiche fra case (Statistiche Globali)
- P2PThread, StatsReceiverThread e MeanThread:
  MeanT manda a tutte le case la sua statistica locale calcolata.
  StatsReceiver ascolta e riceve queste statistiche da tutte le case: aspetta finche' non le riceve da TUTTO il condominio:
    - StatsRceiverWorkerThread riceve e basta; poi salva la statistica in un oggetto condiviso con StatsReceiverServer (CondominioStats)
    - StatsReceiverThread tira le somme: controlla questo oggetto dopo ogni richiesta ricevuta e si assicura che ci siano
         stat da tutte le case... Poi, quando succede, stampa il consumo globale e "azzera" l'oggetto condiviso, per ricominciare.
    - E se non arrivano tutte le case per bene ma, mentre aspetti l'ultima, arriva di nuovo stat di qualcun altra???
      -> la ignora e aspetta il ritardatario (si puo' cambiare in CondominioStats)
    - Una volta che ogni casa possiede le stesse stat globali in ogni "momento", qualcuno deve inviarle al server. Vedi sotto


**Election**
electionThread sempre in ascolto. Scrive stati su un oggetto CONDIVISO fra StatsReceiverThread e ElectionThread (Election)
StatsReceiver legge oggetto condiviso per sapere il suo stato nella elezione:
- ancora nessun coord (prima volta): manda un iniziale msg di "ELECTION" a TUTTE le case tranne se stessa (startElection)
- e' lui il coord: invia lui stat globali al server
- non e' lui il coord: non fa niente. Non serve pingare il coord perche' tanto uscite sono controllate quindi se il coord cade avvisa prima

( se il coord esce dalla rete: 
manda a tutti un msg di "NEED_REELECTION": gli ElectionThread lo ricevono e settano lo stato in Election come NEED_ELECTION (come fosse prima volta)
e quindi, al prossimo giro di StatsReceiver, quando fa il check sullo stato elezione (coord / non coord / serve elezione), si accorge
che serve nuova elezione e ricomincia
)

Poi la elezione la gestisce ElectionThread: riceve "ELECTION" e allora invia a sua volta "ELECTION" a tutti quelli con ID maggiore del suo;
se non ce ne sono, si proclama coord e avvisa tutti gli altri ElectionThread, che settano stato in Election come NOT_COORD;
lui invece si setta COORD.

- Se una casa si unisce dopo: parte da stato NEED_ELECTION, quindi crede che non ci sia coord ancora: fa partire startElection, mandando un msg
  ELECTION a tutte le case tranne se stessa;
Chi riceve, se e' NOT_COORD allora non risponde; se e' lui il COORD gli risponde dicendo che e' lui il COORD.
Non ci sono altri casi perche' quando una casa esce, tutti settano lo stato NEED_ELECTION: questo e' l'unico caso in cui si porta avanti
una nuova elezione vera. Negli altri casi un coord c'e' gia' e quindi risponde al nuovo arrivato, che si mette NOT_COORD.

Elezione parte solo quando tutti sono in NEED_ELECTION, cioe' all'inizio OPPURE quando esce il coord. Quindi elezione non si rifa' ogni volta.
Questo vuol dire che coord non e' per forza sempre quello con id maggiore in ogni momento, ma si tiene quello che esisteva gia (se entra una casa
con id maggiore, accetta il vecchio coord).

======================================================================================================================================
**APPUNTI**


(segue le slide lab5)

PARTE1

- un thread per casa:
	simulare dati che escono

- client amministratore

- server e' un server REST (jersey?):
	bisogna risolvere tutti i problemi di syncro


(c'e' una classe statistiche e una classe condominio lato server)

PARTE2

- Rete p2p fra le case:
	architettura e protocolli
	pensare a tutti i casi limite che possono succedere
	(fault tolerance, es: una casa esce durante elezione)

	CASI LIMITE VANNO PENSATI TUTTI E GESTITI; VENGONO VALUTATi

PASSI
- prova aggiunte / rimozione di case dalla rete
- analisi sensori e comunicazione server
- algoritmo di mutua esclusione


....


GESTIONE CASE

Si vuole aggiungere / togliere case
(casa e' IP + PORT. "esiste gia" = esiste gia un IP+PORT)
Server AMMINISTRATORE gestisce (metodi syncronized non funzionano sempre, usa syncro statement SEMPRE)
Va protetta la sincronizzazione sia in lettura che in scrittura


GESTIONE STATISTICHE
"ha senso bloccare ogni operazione (ad esempio
aggiunta/rimozione di case) mentre vengono calcolate le
statistiche?"
  -> NO. Se usi una unica classe in cui metti strutture dati di case e di statistiche,
  e poi fai tutti i syncronized methods, hai un solo lock e quindi una operazione blocca tutto
  Quindi separa in 2 classi oppure usa i sync statement.

Stessa cosa per:
"Ha senso bloccare l’intera struttura dati con tutte le statistiche
anche se vogliamo analizzare le statistiche di una specifica
casa?" 
  -> NO. Locka solo certe cose, cerca di tenere i lock al minimo.
  Es, se leggi statistiche di una casa non serve bloccarle tutte


JERSEY REMINDER
non mettere come synchronized sui metodi annotati tipo @PATH.
Usa SINGLETON su tutte le risorse che sono condivise. Es, strutture dati,
statistiche globali e locali sono condivise e quindi singleton.
(serve perche' jersey istanzia tante volte cose ???)


