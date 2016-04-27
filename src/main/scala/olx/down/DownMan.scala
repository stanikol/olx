package olx.down

import akka.actor.{Actor, ActorRef, Props, Terminated}
import olx.down.DownMan.{JobPoolItem, Tick}
import olx._
import org.joda.time.{DateTime, Period}
import org.joda.time.format.PeriodFormatterBuilder
import org.slf4j.{LoggerFactory, MDC}

import scala.collection.mutable.{Set => MutebleSet}
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

/**
  * Created by stanikol on 21.04.16.
  */
object DownMan {
  case class Tick()
  case class DownloadUrl(url: String)
  case class DownloadUrls(urls: Stream[String])
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
  var masterActor: ActorRef = _

  var nextPageRetry: Int = 3

  private val startTime: DateTime = DateTime.now()

  override def receive: Receive = {
    case DownMan.DownloadUrl(url) =>
      masterActor = sender()
      println(s"$target -> $url")
      nextPage = Some(url)
      self ! Tick()
    case DownMan.DownloadUrls(urls) =>
      masterActor = sender()
      println(s"$target -> ${urls.length}")
      newLinks ++= urls
      nextPage = None
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
        masterActor ! "Done"

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

    case advUrls @ AdvUrls(urls, nextPg) =>
      val sender_ = sender()
      val newFoundLinks = urls.filter(url =>
        !(newLinks.contains(url) || storedLinks.contains(url) ||
          fetchedLinks.contains(url) || errorLinks.contains(url))
      )
      println(s"${newFoundLinks.length} new links from ${urls.length} are found on ${nextPage.get}")
      println(s"nextPage = ${nextPg.getOrElse("None")} nextPageRetry = $nextPageRetry  ${visitedPages.contains(nextPage.get)}")
      visitedPages += nextPage.get
      newLinks ++= newFoundLinks
      nextPage = nextPg
      val jobPoolItem = jobPool.find(_.actorRef == sender_).get
      jobPool -= jobPoolItem
      if(newFoundLinks.length == 0 && nextPage.isDefined) {
        jobPoolItem.actorRef ! Downl.FetchAdvUrls(nextPage.get)
        jobPool += jobPoolItem.copy(url=nextPage)
      } else {
        jobPool += jobPoolItem.copy(url=None)
      }
//      fetchedLinks += nextPage.get
//      self ! Tick()



    case Error(url, error) =>
      val sender_ = sender()
      val jobPoolItem = jobPool.find(_.actorRef == sender_).get
      jobPool -= jobPoolItem
      jobPool += jobPoolItem.copy(url=None)
      errorLinks += url
      println(s"${sender_.path.name} got error with $url: ${error}")
      offerToFetchAds

    case Terminated(actor) =>
      jobPool.find(_.actorRef == actor) match {
        case  Some(j @ JobPoolItem(actorRef, _)) => jobPool -= j
        case _ =>
      }
  }

  private def fetchNextLinksIfReady: Unit = {
//    val jobPoolIsFree = jobPool.filter(_.url.isDefined).isEmpty
    if(!visitedPages.contains(nextPage.getOrElse("")) && nextPage.isDefined && jobPool.filter(_.url.isDefined).isEmpty && newLinks.isEmpty) {
      val pageUrl = nextPage.get
      println("Going to fetch "+pageUrl)
      val freeJob = jobPool.head
      jobPool -= freeJob
      jobPool += freeJob.copy(url=Some(pageUrl))
      freeJob.actorRef ! Downl.FetchAdvUrls(pageUrl)
    }
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
