package olx.down

import org.joda.time.{DateTime, Period}
import org.joda.time.format.PeriodFormatterBuilder

import scala.concurrent.Future


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
  val actorSystem = ActorSystem("olxDownloaders")

  class MainActor extends Actor {
    val downloadManager = actorSystem.actorOf(Props(classOf[DownloadManager]), name = "DM")
    val targetsIterator = Cfg.targets.iterator
    val startTime = DateTime.now()

    def receive: Receive = {
      case "Start" => offerWork
      case DownloadManager.Finished(target, fetchedCount) =>
        println(s"Finished $target downloaded ${fetchedCount} ads")
        offerWork
    }
    private def offerWork = {
      if(targetsIterator.hasNext) {
        val (target, url) = targetsIterator.next()
        println(s"Starting $target $url")
        downloadManager ! DownloadManager.DownloadUrl(url, target)
      } else {
        println(s"All targets are done.")
        println("Terminating all jobs !")
        val timeElapsed = durationFormatter.print( new Period(startTime, DateTime.now) )
        println(s"Time elapsed $timeElapsed")
        context.stop(self)
        actorSystem.terminate()
      }
    }
  }

    def main(args: Array[String]) {

      if(!Cfg.savedir.exists()) Cfg.savedir.mkdirs()


      val mainActor = actorSystem.actorOf(Props(classOf[MainActor]), name = "MainActor")
      mainActor ! "Start"


    }

    private val durationFormatter = new PeriodFormatterBuilder()
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
}
