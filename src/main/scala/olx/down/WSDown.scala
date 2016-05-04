package olx.down

import org.joda.time.{DateTime, Period}
import org.joda.time.format.PeriodFormatterBuilder


/**
  * Created by stanikol on 21.04.16.
  */
object WSDown {

  import akka.actor._
  import akka.pattern.ask
  import olx.Cfg

  import scala.concurrent.ExecutionContext

  /**
    * Created by stanikol on 06.04.16.
    */

  var nextTarget = false

  class MainActor extends Actor {
    def receive: Receive = {
      case "Done" => nextTarget = true
    }
  }

    def main(args: Array[String]) {

      if(!Cfg.savedir.exists()) Cfg.savedir.mkdirs()

      implicit val ec = ExecutionContext.Implicits.global

      val actorSystem = ActorSystem("olxDownloaders")
      val downMan = actorSystem.actorOf(Props(classOf[DownMan]), name = "DownMan")

      val startTime = DateTime.now()
      val durationFormatter = new PeriodFormatterBuilder()
        .appendDays()
        .appendSuffix(" day", " days")
        .appendSeparator(" ")
        .appendHours()
        .appendSuffix(" hour", " hours")
        .appendSeparator(" ")
        .appendMinutes()
        .appendSuffix(" minute", " minutes")
        .appendSeparator(" ")
        .appendSeconds()
        .appendSuffix(" second", " seconds")
        .toFormatter

      for((target, url)<-Cfg.targets){
        println(s"Starting $target $url")
        val f = ask(downMan, DownMan.DownloadUrl(url,target))(Cfg.terminate_after)
        while(!f.isCompleted){}
        println(s"Finished $target $url")
      }
      println("Terminating all jobs !")
      actorSystem.terminate()
      val timeElapsed = durationFormatter.print( new Period(startTime, DateTime.now) )
      println(s"Time elapsed $timeElapsed")

    }

}
