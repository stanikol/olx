package olx.down

import akka.actor.{Actor, ActorRef, Props, Terminated}
import akka.pattern.ask
import akka.util.Timeout
import olx.down.DownMan.{JobPoolItem, Tick}
import olx._
import org.joda.time.{DateTime, Period}
import org.joda.time.format.PeriodFormatterBuilder
import org.slf4j.{LoggerFactory, MDC}
import play.api.libs.ws.ahc.AhcWSClient
import play.api.libs.ws.{WSClient, WSResponse}

import scala.collection.mutable.{Set => MutebleSet}
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

/**
  * Created by stanikol on 21.04.16.
  */
object DownMan {
  case class Tick()
  case class Download(url: String)
  case class JobPoolItem(actorRef: ActorRef, url: Option[String])
}


class DownMan(target: String) extends Actor {

  val jobPool = MutebleSet.empty[JobPoolItem]

  private val saveAdvLogger = LoggerFactory.getLogger("SCRAP")
  private val savePath: String = s"${Cfg.savedir.getAbsolutePath}/$target " +
    DateTime.now().toString(Cfg.save_path_time_suffix)

  override def preStart = {
    jobPool ++= 1 to Cfg.number_of_fetchers map { n =>
        val actor = context.actorOf(Props(classOf[Downl]), name = s"Downl_$n")
        context.watch(actor)
        new JobPoolItem(actor, None) }
    storedLinks ++= Adv.readStoredURLs(Cfg.savedir).get
  }

  var nextPage: Option[String] = None
  val newLinks = MutebleSet.empty[String]
  val fetchedLinks = MutebleSet.empty[String]
  val storedLinks = MutebleSet.empty[String]
  val errorLinks = MutebleSet.empty[String]
  val visitedPages = MutebleSet.empty[String]

  var nextPageRetry: Int = 3

  private val startTime: DateTime = DateTime.now()

  override def receive: Receive = {
    case DownMan.Download(url) =>
      println(s"$target -> $url")
      nextPage = Some(url)
      self ! Tick()
    case Tick() =>
      println(
        s"""
           |Info: Time elapsed $timeElapsed - ~ $timeFor1Adv for 1 adv
           |newLinks = ${newLinks.toList.length}
           |fetchedLinks = ${fetchedLinks.toList.length}
           |errorLinks = ${errorLinks.toList.length}
           |nextPage = ${nextPage.getOrElse("None")}
           |nextPageRetry = ${nextPageRetry}
           |jobPool = ${jobPool.filter(_.url.isEmpty).toList.length} free of ${jobPool.toList.length}
         """.stripMargin)
      fetchNextLinksIfReady
      offerToFetchAds
      import context.dispatcher
      if((nextPage.isEmpty  && jobPool.forall(_.url.isEmpty) && newLinks.isEmpty ) | nextPageRetry <= 0)
        context.system.terminate()

      context.system.scheduler.scheduleOnce(Cfg.sleep_time) {
        self ! Tick()
      }

    case adv @ Adv(items) =>
      val sender_ = sender()
      val url = items("url")
      val jobPoolItem = jobPool.find(_.actorRef == sender_).get
      jobPool -= jobPoolItem
      jobPool += jobPoolItem.copy(url=None)
      fetchedLinks += url
      MDC.put("savePath", savePath)
      saveAdvLogger.info(adv.toString())
      offerToFetchAds

    case Error(url, error) =>
      val sender_ = sender()
      val jobPoolItem = jobPool.find(_.actorRef == sender_).get
      jobPool -= jobPoolItem
      jobPool += jobPoolItem.copy(url=None)
      errorLinks += url
      println(s"${sender_.path.name} got error with $url: ${error.getMessage}")
      offerToFetchAds

    case Terminated(actor) =>
      jobPool.find(_.actorRef == actor) match {
        case  Some(j @ JobPoolItem(actorRef, _)) => jobPool -= j
        case _ =>
      }
  }

  private def fetchNextLinksIfReady: Unit = {
//    val jobPoolIsFree = jobPool.filter(_.url.isDefined).isEmpty
    if(nextPage.isDefined && jobPool.filter(_.url.isDefined).isEmpty && newLinks.isEmpty) do {
      val pageUrl = nextPage.get
      println("Going to fetch "+pageUrl)
      implicit val timeout: Timeout = Cfg.kill_actor_when_no_response
      val advUrlsFuture: Future[AdvUrls] = { jobPool.head.actorRef ? Downl.FetchAdvUrls(pageUrl)  }.mapTo[AdvUrls]
      val advUrlsTry: Try[AdvUrls] = Try(Await.result(advUrlsFuture, Cfg.kill_actor_when_no_response))
      advUrlsTry match {
        case Success(AdvUrls(links:List[String], nxtPage: Option[String])) =>
          val newFoundLinks = links.filter(url => !(storedLinks ++ fetchedLinks ++ errorLinks ++ newLinks).contains(url))
          if( nxtPage.isEmpty || visitedPages.contains(pageUrl) ){
            nextPageRetry -= 1
          } else {
            nextPage = nxtPage
            if(nextPage.isDefined && !visitedPages.contains(nextPage.get))
              nextPageRetry = 3
          }
          newLinks ++= newFoundLinks
          visitedPages += pageUrl
          println(s"${newFoundLinks.length} new links from ${links.length} are found on $pageUrl")
          println(s"nextPage = ${nextPage.getOrElse("None")} nextPageRetry = $nextPageRetry  ${visitedPages.contains(pageUrl)}")
        case Failure(error: Throwable) =>
          println("Error while fetching "+pageUrl)
      }
    }while(nextPage.isDefined  && jobPool.filter(_.url.isDefined).isEmpty && newLinks.isEmpty && nextPageRetry > 0)
  }

  private def offerToFetchAds: Unit = {
    var freeJob = jobPool.find(_.url.isEmpty)
    while(freeJob.isDefined && newLinks.nonEmpty){
      val url = newLinks.head
      newLinks -= url
      jobPool -= freeJob.get
      jobPool += freeJob.get.copy(url=Some(url))
      freeJob.get.actorRef ! Downl.FetchAdv(url)
      freeJob = jobPool.find(_.url.isEmpty)
    }
  }

  implicit val mat = akka.stream.ActorMaterializer()

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
    .appendSeparator(" ")
    .appendMillis3Digit()
    .appendSuffix(" milis")
    .toFormatter

  def timeElapsed: String = {
    val period = new Period(startTime, DateTime.now)
    durationFormatter.print(period)
  }

  def timeFor1Adv: String = Try {
    val millis: Long = new Period(startTime, DateTime.now()).toStandardDuration.getMillis
    durationFormatter.print(
      new Period(millis / fetchedLinks.toList.length).normalizedStandard()
    )
  }.getOrElse("0 seconds")
}
