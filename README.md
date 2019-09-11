# Descrizione

Lo scopo del progetto è quello di realizzare un sistema per la gestione intel-
ligente dell’energia elettrica prodotta da un complesso di case. Il consumo
elettrico di ogni casa viene misurato costantemente da uno smart meter
che monitora l’utilizzo degli elettrodomestici.
Il complesso di case costituisce una rete peer-to-peer dinamica volta alla gestione decentralizzata del consumo
elettrico. All’interno di questa rete, ogni Casa deve informare il vicinato
del suo consumo elettrico in tempo reale, in modo tale che ogni casa possa
calcolare il consumo energetico complessivo. Inoltre, quando una casa neces-
sita di consumare una quantità di energia superiore alla norma, deve prima
chiedere il consenso alle altre case in modo tale da non superare la quota
complessiva prevista per il complesso. Le informazioni relative al consumo
di corrente condominiale vengono periodicamente fornite dalle case ad un
server remoto, chiamato Server amministratore, tramite il quale gli ammi-
nistratori possono consultare i consumi energetici per calcolare le spese. Il
Server amministratore permette anche alle case di aggiungersi o rimuoversi
alla rete peer-to-peer del condominio in modo dinamico.


## TODO

------- CASI PARTICOLARI POWER BOOST / TANTO TEST

- boost: gli OK non arrivano MAI: sei REQUESTED ma non arrivano msg...
  -> timeout? chi ha richiesto BOOST dopo un po' lo richiede ancora
 si blocca tutto per semaforo e non puoi neanche richiederne un altro per ora

( allora parte un timer quando richiedi boost: quando finisce timer fai check se sto boost ti e' stato concesso;
  se ancora no, fai ripartire mandando nuovamente messaggio di boost)





- BOOST: caso in cui 3 case: mandi boost, ricevi OK da una delle altre e vai, pero' DOPO ricevi il tuo msg di BOOST
   (in pratica fa prima ad andare fuori BOOST e ricevere un OK, rispetto a ricevere il TUO BOOST).
   allora non devi accodare a tua stessa richiesta, ma ignorare... (basta un IF nel case "BOOST" se sei USING e richiesta ha il tuo ID)

   OPPURE lascia cosi', perche' tanto succede che: quando hai finito boost, ti rimandi da solo OK, ma questo viene ignorato perche' non hai
   richiesto boost (magari aggiungi spiegazione nel LOG)

E' stato lasciato cosi' ma da testare il caso.


------- CASI PARTICOLARI ELECTION

- coord appena caduto (3 case ora): in 2 fanno partire elezione, (0, 1):  2 legge election di 1, diventa coord e avvisa tutti.
  MA c'e' ancora in giro ELECTION di 0: 1 riceve election e eventualmente (se ancora NEED_ELECTION) manda ancora a 2, che gli risponde ELCETED.
  Altrimenti non lo rimanda neanche (se gia' NOT_COORD) e finisce.
  -> tutto ok.

----------------------------------------------------------


## SCELTE / COSE DA CAMBIARE
-> "una stat non deve contribuire piu di una volta nel calcolo globale -->> vai in CondominioStats e 
   fai che quando arriva una nuova stat da x ma x esistevia gia (a questo giro di tot) allora non fa niente, invece di aggiornare!

-> StatLocaliService ha un lock obj che puo' diventare sync (si puo togliere e metti sync method)
   E anche altri service

-> AdminApp e' fatta proprio male, codice ripetuto e non usa metodi comuni (Http)

- TOGLI / CONTROLLA i TODO e FIXME




# COSE DA TENERE SEMPRE A MENTE
- Quando mandi una richiesta al server, se non leggi la risposta (url-rest)
(conn.getResponseMessage / Code) e' come se non l'avessi inviata... va ignorata boh
- Quando sei in debug, alcune richieste non arrivano a chi ascolta.
- marshalling con socket deve chiudere la socket... altrimenti unmarshaller si blocca senza dire niente
- Comunicazioni in broadcast non devono mai essere sequenziali! lancia thread che invia, ogni volta



=================================================================================================

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
      -> aggiorna la stat esistente per avere le misure piu' aggiornate possibile.
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


# POWER BOOST
Simile a election. Oggetto condiviso PowerBoost (stato e altre info) che serve ai vari Thread PowerBoostWorker. Questi sono lanciati
da PowerBoostThread, un server concorrente che gestisce le richieste.
MUTUA ESCLUSIONE 2 ALLA VOLTA: SU N CASE BASTANO (N-2) OK PER ANDARE.

- Parte tutto in PowerBoost.requestPowerBoost: setta stato in PowerBoost (REQUESTED), setta timestamp della richiesta (serve dopo) e conta
    le case attive in quel momento (serve dopo). Poi invia messaggio BOOST a tutte le case attive.

- PowerBoostWorker riceve e gestisce tutti i messaggi di BOOST: a fronte del BOOST appena inviato (e ricevuto) controlla lo stato attuale della
    casa: se NOT_INTERESTED non ha richiesto boost quindi risponde OK; 
          se USING sta usando lui il boost e quindi accoda la richiesta
          se REQUESTED: ci sono in giro 2 richieste nello stesso momento (oppure era la mia appena mandata): check timestamp per chi va prima
	      se timestamp della mia richesta (salvato prima) e' piu' vecchio / uguale a quello del msg, vado io: accodo l'altro
	      se timestamp mio e' piu' recente, faccio andare l'altro (mando OK). Poi lui, che ha ricevuto la mia, la avra' accodata e mi svegliera' con OK

    Risolto chi puo' andare prima di chi, ora resta da attendere gli OK che fanno partire il boost vero e proprio:
    A fronte di un messaggio di OK ricevuto, controlla lo stato attuale della casa:
          se REQUESTED: hai chiesto boost, sei in attesa che ti mandino gli OK e ne hai appena ricevuto uno:
              se numero ok ricevuti (salvato in obj condiviso e appena incrementato qua) e' pari a (numero di case -2) allora "puoi andare"
              altrimenti non fai piu' niente e aspetti degli altri OK
          se USING / NOT_INTERESTED: hai gia' ricevuto abbastanza OK, puoi ignorare
	  (esempio 3 case: 1 OK basta gia', stai gia' facendo boost ma ti arriva anche OK dalla rimanente casa)

   Una volta che "puoi andare": richiami PowerBoost.beginBoost(), il quale fa partire un thread che chiama il metodo boost vero e proprio
   del simulatore. Questo thread attende per tutto il tempo che serve e poi rilascia la risorsa (endBoost()). Serve un thread a parte perche'
   non si puo' tenere impegnato il lock di PowerBoost!

   Finito il boost, viene risettato lo stato NOT_INTERESTED, azzerati i campi tipo timestamp richiesta e numeroOK ricevuti... E infine
   manda messaggio OK a tutti quelli che erano in coda ad aspettare (se ce n'erano)

- WAIT/NOTIFY: per non permettere di mandare continuamente richieste di BOOST inutili (se per esempio hai appena chiesto e stai aspettando
  lo scambio messaggi oppure aspetti che si liberi la risorsa), allora c'e' un lock in PoweBoost: quando invii il primo BOOST (requestPowerBoost),
  appena prima viene controllato lo stato: se sei USING o REQUESTED vai in WAIT e devi prima aspettare che esca (endPowerBoost). 
  Quando finalmente riuscirai ad ottenere il boost (oppure lo avevi gia' ottenuto) e poi uscirai, NOTIFY sveglia se c'era un altra richiesta
  in attesa



# NOTIFICHE PUSH
Entrata / uscita casa sono gestite dal server amministratore: quando riceve la richiesta HTTP da una casa che informa l'entrata / uscita
della stessa, il server contatta ADMIN APP su IP e PORTA noti (file di configurazione), inviando come messaggio jaxb + socket la notifica.
(CondominioService: aggiunge solo del codice a quello che gia' esisteva)

Invece la notifica del power boost deve arrivare per forza dalla rete (il server amministratore non e' a conoscenza dei boost), non si puo'
riutilizzare un metodo gia' esistente.
Quando una casa ottiene il boost, manda ad-hoc un messaggio HTTP al server; NotificheService un servizio apposta per ricevere la notifica di 
boost dalla rete. Questo la riceve e inoltra alla ADMIN APP, come per entrata / uscita case.
======================================================================================================================================
