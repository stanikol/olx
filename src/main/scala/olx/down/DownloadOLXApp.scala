package olx.down

import olx.down.DownloadManagerWS.Finished
import olx.down.scrap.OLXScrapWS
import olx.{DurationFormatters, Log}
import org.joda.time.{DateTime, Period, Duration => YodaDuration}
import akka.pattern.ask

/**
  * Created by stanikol on 21.04.16.
  */

class DownloadOLXMan extends DownloadManagerWS with OLXScrapWS

object DownloadOLXApp {

  import Log.rootLogger
  import akka.actor._
  import olx.Cfg

  import scala.collection.mutable.{Set => MutableSet}

  val actorSystem = ActorSystem("OLX")

  val finishedTargets: MutableSet[DownloadManagerWS.Finished] = MutableSet.empty

  class MainActor extends Actor {
      val downloadManager = actorSystem.actorOf(Props(classOf[DownloadOLXMan]), name = "DownloadOLXMan")
      val targetsIterator = Cfg.targets.iterator
      val startTime = DateTime.now()


      def receive: Receive = {
          case "Start" => offerWorkOrFinish
          case finished @ DownloadManagerWS.Finished(target, fetchedCount, pagesCount) =>
            rootLogger.info(s"Finished $target downloaded ${fetchedCount} ads from $pagesCount pages")
            finishedTargets += finished
            offerWorkOrFinish
      }
      private def offerWorkOrFinish = {
          if(targetsIterator.hasNext) {
            val (target, url) = targetsIterator.next()
            rootLogger.info(s"Starting $target $url")
            downloadManager ! DownloadManagerWS.DownloadUrl(url, target)
          } else {
            rootLogger.info(s"All targets are done:")
            rootLogger.info("\n"+
              (for(Finished(target, fetchedCount, pagesCount)<-finishedTargets.toList.sortWith(_.fetchedCount > _.fetchedCount))
                  yield f"\t\t$target%-12s\t+=\t$fetchedCount%8s\t($pagesCount)").mkString("\n") +
                f"\n\t\t${"Total"}%-12s\t==\t${finishedTargets.foldLeft(0){ case (acc, Finished(_, fetchedCount, _)) => acc + fetchedCount }}%8s"
            )
            rootLogger.info("Terminating all jobs !")
            val timeElapsed = DurationFormatters.duration.print( new Period(startTime, DateTime.now) )
            rootLogger.info(s"Time elapsed from start: $timeElapsed")
            actorSystem.terminate()
//            context.stop(self)
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
          if(new YodaDuration(startTime, DateTime.now()).compareTo(  new YodaDuration(Cfg.terminate_after)) > 0 )
              rootLogger.info(s"Terminated on Timeout ${Cfg.terminate_after}")}}
    }

//  implicit val materializer = ActorMaterializer()

}
