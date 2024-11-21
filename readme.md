## Parser / Scraper for classified advertisements and phone numbers from site [www.olx.ua](www.olx.ua)
This application downloads classified ads and phone numbers from [www.olx.ua](www.olx.ua)  and saves them to [H2 Database](https://www.h2database.com/html/main.html).
It's capable of scraping huge number of ads and phones within very short time.


## Installation and running
1. Install [Docker](https://www.docker.com/) with docker-compose.
2. Download the source code. Run
   ` git clone https://github.com/stanikol/olx `
3. Change dir to 'olx/docker'. `cd olx/docker`
4. Run `docker-compose up --build` for the first time, then use just `docker-compose up`.
5. Wait for a while till sbt downloads all the libraries needed and compiles the sources. This may take a while, when running for the first time.
6. Open [http://localhost:8080/olx](http://localhost:8080/olx) in your browser.
7. Set the search parameters and press "Start".
8. H2 database with downloaded ads will be stored in "olx/db/olxdb.mv.db"
9. If you want to export results into csv, just run `call CSVWRITE('out.csv', 'select * from ADS');` from DB page. CSV file is saved to `olx/db/out.csv`

## Micro-service
You can also send POST requests to [http://localhost:8080/olx/run](http://localhost:8080/olx/run) to start downloads.
Parameters are:

* **url** - OLX search URL
* **name** - name of the search
* **count** - max number of advertisements to download
* **parsePhones** - When `true` parse and save phones

You can send these params with POST requests as form data.
```
curl -X POST http://localhost:8080/olx/run \
     -H "Content-Type: application/x-www-form-urlencoded" \
     --data-urlencode "name=Test-1" \
     --data-urlencode "count=5" \
     --data-urlencode "url=https://www.olx.ua/uk/nedvizhimost/odessa/q-%D1%81%D0%BE%D0%B2%D1%96%D0%BD%D1%8C%D0%BE%D0%BD/?currency=UAH" 
```
Please note, that your search query term should be url encoded. You can use [jq](https://jqlang.github.io/jq/), for example:
```
SEARCH=$(echo "совіньон" | jq --raw-input --raw-output  @uri)
curl -X POST http://localhost:8080/olx/run \
     -H "Content-Type: application/x-www-form-urlencoded" \
     --data-urlencode "name=Test-1" \
     --data-urlencode "count=5" \
     --data-urlencode "url=https://www.olx.ua/uk/nedvizhimost/odessa/q-$SEARCH/?currency=UAH" 

```
To stop all downloads, run
```
curl -X POST http://localhost:8080/olx/stop
```

## Technology

This app relies on tremendous open source projects. Here's a few of them.

* [Scala](http://www.scala-lang.org)
* [Cats](https://typelevel.org/cats)
* [Cats Effect](https://typelevel.org/cats-effect)
* [Http4s](https://http4s.org)
* [FS2](https://fs2.io)
* [Selenium](https://www.selenium.dev)
* [H2 database](https://www.h2database.com)
* [Jsoup](https://jsoup.org/)


## License

The code is licensed under [Apache License v2.0](http://www.apache.org/licenses/LICENSE-2.0).

