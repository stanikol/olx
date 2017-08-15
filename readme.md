# Advertisement's Downloader (Grabber) from site [www.olx.ua](www.olx.ua) 

Aim of this little toy project is to create a simple microservice to download (grab) advertisements from [www.olx.ua](www.olx.ua).
Both advertisements' text and phone numbers.  


## Installation

    (1) Download SBT from http://www.scala-sbt.org/
    (2) Download source code (git clone https://github.com/stanikol/olx)
    (3) In terminal from folder (which you have dowloaded source code in) issue `sbt run` command.
    (4) Wait for a while till sbt downloads all the libraries needed.
    (5) Than in your browser open url `http://localhost:8080/`

    
## Running the app

To download advertisements from [www.olx.ua](www.olx.ua) just copy-paste from  your initial query url from site (www.olx.ua) into URl input 
box, set number of ads to download and press "GO" button, and in several seconds you'll start to receive 
data from www.olx.ua in JSON format. Note that data is streamed back to you, this means that you will get requested 
ads not all at once, but in chunks. So wait for a while to get all your data downloaded.

Feel free also to send POST requests to http://localhost:8080/ to get back stream of JSON data. 
Parameters for requests are `url` - url from www.olx.ua, and `max` - nuber of ads to download.
You can send these params with POST reqests as JSON, form data  or path params.


## Technology

This app relies on tremendous open source projects. Here's a few of them.

* [Scala](http://www.scala-lang.org)
* [Akka](http://akka.io)
* [Jsoup](https://jsoup.org/)


## License

The code is licensed under [Apache License v2.0](http://www.apache.org/licenses/LICENSE-2.0).


# Скачиватель объявлений (Граббер) с сайта [www.olx.ua](www.olx.ua) 

Этот игрушечный проект является микросервисосом для скачивания объявлений с 
сайта [www.olx.ua](www.olx.ua) (объявлений вместе с номерами телефонов).

## Инсталаяция
    (1) Загрузите и установите SBT по ссылке http://www.scala-sbt.org/
    (2) Загрузите исходный код этого проекта (git clone https://github.com/stanikol/olx)
    (3) В терминале, из папки в которой находятся исходники, запустите команду `sbt run`
    (4) Подождите, пока система обновиться и загрузит требуемые библиотеки.
    (5) В окне Вашего броузера откройте url `http://localhost:8080/`
    
## Использование программы

Чтобы скачать объявления с сайта [www.olx.ua](www.olx.ua) просто скопируйте URL из 
адресной строки в поле URL, укажите требуемое количество объявлений и нажмите кнопуку "GO".
Через несколько секунд Вы начнете получать объявления вместе с номерами телефонов в формате JSON.
Обратите внимание, данные поступают ввиде потока, что означает что запрошенные данные будут поступать 
не все сразу, а по частям, поэтому для получения всех запрошенных данных, ожидайте до полного 
окончания загрузки.

Посылая POST запросы http://localhost:8080/ с параметрами url и max получаете в ответ поток с JSON.
Эти параметры можно посылать в теле запроса как JSON или form data, или через url, (?) как параметры пути. 
 