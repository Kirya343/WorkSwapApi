# WorkSwap

Alusta lisätyön, palveluiden, tuotteiden ja ansioluetteloiden etsimiseen.

Projekti koostuu useista sovelluksista ja käyttöliittymistä.

---

## Teknologiat

* Backend: Java 21, Spring Boot (Web, Security, Data JPA, OAuth2, WebSocket, Ampq), Hibernate, MySQL/mariaDB, RabbitMq
* Frontend: React
* Integraatiot: Google OAuth2
* Reaaliaika: WebSocket (chat, ilmoitukset)
* Lokalisointi: RU, EN, FI
* Rakennus: Maven

---

## Projektin arkkitehtuuri

* **Sovellukset:**

  1. **Cloud (Pilvi)** — kaikkien sovellusten käyttämien tiedostojen tallennus ja käsittely
  2. **API** — REST API tietojen käsittelyyn, jota käyttäjä käyttää saadakseen tietoja tietokannoista. Ainoa sovellus, joka on suorassa yhteydessä käyttäjään.
  3. **Statistics (Tilastot)** — analytiikan kerääminen koko sivustolta

* **Kirjastot:**

  1. **Core** — ydin, jota kaikki sovellukset käyttävät. Sisältää datan käsittelypalvelut ja yleiset konfiguraatioluokat
  2. **Datasource** — moduuli tietokantamallien konfigurointiin. Sisältää kaikki mallit ja niiden repositoriot
  3. **Common** — kirjasto, joka sisältää DTO-luokat ja Enum-luokat objektien tyypittämiseen

* **Frontend:**

  1. **Admin** — hallinta- ja moderointisivusto
  2. **Production** — julkinen sivusto käyttäjille

---

## Sovellukset

### 1. Cloud

* Tarkoitus: tiedostojen tallennus ja käsittely: ilmoituskuvat, avatarit, käyttöehtojen ja tietosuojakäytäntöjen tekstitiedostot kaikilla kielillä.
* Teknologiat:

  * Spring Boot (sovelluksen yleinen toiminta)
  * ImageIO (kuvien pakkaus)
* Päätoiminnot:

  * Käyttäjäkuvien ja muiden sivustotiedostojen vastaanotto, pakkaus, käsittely ja tallennus

### 2. API

* Tarkoitus: käyttäjän tietopyyntöjen käsittely ja vastausten lähetys tietokannoista
* Teknologiat:

  * Spring Boot
  * WebSocket (chat)
  * RabbitMQ (yhteys tilastosovellukseen)
* Päätoiminnot:

  * Käyttäjältä saatujen tietojen käsittely ja tallennus
  * Hakujen suorittaminen tietokannoista ja tietojen palauttaminen käyttäjälle
  * Messengerin toteutus WebSocketin avulla
  * Komentojen säännöllinen lähetys tilastosovellukseen

### 3. Statistics

* Tarkoitus: analytiikan kerääminen ja käsittely kaikista sivuston tietokannoista
* Teknologiat:

  * Spring Boot
  * RabbitMQ (yhteys API-sovellukseen)
* Päätoiminnot:

  * Tietokantojen säännöllinen skannaus ja tilastojen tallennus
  * Jokaisen ilmoituksen tilastojen snapshot-tallennus
  * Online-käyttäjämäärän snapshot-tallennus

---

## Frontend

### Admin Panel

* Tarkoitus: pääsovelluksen hallinta
* Teknologiat:

  * React
* Päätoiminnot:

  * Käyttäjien moderointi
  * Ilmoitusten moderointi
  * Arvostelujen moderointi
  * Roolien ja oikeuksien hallinta
  * Kategorioiden hallinta (lisäys, muokkaus)
  * Sijaintien hallinta (lisäys, muokkaus)
  * Sivustotiimin tehtäväpalvelu

### Prod Frontend

* Tarkoitus: käyttäjäsivusto
* Teknologiat:

  * React
  * i18nNext (lokalisointi)
* Päätoiminnot:

  * Ilmoituskatalogin selaus
  * Ilmoitusten luonti ja muokkaus
  * Oman tilin hallinta

---

## Tietokanta

* **Center**
  Pääentiteetit:

  * User
  * UserSettings
  * Permission
  * Role
  * Listing
  * ListingTranslation
  * Category
  * Location
  * Image
  * Review
  * Report
  * Resume
  * Chat
  * ChatPartizipant
  * Message
  * News
  * NewsTranslation

* **Admin**
  Pääentiteetit:

  * Task
  * TaskComment

* **Stats**
  Pääentiteetit:

  * ListingView
  * ListingStatSnapshot
  * OnlineStatSnapshot

---

## Projektin käynnistys

### Cloud

1. Asenna Java 21
2. Lataa ydin
3. Lataa konfiguraatio
4. Lataa SSL-avaimet
5. Käynnistä sovellus

### API

1. Asenna Java 21
2. Lataa ydin
3. Lataa konfiguraatio
4. Lataa SSL-avaimet
5. Käynnistä sovellus

### Statistics

1. Asenna Java 21
2. Lataa ydin
3. Lataa konfiguraatio
4. Käynnistä sovellus

### Frontend (Admin / Prod)

1. Asenna Node.js
2. Liitä GitHub ja aseta Main-haara
3. Käynnistä sivusto

---

## Kehityssuunnitelmat

* Lisätä käyttäjän varoitus ennen chatin aloittamista
* Lisätä mahdollisuus ilmoittaa ilmoituksesta
* Lisätä mahdollisuus poistaa ilmoituksen arvostelu
* Lisätä mahdollisuus ladata avatar
* Siirtää chat erilliseen sovellukseen, joka kirjoitetaan GoLangilla

---

Хочешь, я сделаю ещё **двуязычную версию** (русский+финский рядом в таблице), чтобы удобно было и для команды, и для будущих партнёров?
