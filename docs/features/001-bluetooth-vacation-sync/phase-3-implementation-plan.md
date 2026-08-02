# Sincronizzazione vacanza via Bluetooth — Implementation Plan

**Stato:** Approvato — decisioni sciolte, in implementazione
**Autore:** Alberto Goldoni
**Data:** 2026-08-02
**Versione:** 1.1

---

## 1. Executive Summary

Due compagni di viaggio potranno avere lo stesso piano vacanza su entrambi i telefoni
senza reinserirlo a mano: dall'app, una vacanza (con le sue località e attività) si
invia al telefono vicino via Bluetooth, senza internet né account. Chi riceve vede
un'anteprima e conferma; se la vacanza era già stata ricevuta in passato viene
aggiornata, non duplicata. Effort stimato: ~7 giorni/uomo, sviluppatore singolo.

---

## 2. Obiettivo e motivazione

- **Problema che risolve:** oggi l'unico modo di condividere una vacanza è
  reinserirla manualmente sul secondo dispositivo, con inevitabili divergenze tra i
  due piani.
- **Metriche di successo:**
  - [ ] Trasferimento end-to-end completato in < 30 secondi tra due dispositivi associati
  - [ ] Re-invio della stessa vacanza: zero duplicati sul ricevente (criterio US-003)
  - [ ] Nessun caso di database corrotto o import parziale dopo errori di trasferimento
- **Legame con obiettivi di prodotto:** app personale; l'obiettivo è l'uso reale in
  coppia nella pianificazione delle prossime vacanze.

---

## 3. Scope

### Incluso

- Invio manuale di **una singola vacanza** (località + attività incluse) verso un
  dispositivo Bluetooth **già associato** (RFCOMM secure).
- Ricezione in modalità ascolto con anteprima e conferma esplicita prima dell'import.
- Import idempotente: il re-invio aggiorna la vacanza esistente (identificata da un
  nuovo `syncId` stabile), sostituendone integralmente il contenuto.
- Permessi runtime per API 31+ (`BLUETOOTH_CONNECT`) e permessi normal per API ≤ 30;
  attivazione Bluetooth via flusso di sistema (`ACTION_REQUEST_ENABLE`).
- Stato e esito del trasferimento visibili su entrambi i lati; operazione annullabile.

### Escluso (out of scope)

- Merge bidirezionale granulare — l'ultimo invio vince; complessità non giustificata
  per 2 utenti che pianificano insieme.
- Sync automatica in background — il trasferimento è sempre presidiato; evita
  foreground service e complessità di lifecycle.
- Discovery di dispositivi non associati — il pairing è demandato al sistema; elimina
  `BLUETOOTH_SCAN` e i permessi di localizzazione.
- Più di 2 dispositivi, canali alternativi (Wi-Fi Direct, QR, cloud, export file).
- Cifratura applicativa aggiuntiva — il link RFCOMM secure tra dispositivi bonded è
  sufficiente per il modello di minaccia (dati non sensibili, dispositivi fidati).

### Decisioni (sciolte il 2026-08-02)

| # | Decisione | Esito |
|---|-----------|-------|
| 1 | Contenuto dell'anteprima di ricezione | **Solo conteggi**: titolo, data, n. località, n. attività |
| 2 | Modifiche locali sul ricevente al re-invio | **Avviso esplicito** nell'anteprima quando la vacanza esiste già: accettando si sovrascrivono le modifiche locali |
| 3 | Dispositivi fisici di collaudo | **2 dispositivi Android 16 (API 36)**: si collauda su hardware reale solo il ramo `BLUETOOTH_CONNECT`; il ramo API ≤ 30 resta verificato a livello di codice |
| 4 | Cautele sulla migrazione DB | **Nessuna installazione con dati reali**: la migrazione v1→v2 non richiede backup preventivi |

---

## 4. User Stories e criteri di accettazione

### US-001 · Invio di una vacanza
**Priorità:** Must Have

Come utente che ha pianificato una vacanza, voglio inviarla al telefono del mio
compagno di viaggio via Bluetooth, per condividere il piano completo senza internet e
senza doverlo ridigitare.

**Criteri di accettazione:**
- [ ] Dal dettaglio vacanza è disponibile l'azione "Invia via Bluetooth" (icona nella TopAppBar)
- [ ] L'app elenca i dispositivi associati e permette di sceglierne uno
- [ ] Bluetooth spento → l'app propone l'attivazione tramite il flusso di sistema
- [ ] Permesso mancante (API 31+) → richiesta runtime, rifiuto gestito con messaggio chiaro
- [ ] Il payload include titolo, data di inizio, tutte le località (con flag baricentro) e tutte le attività

### US-002 · Ricezione con conferma
**Priorità:** Must Have

Come utente, voglio ricevere una vacanza da un dispositivo vicino e vederne
un'anteprima prima di confermare l'import, per non ritrovarmi dati indesiderati.

**Criteri di accettazione:**
- [ ] Dall'elenco vacanze è disponibile la modalità "Ricevi via Bluetooth" (ascolto)
- [ ] Alla ricezione compare l'anteprima (titolo, data, n. località, n. attività) con scelta Accetta/Rifiuta
- [ ] Se una vacanza con lo stesso `syncId` esiste già, l'anteprima avverte che accettando si sovrascrivono le modifiche locali
- [ ] Il rifiuto non lascia alcun dato nel database e notifica l'esito al mittente
- [ ] Dopo l'import la vacanza è subito visibile nell'elenco (i Flow Room aggiornano la UI automaticamente)

### US-003 · Aggiornamento senza duplicati
**Priorità:** Must Have

Come utente che ha già ricevuto una vacanza, voglio che un nuovo invio la aggiorni
invece di duplicarla, per mantenere l'elenco pulito.

**Criteri di accettazione:**
- [ ] Doppio invio della stessa vacanza → una sola vacanza sul ricevente, con il contenuto dell'ultimo invio
- [ ] Località/attività rimosse sul mittente tra i due invii non compaiono più sul ricevente
- [ ] Vacanze diverse con lo stesso titolo restano distinte (l'identità è il `syncId`)
- [ ] L'aggiornamento preserva l'`id` Room locale della vacanza: una schermata di dettaglio aperta sul ricevente non si rompe

### US-004 · Trasparenza dell'esito
**Priorità:** Should Have

Come utente, voglio vedere lo stato del trasferimento su entrambi i dispositivi, per
sapere con certezza se il piano è arrivato.

**Criteri di accettazione:**
- [ ] Entrambi i lati mostrano gli stati: in corso / completato / fallito / annullato
- [ ] Errori (connessione persa, payload corrotto, `formatVersion` incompatibile) → messaggio comprensibile, database intatto
- [ ] Annullabile da entrambi i lati finché non completato

---

## 5. Architettura tecnica

### Componenti coinvolti

```
 MITTENTE                                          RICEVENTE
 VacationDetailScreen ─ "Invia"                    VacationListScreen ─ "Ricevi"
        │                                                 │
 SyncSendScreen/ViewModel                          SyncReceiveScreen/ViewModel
        │                                                 │
 VacationSyncRepository ── VacationPayload ──►     VacationSyncRepository
   (export da Room)          (JSON, RFCOMM)          (anteprima → import)
        │                                                 │
 BluetoothVacationTransfer ══ socket RFCOMM ══     BluetoothVacationTransfer
   client (connect)          UUID SDP fisso          server (listen)
        │                                                 │
     SyncDao ──── AppDatabase v2 (Room) ────────────── SyncDao
```

Nuovo package `it.goldoni.vacations.sync` (payload, trasporto, repository) +
`SyncDao` in `data`; i DAO esistenti restano intatti. Dettaglio file in
[phase-2-analysis.md](phase-2-analysis.md), sezione A.

### Modifiche al data model

| Tabella/Tipo | Tipo modifica | Dettaglio |
|---|---|---|
| `vacations` | Modifica | Nuova colonna `syncId TEXT NOT NULL` + indice unico; migrazione v1→v2 con backfill `lower(hex(randomblob(16)))`; nuove righe con UUID generato in Kotlin |
| `AppDatabase` | Modifica | `version = 2`, registrazione `MIGRATION_1_2` e `SyncDao`; schema `2.json` committato |
| `VacationPayload` (nuovo, non persistito) | Nuovo | DTO JSON versionato: `{formatVersion, syncId, title, startEpochDay, places[{name, isMain, activities[name]}]}` — senza ID Room (semantica replace-all) |

### Protocollo di scambio (al posto di API/endpoint: nessun backend)

| Passo | Direzione | Contenuto |
|---|---|---|
| 1. Connessione | client → server | Socket RFCOMM secure su UUID SDP fisso dell'app (dispositivi già bonded) |
| 2. Payload | mittente → ricevente | `[4 byte lunghezza big-endian][JSON UTF-8]` |
| 3. Esito | ricevente → mittente | 1 byte: `0` importato · `1` rifiutato dall'utente · `2` errore |

Il ricevente rifiuta `formatVersion` superiore al proprio con errore esplicito.

### Breaking changes

| Componente | Tipo di breaking change | Piano di migrazione |
|---|---|---|
| `AppDatabase` | Il downgrade dell'app dopo l'aggiornamento a schema v2 non è supportato da Room (version mismatch all'apertura) | Nessun rollback dell'APK: strategia fix-forward; la migrazione è additiva e non tocca i dati esistenti |

---

## 6. Piano di implementazione

| ID | Task | Area | Stima (gg) | Dipende da | Responsabile |
|---|---|---|---|---|---|
| T-01 | `syncId` su `Vacation`, `MIGRATION_1_2`, schema `2.json`, test migrazione | Dati | 1,0 | — | Alberto |
| T-02 | Setup kotlinx.serialization (toml + gradle), DTO `VacationPayload` versionato | Dati | 0,5 | — | Alberto |
| T-03 | `BluetoothVacationTransfer`: server/client RFCOMM, framing, byte di esito, annullamento, errori | Trasporto | 2,0 | T-02 | Alberto |
| T-04 | `SyncDao` + `VacationSyncRepository`: export snapshot, import transazionale idempotente che preserva l'`id` locale (update vacanza in place, replace dei figli) | Logica | 1,0 | T-01, T-02 | Alberto |
| T-05 | UI invio: permessi (doppio ramo API), attivazione BT, picker dispositivi associati, stati | UI | 0,75 | T-03 | Alberto |
| T-06 | UI ricezione: ascolto, anteprima, Accetta/Rifiuta, esito | UI | 0,75 | T-03, T-04 | Alberto |
| T-07 | Infrastruttura test (source set + dipendenze), unit + instrumented, collaudo e2e su 2 dispositivi, verifica build release (R8) | Test | 0,75 | T-01…T-06 | Alberto |
| T-08 | Documentazione (docs + note d'uso) | Doc | 0,25 | T-07 | Alberto |

**Stima totale:** 7 giorni/uomo
**Breakdown:** Dati/Logica 2,5 gg · Trasporto 2 gg · UI 1,5 gg · Test 0,75 gg · Doc 0,25 gg

T-01 e T-02 sono indipendenti e possono partire subito; T-03 è il percorso critico.

---

## 7. Piano di test

**Strategia generale:** unit test JVM per il payload, instrumented test su Room
in-memory per migrazione e import, collaudo manuale end-to-end per il trasporto
(l'emulatore non supporta il Bluetooth). Il progetto non ha oggi alcuna infrastruttura
di test: viene creata in T-07 (evidenze in phase-2-analysis.md, sezione D).

### Test cases critici

| ID | Tipo | Descrizione | Priorità |
|---|---|---|---|
| TC-01 | Unit | Round-trip serializzazione `VacationPayload`; rifiuto `formatVersion` futuro; JSON malformato | Alta |
| TC-02 | Instrumented | Migrazione v1→v2 con `MigrationTestHelper`: dati preservati, `syncId` backfillati e unici | Alta |
| TC-03 | Instrumented | Import idempotente: primo import crea; secondo sostituisce senza duplicare; figli rimossi spariscono; `id` locale preservato | Alta |
| TC-04 | E2E manuale | Matrice su 2 dispositivi fisici Android 16: invio nuovo, re-invio (con avviso sovrascrittura), rifiuto, annullamento a metà, BT spento, permesso negato, `formatVersion` incompatibile | Alta |
| TC-05 | E2E manuale | Round-trip payload su APK **release** (R8/minify attivo) | Media |

### Definition of Done per QA

- [ ] TC-01/02/03 automatizzati e verdi (`./gradlew test connectedAndroidTest`)
- [ ] Matrice TC-04 eseguita su 2 dispositivi fisici (almeno un Android 12+) senza esiti inattesi
- [ ] TC-05 verificato sull'APK release firmato
- [ ] Nessun import parziale possibile: verifica che ogni errore lasci il DB allo stato precedente
- [ ] Schema `2.json` committato insieme alla migrazione
- [ ] Documentazione aggiornata (T-08)

---

## 8. Rischi e mitigazioni

| Rischio | Probabilità | Impatto | Mitigazione |
|---|---|---|---|
| Affidabilità RFCOMM variabile tra produttori | Media | Medio | Socket secure verso dispositivi bonded, framing con lunghezza esplicita, byte di esito applicativo, retry manuale lato UX; niente fallback via reflection |
| Doppio ramo permessi (API ≤ 30 vs 31+) | Media | Medio | Scope ridotto ai soli dispositivi associati (niente discovery → niente `BLUETOOTH_SCAN`/localizzazione); collaudo reale su Android 16 (ramo `BLUETOOTH_CONNECT`); il ramo legacy è solo permesso *normal*, senza richiesta runtime |
| R8/minify in release rompe la serializzazione | Bassa | Medio | TC-05 sull'APK release prima del rilascio; regole ProGuard aggiuntive solo se necessarie |
| Errore a metà trasferimento → dati incoerenti | Bassa | Alto | Import in un'unica transazione Room: o tutto o niente; l'anteprima avviene prima di toccare il DB |
| Trasferimento interrotto se l'app va in background | Media | Basso | Accettato per la v1 (trasferimento presidiato di pochi KB); documentato per l'utente |
| Emulatore senza Bluetooth (e KVM occupato da VirtualBox sull'host) | Alta | Medio | Collaudo esclusivamente su 2 dispositivi fisici; le build debug/release convivono (`applicationIdSuffix .debug`) e condividono l'UUID SDP, utile in collaudo |

---

## 9. Rollout e feature flag

**Strategia di rilascio:**
- [x] Deploy diretto (direct)
- [ ] Graduale con feature flag
- [ ] Canary release

**Feature flag:** non previsto — app personale distribuita manualmente (`install-all.sh`),
senza infrastruttura di flag; la feature è raggiungibile solo tramite azioni esplicite
dell'utente e non altera i flussi esistenti.

**Piano di rollback:**
1. Nessun rollback dell'APK: la migrazione DB v1→v2 impedisce il downgrade (Room
   rifiuta l'apertura con versione inferiore) — strategia **fix-forward**.
2. La migrazione è additiva (una colonna + un indice): in caso di bug nella feature si
   rilascia una build con le entry point UI disabilitate, lasciando lo schema v2.

---

## 10. Checklist di approvazione

| Revisione | Responsabile | Stato | Data |
|---|---|---|---|
| Revisione tecnica | Alberto Goldoni | ✅ Approvata | 2026-08-02 |
| Revisione prodotto | Alberto Goldoni | ✅ Approvata | 2026-08-02 |
| Stima approvata | Alberto Goldoni | ✅ Approvata | 2026-08-02 |
| Rischi accettati | Alberto Goldoni | ✅ Accettati | 2026-08-02 |
| Data di inizio confermata | Alberto Goldoni | ✅ 2026-08-02 | 2026-08-02 |

---

## Domande aperte

Tutte risolte il 2026-08-02 — vedi la tabella "Decisioni (sciolte il 2026-08-02)"
nella sezione 3:

1. **Anteprima di ricezione** → solo conteggi.
2. **Protezione modifiche locali** → avviso esplicito nell'anteprima.
3. **Parco dispositivi di collaudo** → 2 × Android 16 (API 36).
4. **Timing della migrazione DB** → nessuna installazione reale, nessuna cautela necessaria.

---

*Documento generato con la skill `claude-code-feature`.*
