# Feature 001 — Sincronizzazione di una singola vacanza via Bluetooth (2 dispositivi)

**Stato:** Fase 1 — Requisiti
**Data:** 2026-08-02
**Progetto:** app Android "Vacanze" (`it.goldoni.vacations`) — Kotlin, Jetpack Compose, Room, minSdk 26, targetSdk 36

---

## 1. Obiettivo e motivazione

Due persone che pianificano la stessa vacanza (es. una coppia o due compagni di viaggio)
vogliono avere lo stesso piano — vacanza, località e attività — su entrambi i telefoni,
senza dipendere da internet, account cloud o server.

Il Bluetooth è il canale ideale per questo caso d'uso: i due dispositivi sono fisicamente
vicini (si pianifica insieme), il volume dati è minimo (una vacanza serializzata pesa
pochi KB) e non serve alcuna infrastruttura.

**Problema risolto:** oggi l'unico modo di "condividere" una vacanza è reinserirla a mano
sul secondo dispositivo, con inevitabili divergenze tra i due piani.

## 2. Scope

### Incluso

- Invio di **una singola vacanza** (con tutte le sue località e attività) da un
  dispositivo A a un dispositivo B via Bluetooth Classic (RFCOMM).
- Ricezione con anteprima e conferma esplicita prima dell'import.
- Re-invio della stessa vacanza: sul ricevente la vacanza già presente viene
  **aggiornata** (sostituita integralmente), non duplicata. Richiede un identificatore
  stabile della vacanza indipendente dagli ID Room autoincrement.
- Gestione permessi runtime Bluetooth per tutte le versioni Android supportate
  (26 → 36, con il cambio di modello permessi di Android 12).
- Feedback di avanzamento e esito (successo / errore / annullato) su entrambi i lati.
- Accoppiamento (pairing) demandato al sistema: l'app lavora con dispositivi già
  associati o avvia l'associazione di sistema, non implementa un pairing proprio.

### Escluso (out of scope)

- Sincronizzazione **bidirezionale con merge granulare** (unione campo per campo di
  modifiche concorrenti): il trasferimento è unidirezionale, ultimo invio vince.
- Sync automatica in background o alla riconnessione: il trasferimento è sempre
  avviato manualmente dall'utente.
- Sincronizzazione dell'intero database (tutte le vacanze in un colpo solo).
- Più di 2 dispositivi contemporaneamente (niente broadcast/mesh).
- Canali alternativi (Wi-Fi Direct, cloud, QR code, file export).
- Cifratura applicativa aggiuntiva oltre a quella del link Bluetooth.

## 3. User Stories

1. **Invio** — Come utente che ha pianificato una vacanza, voglio inviarla al telefono
   del mio compagno di viaggio via Bluetooth, per condividere il piano completo senza
   internet e senza doverlo ridigitare.
2. **Ricezione** — Come utente, voglio ricevere una vacanza da un dispositivo vicino e
   vederne un'anteprima (titolo, data, numero di località e attività) prima di
   confermare l'import, per non ritrovarmi dati indesiderati nel mio elenco.
3. **Aggiornamento senza duplicati** — Come utente che ha già ricevuto una vacanza in
   passato, voglio che un nuovo invio della stessa vacanza aggiorni quella esistente
   invece di crearne una copia, per mantenere l'elenco pulito.
4. **Trasparenza dell'esito** — Come utente, voglio vedere lo stato del trasferimento
   (in corso, completato, fallito, annullato) su entrambi i dispositivi, per sapere con
   certezza se il piano è arrivato.

## 4. Criteri di accettazione

**US1 — Invio**
- [ ] Dal dettaglio vacanza (o dal suo menu nell'elenco) è disponibile l'azione "Invia via Bluetooth".
- [ ] L'app mostra l'elenco dei dispositivi Bluetooth associati e permette di sceglierne uno.
- [ ] Se il Bluetooth è spento, l'app chiede di attivarlo tramite il flusso di sistema.
- [ ] Se mancano i permessi runtime, l'app li richiede e gestisce il rifiuto con un messaggio chiaro.
- [ ] La vacanza inviata include titolo, data di inizio, tutte le località (con flag `isMain`) e tutte le attività.

**US2 — Ricezione**
- [ ] Esiste una modalità "Ricevi via Bluetooth" che mette il dispositivo in ascolto.
- [ ] Alla ricezione viene mostrata un'anteprima (titolo, data, n. località, n. attività) con scelta Accetta/Rifiuta.
- [ ] Il rifiuto non lascia alcun dato nel database.
- [ ] L'import avvenuto rende la vacanza immediatamente visibile nell'elenco.

**US3 — Aggiornamento senza duplicati**
- [ ] Inviando due volte la stessa vacanza allo stesso ricevente, sul ricevente esiste una sola vacanza con il contenuto dell'ultimo invio.
- [ ] Località e attività rimosse sul mittente dopo il primo invio non compaiono più sul ricevente dopo il secondo invio.
- [ ] Vacanze diverse con lo stesso titolo restano distinte (l'identità è l'ID stabile, non il titolo).

**US4 — Esito**
- [ ] Durante il trasferimento entrambi i lati mostrano uno stato "in corso".
- [ ] Errori (connessione persa, dati corrotti, versione payload incompatibile) producono un messaggio comprensibile e lasciano il database intatto.
- [ ] L'operazione è annullabile da entrambi i lati finché non è completata.

## 5. Rischi e dipendenze

### Tecnici

| Rischio | Impatto | Note |
|---|---|---|
| Modello permessi Bluetooth frammentato (API 26–30: `BLUETOOTH`/`ACCESS_FINE_LOCATION`; API 31+: `BLUETOOTH_CONNECT`/`BLUETOOTH_SCAN`) | Alto | Doppio percorso di richiesta permessi da testare su versioni diverse |
| Gli ID Room sono autoincrement locali: senza un identificatore stabile (UUID) l'idempotenza dell'import (US3) è impossibile | Alto | Serve migrazione schema Room v1 → v2 |
| Nessuna libreria di serializzazione nel progetto | Medio | Da aggiungere `kotlinx.serialization` (o equivalente) per il payload |
| Affidabilità RFCOMM variabile tra produttori (socket che cadono, `createRfcommSocket` capriccioso) | Medio | Protocollo con lunghezza-prefisso e checksum; retry lato UX |
| L'emulatore Android non supporta il Bluetooth | Medio | Test end-to-end solo su 2 dispositivi fisici |
| Evoluzione futura del payload (nuovi campi) | Basso | Versionare il payload fin dal primo giorno |

### Di progetto

- Servono **2 dispositivi fisici Android** per il collaudo (di cui idealmente uno con Android 12+ e uno pre-12).
- Nessuna dipendenza da servizi esterni o backend.

## 6. Stima effort

Team: 1 sviluppatore. Totale stimato: **~7 giorni/uomo**.

| Area | Attività | Stima |
|---|---|---|
| Dati | UUID stabile su `Vacation`, migrazione Room v1→v2, query di supporto | 1,0 g |
| Dati | DTO payload + serializzazione + versionamento formato | 0,5 g |
| Trasporto | Servizio Bluetooth RFCOMM (server in ascolto, client, protocollo framing, errori) | 2,0 g |
| Logica | Import transazionale con upsert idempotente (replace contenuto vacanza) | 1,0 g |
| UI | Azione "Invia", picker dispositivi associati, schermata "Ricevi", anteprima, stati e permessi | 1,5 g |
| Test | Unit (serializzazione, import/merge), collaudo manuale su 2 dispositivi | 0,75 g |
| Documentazione | Aggiornamento docs + note d'uso | 0,25 g |

## 7. Milestones

1. **M1 — Identità stabile dei dati**: campo `syncId` (UUID) su `Vacation`, migrazione Room v1→v2, schema esportato aggiornato.
2. **M2 — Payload**: DTO serializzabili di vacanza/località/attività, formato versionato, round-trip testato via unit test.
3. **M3 — Trasporto Bluetooth**: connessione RFCOMM tra 2 dispositivi associati con scambio di un payload di prova (server + client + framing).
4. **M4 — Import idempotente**: transazione Room che inserisce o sostituisce la vacanza ricevuta in base al `syncId`.
5. **M5 — UI di invio**: permessi runtime, attivazione BT, picker dei dispositivi associati, invio con stato e esito.
6. **M6 — UI di ricezione**: modalità ascolto, anteprima con Accetta/Rifiuta, import e feedback.
7. **M7 — Collaudo e rifinitura**: test end-to-end su 2 dispositivi fisici, gestione errori/annullamento, documentazione.
