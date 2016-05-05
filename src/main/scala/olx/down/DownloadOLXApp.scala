package olx.down

import java.time.Duration

import olx.DurationFormatters
import olx.down.DownloadManager.Finished
import org.joda.time.{DateTime, Period}
import org.joda.time.format.PeriodFormatterBuilder

import scala.concurrent.Future
import scala.concurrent.duration.Duration


/**
  * Created by stanikol on 21.04.16.
  */
object DownloadOLXApp {

  import akka.actor._
  import akka.pattern.ask
  import olx.Cfg
  import scala.collection.mutable.{Set => MutableSet}
  import scala.concurrent.ExecutionContext

  /**
    * Created by stanikol on 06.04.16.
    */
  val actorSystem = ActorSystem("olxDownloaders")

  val finishedTargets: MutableSet[DownloadManager.Finished] = MutableSet.empty

  class MainActor extends Actor {
      val downloadManager = actorSystem.actorOf(Props(classOf[DownloadManager]), name = "DM")
      val targetsIterator = Cfg.targets.iterator
      val startTime = DateTime.now()

      def receive: Receive = {
          case "Start" => offerWorkOrFinish
          case finished @ DownloadManager.Finished(target, fetchedCount, pagesCount) =>
            println(s"Finished $target downloaded ${fetchedCount} ads from $pagesCount pages")
            finishedTargets += finished
            offerWorkOrFinish
      }
      private def offerWorkOrFinish = {
          if(targetsIterator.hasNext) {
            val (target, url) = targetsIterator.next()
            println(s"Starting $target $url")
            downloadManager ! DownloadManager.DownloadUrl(url, target)
          } else {
            println(s"All targets are done:")
            println(
              (for(Finished(target, fetchedCount, pagesCount)<-finishedTargets)
                  yield f"\t\t$target%-12s\t+=\t$fetchedCount%8s\t($pagesCount)").mkString("\n") +
                f"\n\t\t${"Total"}%-12s\t==\t${finishedTargets.foldLeft(0){ case (acc, Finished(_, fetchedCount, _)) => acc + fetchedCount }}%8s"
            )
            println("Terminating all jobs !")
            val timeElapsed = DurationFormatters.duration.print( new Period(startTime, DateTime.now) )
            println(s"Time elapsed from start: $timeElapsed")
            context.stop(self)
            actorSystem.terminate()
          }
      }
  }

    def main(args: Array[String]) {
      import actorSystem.dispatcher
      val startTime = DateTime.now()
      if(!Cfg.savedir.exists()) Cfg.savedir.mkdirs()
      val mainActor = actorSystem.actorOf(Props(classOf[MainActor]), name = "MainActor")
      mainActor ! "Start"
      actorSystem.scheduler.scheduleOnce(Cfg.terminate_after){
        actorSystem.terminate().map { _ =>
          if(new Duration(startTime, DateTime.now()).toNanos >  Cfg.terminate_after.toNanos)
              println(s"Terminated on Timeout ${Cfg.terminate_after}")}}
    }
}
