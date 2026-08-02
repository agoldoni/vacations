# Feature 001 — Sincronizzazione vacanza via Bluetooth — Analisi tecnica

**Stato:** Fase 2 — Analisi della codebase
**Data:** 2026-08-02
**Riferimento:** [phase-1-requirements.md](phase-1-requirements.md)

Analisi condotta sull'intera codebase (18 file Kotlin/config, singolo modulo `app`,
commit `a171c73`). Tutti i percorsi citati sono stati verificati.

---

## A. File coinvolti

### Nuovi

| File | Motivazione |
|---|---|
| `app/src/main/java/it/goldoni/vacations/sync/VacationPayload.kt` | DTO serializzabili del payload (vacanza + località + attività), campo `formatVersion` per l'evoluzione futura. Niente ID Room nel payload: la semantica è replace-all |
| `app/src/main/java/it/goldoni/vacations/sync/BluetoothVacationTransfer.kt` | Trasporto RFCOMM: server in ascolto (`listenUsingRfcommWithServiceRecord`), client (`createRfcommSocketToServiceRecord`), UUID di servizio fisso, framing lunghezza-prefisso, byte di esito dal ricevente |
| `app/src/main/java/it/goldoni/vacations/sync/VacationSyncRepository.kt` | Ponte dati↔trasporto: costruisce il payload dal DB (export) e importa in transazione con upsert idempotente su `syncId` |
| `app/src/main/java/it/goldoni/vacations/data/SyncDao.kt` | Query di supporto sync: snapshot completo non-reattivo della vacanza, lookup per `syncId`, transazione di import. DAO dedicato per non toccare i DAO esistenti |
| `app/src/main/java/it/goldoni/vacations/ui/SyncSendScreen.kt` + `SyncSendViewModel.kt` | UI invio: permessi, attivazione BT, elenco dispositivi associati, stato trasferimento |
| `app/src/main/java/it/goldoni/vacations/ui/SyncReceiveScreen.kt` + `SyncReceiveViewModel.kt` | UI ricezione: modalità ascolto, anteprima con Accetta/Rifiuta, esito import |
| `app/schemas/it.goldoni.vacations.data.AppDatabase/2.json` | Schema v2 esportato (generato da KSP, da committare) |
| `app/src/test/java/it/goldoni/vacations/sync/VacationPayloadTest.kt` | Unit test round-trip serializzazione (source set **da creare**, vedi D) |
| `app/src/androidTest/java/it/goldoni/vacations/data/SyncDaoTest.kt` | Test import idempotente su Room in-memory (source set **da creare**, vedi D) |

### Da modificare

| File | Modifica |
|---|---|
| [Vacation.kt](../../../app/src/main/java/it/goldoni/vacations/data/Vacation.kt) | Nuovo campo `syncId: String` con default `UUID.randomUUID().toString()` e indice unico |
| [AppDatabase.kt](../../../app/src/main/java/it/goldoni/vacations/data/AppDatabase.kt) | `version = 2`, registrazione `SyncDao`, `MIGRATION_1_2` aggiunta al builder (righe 8–12 e 23–30) |
| [MainActivity.kt](../../../app/src/main/java/it/goldoni/vacations/MainActivity.kt) | Due route nuove nel `NavHost` (righe 32–48): `"sync/send/{vacationId}"` e `"sync/receive"` |
| [VacationDetailScreen.kt](../../../app/src/main/java/it/goldoni/vacations/ui/VacationDetailScreen.kt) | Azione "Invia via Bluetooth" nelle `actions` della `TopAppBar` (righe 85–89), accanto all'icona Modifica |
| [VacationListScreen.kt](../../../app/src/main/java/it/goldoni/vacations/ui/VacationListScreen.kt) | Azione "Ricevi via Bluetooth" nella `TopAppBar` (riga 56, oggi senza `actions`) |
| [AndroidManifest.xml](../../../app/src/main/AndroidManifest.xml) | Permessi Bluetooth (oggi assenti): `BLUETOOTH` con `maxSdkVersion="30"` e `BLUETOOTH_CONNECT` per API 31+ |
| [libs.versions.toml](../../../gradle/libs.versions.toml) | Plugin `kotlin-serialization` (stessa versione di Kotlin 2.1.21), libreria `kotlinx-serialization-json`, dipendenze di test (JUnit, androidx.test, room-testing) |
| [app/build.gradle.kts](../../../app/build.gradle.kts) | Applicazione plugin serialization, nuove dipendenze `implementation`/`testImplementation`/`androidTestImplementation` |

### Da eliminare

Nessuno.

## B. Contratti e interfacce da modificare

**Schema DB v1 → v2** (migrazione additiva, nessun breaking change per i dati esistenti):

```sql
ALTER TABLE vacations ADD COLUMN syncId TEXT NOT NULL DEFAULT '';
UPDATE vacations SET syncId = lower(hex(randomblob(16)));
CREATE UNIQUE INDEX index_vacations_syncId ON vacations(syncId);
```

Verificato in `app/schemas/.../1.json`: `vacations` ha oggi solo `id`, `title`,
`startEpochDay` (identityHash `678c05a1…`). Il backfill SQL produce hex a 32 caratteri
mentre le nuove righe avranno UUID canonici da Kotlin: formati diversi ma entrambi
stabili e unici — irrilevante per il protocollo, che tratta `syncId` come stringa opaca.

**Contratto payload (nuovo, JSON via kotlinx.serialization):**

```
VacationPayload v1 {
  formatVersion: Int = 1,
  syncId: String,
  title: String,
  startEpochDay: Long,
  places: [ { name: String, isMain: Boolean, activities: [String] } ]
}
```

Il ricevente rifiuta `formatVersion` maggiore del proprio con errore esplicito (US4).

**Protocollo di trasporto (nuovo):** UUID SDP fisso dell'app → framing `[4 byte
lunghezza big-endian][payload JSON UTF-8]` → il ricevente risponde con 1 byte di esito
(`0` importato, `1` rifiutato dall'utente, `2` errore). Socket **secure** RFCOMM: i
dispositivi sono già associati per requisito (Fase 1, scope).

**API interne esistenti:** nessuna modifica a firme esistenti. `VacationDao`, `PlaceDao`,
`ActivityDao` restano intatti; le query sync vivono nel nuovo `SyncDao`.

## C. Pattern da rispettare

Verificati direttamente nel codice:

- **Iniezione manuale**: niente DI framework; le dipendenze arrivano da
  `VacationsApplication.database` via `viewModelFactory { initializer { this[APPLICATION_KEY] … } }`
  ([VacationListViewModel.kt:32-39](../../../app/src/main/java/it/goldoni/vacations/ui/VacationListViewModel.kt#L32-L39),
  [VacationDetailViewModel.kt:65-72](../../../app/src/main/java/it/goldoni/vacations/ui/VacationDetailViewModel.kt#L65-L72)).
  I nuovi ViewModel sync seguono lo stesso schema, incluso il factory parametrico
  (`VacationDetailViewModel.factory(vacationId)`) per `SyncSendViewModel(vacationId)`.
- **Stato UI**: `StateFlow` + `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), …)`
  e `collectAsStateWithLifecycle()` lato Compose. Gli stati del trasferimento
  (Idle/Connecting/Transferring/Done/Error) come `StateFlow` di una sealed class.
- **Transazioni nei DAO**: metodi `@Transaction` con implementazione di default nel DAO
  stesso ([PlaceDao.kt:33-47](../../../app/src/main/java/it/goldoni/vacations/data/PlaceDao.kt#L33-L47)).
  L'import idempotente segue questo pattern in `SyncDao`.
- **Entità**: PK `autoGenerate`, FK con `onDelete = CASCADE` + `Index` sulla colonna FK,
  date come epoch-day `Long`. La delete-cascade esistente semplifica l'import
  replace-all: basta cancellare la vacanza per portarsi via località e attività.
- **UI text**: stringhe italiane hardcoded nei composable (non in `strings.xml`, che
  contiene solo `app_name`). Dialoghi di conferma con `AlertDialog` + `TextButton`
  ("Annulla" come dismiss). L'anteprima di ricezione riusa questo stile.
- **Navigazione**: route stringa con argomenti tipati nel `NavHost` di `MainActivity`
  ([MainActivity.kt:38-41](../../../app/src/main/java/it/goldoni/vacations/MainActivity.kt#L38-L41)).
- **Commenti**: KDoc e commenti inline in italiano, densità moderata.

## D. Test da creare o aggiornare

**Evidenza: il progetto non ha alcuna infrastruttura di test.** Non esistono i source
set `app/src/test/` né `app/src/androidTest/` (verificato: sotto `app/src/` ci sono solo
`main`, `debug`, `release`) e `app/build.gradle.kts` non dichiara alcuna dipendenza di
test. Vanno creati da zero.

| Area | Tipo | File | Cosa verifica |
|---|---|---|---|
| Payload | Unit (JVM) | `app/src/test/.../sync/VacationPayloadTest.kt` | Round-trip serializzazione/deserializzazione, rifiuto `formatVersion` futuro, payload malformato |
| Import | Instrumented | `app/src/androidTest/.../data/SyncDaoTest.kt` | Su Room in-memory: primo import crea la vacanza; secondo import con stesso `syncId` sostituisce senza duplicare; località/attività rimosse spariscono (criteri US3) |
| Migrazione | Instrumented | `app/src/androidTest/.../data/MigrationTest.kt` | `MigrationTestHelper` di `room-testing` con gli schemi esportati (già disponibili in `app/schemas/`): v1→v2 preserva i dati e backfilla `syncId` unici |
| Trasporto | Collaudo manuale | — | End-to-end su 2 dispositivi fisici (l'emulatore non supporta BT); matrice: invio nuovo, re-invio, rifiuto, interruzione a metà, BT spento, permesso negato |

Dipendenze da aggiungere: `junit` (test), `androidx.test.ext:junit` + `androidx.test:runner` +
`room-testing` (androidTest), più `testInstrumentationRunner` in `defaultConfig`.

## E. Rischi tecnici aggiornati

Rispetto alla Fase 1, con evidenze dalla codebase:

| Rischio (Fase 1) | Aggiornamento |
|---|---|
| Permessi frammentati — **Alto** | **Ridimensionato a Medio.** Lo scope "solo dispositivi già associati" elimina la discovery: niente `BLUETOOTH_SCAN` né permessi di localizzazione. Su API ≤ 30 `BLUETOOTH` è un permesso normal (nessuna richiesta runtime); su API 31+ serve il solo runtime `BLUETOOTH_CONNECT` (anche per `ACTION_REQUEST_ENABLE`). Due rami, ma piccoli |
| ID locali senza identità stabile — **Alto** | **Confermato.** Schema v1 verificato: nessun candidato a ID stabile. La migrazione additiva in B è però a basso rischio; `exportSchema = true` è già attivo e gli schemi committati rendono testabile la migrazione |
| Nessuna libreria di serializzazione — **Medio** | **Confermato.** Kotlin 2.1.21: plugin serialization allineato alla stessa versione, nessun conflitto prevedibile |
| Affidabilità RFCOMM — **Medio** | **Confermato.** Mitigazione: socket secure verso dispositivi bonded, framing con lunghezza esplicita, byte di esito applicativo. Da evitare il fallback `createRfcommSocket` via reflection |
| Emulatore senza BT — **Medio** | **Confermato.** Nota ambiente: sull'host KVM è occupato da VirtualBox, quindi anche l'emulatore gira solo `-accel off` — il collaudo su 2 dispositivi fisici è l'unica via realistica |
| Evoluzione payload — **Basso** | **Confermato**, coperto da `formatVersion` fin dal primo giorno |

**Nuovi rischi emersi dall'analisi:**

- **R8/minify in release**: `isMinifyEnabled = true` ([app/build.gradle.kts:40](../../../app/build.gradle.kts#L40)).
  kotlinx.serialization include consumer rules, ma la build release va verificata
  esplicitamente (round-trip payload su APK release) prima del rilascio. Impatto: Medio.
- **Trasferimento solo in foreground**: senza un foreground service, il transfer vive
  nello scope del ViewModel: se l'utente lascia l'app durante il trasferimento, la
  connessione cade. Accettato per la v1 (trasferimento presidiato di pochi KB); da
  dichiarare nel documento finale. Impatto: Basso.
- **Debug e release convivono** (`applicationIdSuffix = ".debug"`): entrambe le build
  espongono lo stesso UUID SDP, quindi una build debug può parlare con una release.
  Utile in collaudo, ma attenzione a quale delle due app è in ascolto. Impatto: Basso.

## F. Prerequisiti e task bloccanti

1. **Plugin e libreria kotlinx.serialization** (blocca M2 — payload): modifica a
   `libs.versions.toml` + `app/build.gradle.kts`. Nessun impatto sul codice esistente.
2. **Migrazione schema v1→v2 con `syncId`** (blocca M4 — import idempotente): da fare
   per prima e committare subito lo schema `2.json` generato.
3. **Infrastruttura di test** (blocca i test automatici di M2/M4): creazione source set
   e dipendenze; non blocca lo sviluppo della feature in sé.
4. **Nessun refactoring preliminare necessario**: la codebase è piccola, coerente e i
   DAO esistenti non vanno toccati; la delete-cascade già presente supporta la semantica
   replace-all dell'import.
