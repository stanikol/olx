# Parser / Scraper for classified advertisements and phone numbers from site [www.olx.ua](www.olx.ua)
This application downloads classified ads and phone numbers from [www.olx.ua](www.olx.ua)  and saves them to MongoDB.
It's capable of scraping huge number of ads and phones within very short time. 
Speed depends on quantity and quality of proxies you are using. You can also use direct (no proxy) connection.  
Number of requests running in parallel can be easily configured.   


## Installation and running
1. Download & install Scala Build Tool from [http://www.scala-sbt.org/](http://www.scala-sbt.org/)
2. Download the source code. Run ` git clone https://github.com/stanikol/olx `
3. In terminal go to the folder you have downloaded source code in, then run `sbt buildOlx`.
4. Wait for a while till sbt downloads all the libraries needed and compiles the sources. This may take some time. 
5. Download & install MongoDB [https://docs.mongodb.com/manual/administration/install-community/](https://docs.mongodb.com/manual/administration/install-community/)
6. Create mongodb database and run mongo. The simplest way is to run commands below:
   ```
   $ mkdir olx 
   $ mongod -dbpath olx  
   ```
   If you use another name for your mongodb database (not `olx`), please change `mongo.db` entry in config file `olx.conf` located in `bin` folder.
7. Go to the newly created './bin' folder `cd bin`  and run `java -jar olx.jar`
8. Then in your browser open url `http://localhost:8080/` 
9. Configuration settings are in `olx.conf` file. If no `olx.conf` is found in the current dir then defaults will be used.


## Configuration
All configuration is in `olx.conf`. See it for further details.
    
Brief list commands for Mac users
```
$ brew install sbt
$ git clone https://github.com/stanikol/olx
$ cd olx
$ sbt buildOlx
$ brew install mongodb-community
$ mkdir olx 
$ mongod -dbpath olx 
$ cd bin
$ java -jar olx.jar
```

    
## Running the app
To download advertisements from [www.olx.ua](www.olx.ua) just copy-paste query url from the [site](www.olx.ua) into URl input 
box, set number of ads to download and press "GO" button, and in several seconds you'll start to receive 
data from www.olx.ua in JSON format. 
Note that data is streamed back to you, this means that you will get requested ads not all at once, but in chunks. 
So wait for a while to get all your data downloaded.

## Micro-service
You can also send POST requests to http://localhost:8080/download to get back stream of JSON data. 
Parameters are: 
    olxUrl - OLX search URL
    max - max number of advertisements to download
    collection - Mongo collection name
    parsePhones - When `true` parse and save phones

You can send these params with POST requests in JSON, as form data or as path params.
```
curl -i -X POST \
   -H "Content-Type:application/json" \
   -d \
'{      
    "olxUrl": "https://www.olx.ua/nedvizhimost/kvartiry-komnaty/odessa/?currency=USD",
    "max": 4,
    "collection": "olx-odesa-flats",
    "parsePhones": true
}
' 'http://localhost:8080/download'
```


## Technology

This app relies on tremendous open source projects. Here's a few of them.

* [Scala](http://www.scala-lang.org)
* [Akka](http://akka.io)
* [Jsoup](https://jsoup.org/)


## License

The code is licensed under [Apache License v2.0](http://www.apache.org/licenses/LICENSE-2.0).


# Парсер (Граббер, Скачиватель) объявлений и номеров телефонов с сайта [www.olx.ua](www.olx.ua) 

Этот проект является микросервисосом для скачивания объявлений с 
сайта [www.olx.ua](www.olx.ua) (объявлений вместе с номерами телефонов).

## Инсталаяция
1 Загрузите и установите SBT по ссылке [http://www.scala-sbt.org/]
2 Загрузите исходный код этого проекта ` git clone https://github.com/stanikol/olx `
3 В терминале, из папки в которой находятся исходники, запустите команду `sbt buildOlx`
4 Подождите, пока система обновиться и загрузит требуемые библиотеки.
5 Загрузите и установите MongoDB [https://docs.mongodb.com/manual/administration/install-community/]
6 Запустите MongoDB, создав базу с именем `olx`. Например исполнив следующие команды:
``` 
$ mkdir olx 
$ mongod -dbpath olx  
```
  Если Вы хотите испльзовать другое имя для базы данных (не `olx`), внесите изменения в параметр `mongo.db` в файле конфигурации `olx.conf` который расположен в папке `bin`.
7 Перейдите в папку './bin'  ( `cd bin` )  и запустите сервис с помощю комманды `java -jar olx.jar`
8 В окне Вашего броузера откройте url `http://localhost:8080/`
9 Файл с настройками `olx.conf` должен находится в текущей папке. Если `olx.conf` не найден в текущей папке используются настройки по умолчению.
    
## Использование программы

Чтобы скачать объявления с сайта [www.olx.ua](www.olx.ua) просто скопируйте URL из 
адресной строки в поле URL, укажите требуемое количество объявлений и нажмите кнопуку "GO".
Через несколько секунд Вы начнете получать объявления вместе с номерами телефонов в формате JSON.
Обратите внимание, данные поступают ввиде потока, что означает что запрошенные данные будут поступать 
не все сразу, а по частям, поэтому для получения всех запрошенных данных, ожидайте до полного 
окончания загрузки.

## Микросервис

Посылая POST запросы http://localhost:8080/ с параметрами `url` и `max` получаете в ответ поток с JSON.
Эти параметры можно посылать в теле запроса как JSON или form data, или через url, (?) как параметры пути. 
 