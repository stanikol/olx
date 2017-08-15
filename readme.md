# Grabber for advertisement from [www.olx.ua](www.olx.ua) 

Aim of this little toy project is to create a simple tool to download (grab) both 
advertisements text and phone numbers from [www.olx.ua](www.olx.ua).  


## Installation

    (1) Download SBT from http://www.scala-sbt.org/
    (2) Download source code (git clone https://github.com/stanikol/olx)
    (3) In terminal from folder (which you have dowloaded source code in) issue `sbt run` command.
    (4) Wait for a while till sbt downloads all the libraries needed.
    (5) Than in your browser open url `http://localhost:8080/`

    
## Running the app

Just copy-paste from  your initial query url from site (www.olx.ua) into URl input 
box, set number of ads to download and press "GO" button, and in several seconds you'll start to receive 
data from www.olx.ua in JSON format. Note that data is streamed back to you, this means that you will get requested 
ads not all at once, but in chunks. So wait for a while to get all your data downloaded.


## Technology

This app relies on tremendous open source projects. Here's a few of them.

* [Scala](http://www.scala-lang.org)
* [Akka](http://akka.io)
* [Jsoup](https://jsoup.org/)


## License

The code is licensed under [Apache License v2.0](http://www.apache.org/licenses/LICENSE-2.0).


# Граббер объявлений с [www.olx.ua](www.olx.ua) 

Этот игрушечный проект является инструментом для скачивания с сайта [www.olx.ua](www.olx.ua) объявлений вместе с номерами телефонов.

## Инсталаяция
    (1) Загрузите и установите SBT по ссылке http://www.scala-sbt.org/
    (2) Загрузите исходный код этого проекта (git clone https://github.com/stanikol/olx)
    (3) В терминале, из папки в которой находятся исходники, запустите команду `sbt run`
    (4) Подождите, пока система обновиться и загрузит требуемые библиотеки.
    (5) В окне Вашего броузера откройте url `http://localhost:8080/`