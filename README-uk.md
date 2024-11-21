# Парсер / Скрапер для об'яв та номерів телефонів з [www.olx.ua](http://www.olx.ua)
Цей застосунок завантажує об'яви та номера телефонів з [www.olx.ua](http://www.olx.ua) та зберігає їх у БД [H2 Database](https://www.h2database.com/html/main.html).


## Installation and running
1. Встановите [Docker](https://www.docker.com/) із docker-compose.
2. Завантажте код парсера на свій компютер:  
    ` git clone https://github.com/stanikol/olx `
3. Перейдите до директорії 'olx/docker': `cd olx/docker`
4. Запустить `docker-compose up --build` в перший раз, потім можно запускати `docker-compose up`.
5. Зачекайте декілька хвилин, перший запуск займає деякий час на завантаження та компілювання програми.  
6. Відкрийте у своєму броузері [http://localhost:8080/olx](http://localhost:8080/olx).
7. Оберіть параметри пошуку та натисніть "Start". Зверніть увагу, що категорію пошуку OLX потрібно вказати у нижньому вікні у якому відображається сайт `olx.ua`.  
8. H2 БД з завантаженими об'вами буде зберегтись у "olx/db/olxdb.mv.db"
9. Для експорту даних у CSV формат, виконуйте  `call CSVWRITE('out.csv', 'select * from ADS');`. CSV буде збережено у `olx/db/out.csv`

## Micro-service
Ви також можите послати POST запити до [http://localhost:8080/olx/run](http://localhost:8080/olx/run) .
Параметри:
- __name__ - назва
- __url__ - URL з параметрами пошуку з OLX
- __count__ - кількість об'яв
- __parsePhones__ - парсить телефоні номери

Наприклад це запустить завантаження об'яв з https://www.olx.ua/uk/nedvizhimost/odessa/q-совіньон/?currency=UAH
```
curl -X POST http://localhost:8080/olx/run \
     -H "Content-Type: application/x-www-form-urlencoded" \
     --data-urlencode "name=Test-1" \
     --data-urlencode "count=5" \
     --data-urlencode "url=https://www.olx.ua/uk/nedvizhimost/odessa/q-%D1%81%D0%BE%D0%B2%D1%96%D0%BD%D1%8C%D0%BE%D0%BD/?currency=UAH" 
```
Зверніть увагу, параметр пошуку повинен бути url-encoded. Користуючись [jq](https://jqlang.github.io/jq/), наприклад, можна зробити так:
```
SEARCH=$(echo "совіньон" | jq --raw-input --raw-output  @uri)
curl -X POST http://localhost:8080/olx/run \
     -H "Content-Type: application/x-www-form-urlencoded" \
     --data-urlencode "name=Test-1" \
     --data-urlencode "count=5" \
     --data-urlencode "url=https://www.olx.ua/uk/nedvizhimost/odessa/q-$SEARCH/?currency=UAH" 

```
Щоб зупинити усі завантаження, виконуйте
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

