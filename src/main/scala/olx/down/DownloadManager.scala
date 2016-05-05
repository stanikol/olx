package olx.down

import akka.actor.{Actor, ActorRef, Props, Terminated}
import olx._
import olx.Log._
import olx.down.DownloadManager._
import org.joda.time.{DateTime, Period}
import org.slf4j.{LoggerFactory, MDC}

import scala.collection.mutable.{Set => MutebleSet}
import scala.util.Try

/**
  * Created by stanikol on 21.04.16.
  */
object DownloadManager {
  case class Tick()
  case class DownloadUrl(url: String, target: String)
  case class DownloadUrls(urls: Stream[String], target: String)
  case class JobPoolItem(actorRef: ActorRef, url: Option[String]) {
    def isFree = url.isEmpty
    def isBusy = url.isDefined
  }
  case class Finished(target: String, fetchedCount: Int, pagesCount: Int)
}


class DownloadManager extends Actor {
  import context.dispatcher
  implicit val mat = akka.stream.ActorMaterializer()

  private var target: String = "Default"
  private val jobPool = MutebleSet.empty[JobPoolItem]

//  private val saveAdvLogger = LoggerFactory.getLogger("SCRAP")

  override def preStart = {
    jobPool ++= 1 to Cfg.number_of_fetchers map { n =>
        val actor = context.actorOf(Props(classOf[Downloader]), name = s"Downl_$n")
        context.watch(actor)
        new JobPoolItem(actor, None) }
    storedLinks ++= Adv.readStoredURLs(Cfg.savedir).get
  }

  private var nextPage: Option[String] = None
  private val newLinks = MutebleSet.empty[String]
  private val fetchedLinks = MutebleSet.empty[String]
  private val storedLinks = MutebleSet.empty[String]
  private val errorLinks = MutebleSet.empty[String]
  private val visitedPages = MutebleSet.empty[String]
  private var nextPageRetry: Int = Cfg.next_page_retry
  private var masterActor: ActorRef = _
  private val startTime: DateTime = DateTime.now()



   private def receiveIdle: Receive = {
    case DownloadManager.DownloadUrl(url, target_) =>
      masterActor = sender()
      target = target_
      println(s"$target -> $url")
      nextPage = Some(url)
      nextPageRetry = Cfg.next_page_retry
      visitedPages.clear
      context.become(receiveBusy)
      self ! Tick()
    case DownloadManager.DownloadUrls(urls, target_) =>
      masterActor = sender()
      target = target_
      println(s"$target -> ${urls.length}")
      newLinks.clear
      newLinks ++= urls
      nextPage = None
      nextPageRetry = Cfg.next_page_retry
      visitedPages.clear
      context.become(receiveBusy)
      self ! Tick()
  }

  override def receive: Receive = receiveIdle

  private def receiveBusy: Receive = {
    case cmd @ (_:DownloadUrl | _:DownloadUrls) =>
      println(s"${self.path.name} error: received command $cmd, but I'm busy !")
    case Tick() =>
      printInfo
      fetchNextPageAndLinksIfReady
      fetchAdsIfFree
      if((nextPage.isEmpty  && jobPool.forall(_.isFree) && newLinks.isEmpty ) | nextPageRetry <= 0) {
        context.become(receiveIdle)
        masterActor ! Finished(target, fetchedLinks.toList.length, visitedPages.toList.length)
        newLinks.clear
//        fetchedLinks.clear
//        storedLinks.clear
        errorLinks.clear
        visitedPages.clear
        nextPageRetry = Cfg.next_page_retry
        nextPage = None
        nextPageRetry = Cfg.next_page_retry
      } else {
        context.system.scheduler.scheduleOnce(Cfg.tick_time) { self ! Tick() }
      }
    case adv @ Adv(items) =>
      val sender_ = sender()
      val url = items("url")
      val jobPoolItem = jobPool.find(_.actorRef == sender_).get
      jobPool -= jobPoolItem
      jobPool += jobPoolItem.copy(url=None)
      fetchedLinks += url
      saveAdv(adv, savePath)
      fetchAdsIfFree
      fetchNextPageAndLinksIfReady
    case advUrls @ AdvUrls(urls, currentPage, nextPg) =>
      val sender_ = sender()
      val newFoundLinks = urls.filter(url =>
        !(newLinks.contains(url) || storedLinks.contains(url) ||
          fetchedLinks.contains(url) || errorLinks.contains(url))
      )
      println(s"$target ${newFoundLinks.length} new links from ${urls.length} are found on ${currentPage}")
      println(s"$target visitedPages = ${visitedPages.toList.length} nextPage = ${nextPg.getOrElse("None")} nextPageRetry = $nextPageRetry  wasVisitedBefore = ${visitedPages.contains(nextPage.get)}")
      if( visitedPages.contains(nextPg.getOrElse("")) ) nextPageRetry -= 1
      visitedPages += currentPage
      newLinks ++= newFoundLinks
      nextPage = nextPg
      val jobPoolItem = jobPool.find(_.actorRef == sender_).get
      jobPool -= jobPoolItem
      if(newFoundLinks.length == 0 && nextPage.isDefined && nextPageRetry >= 0) {
        jobPoolItem.actorRef ! Downloader.FetchAdvUrls(nextPage.get)
        jobPool += jobPoolItem.copy(url=nextPage)
      } else {
        jobPool += jobPoolItem.copy(url=None)
      }
      fetchNextPageAndLinksIfReady
      fetchAdsIfFree
    case Error(url, error) =>
      val sender_ = sender()
      val jobPoolItem = jobPool.find(_.actorRef == sender_).get
      jobPool -= jobPoolItem
      jobPool += jobPoolItem.copy(url=None)
      errorLinks += url
      println(s"$target ${sender_.path.name} got error with $url: ${error}")
      fetchNextPageAndLinksIfReady
      fetchAdsIfFree
    case Terminated(actor) =>
      jobPool.find(_.actorRef == actor) match {
        case  Some(j @ JobPoolItem(actorRef, _)) => jobPool -= j
        case _ =>
      }
  }

  private def fetchNextPageAndLinksIfReady: Unit = {
//      if(!visitedPages.contains(nextPage.getOrElse("")) && nextPage.isDefined
//        && freeJob.isDefined && newLinks.isEmpty && nextPageRetry > 0) {
    if(!visitedPages.contains(nextPage.getOrElse("")) && nextPage.isDefined
        && jobPool.forall(_.isFree) && newLinks.isEmpty && nextPageRetry > 0) {
        val pageUrl = nextPage.get
        println(s"$target Going to fetch "+pageUrl)
        val freeJob = jobPool.find(_.isFree)
        val newJob = freeJob.get
        jobPool -= newJob
        jobPool += newJob.copy(url=Some(pageUrl))
        newJob.actorRef ! Downloader.FetchAdvUrls(pageUrl)
      }
  }

  private def fetchAdsIfFree: Unit = {
    var freeJob = jobPool.find(_.isFree)
    while(freeJob.isDefined && newLinks.nonEmpty){
          val url = newLinks.head
          newLinks -= url
          jobPool -= freeJob.get
          jobPool += freeJob.get.copy(url=Some(url))
          freeJob.get.actorRef ! Downloader.FetchAdv(url)
          freeJob = jobPool.find(_.url.isEmpty)
    }
  }

  private def timeElapsed: String = {
    val period = new Period(startTime, DateTime.now)
    DurationFormatters.durationWithMillis.print(period)
  }

  private def timeFor1Adv: String = Try {
    val millis: Long = new Period(startTime, DateTime.now()).toStandardDuration.getMillis
    DurationFormatters.durationWithMillis.print(
      new Period(millis / fetchedLinks.toList.length).normalizedStandard()
    )
  }.getOrElse("0 seconds")

  private def savePath: String = s"${Cfg.savedir.getAbsolutePath}/$target " +
    DateTime.now().toString(Cfg.save_path_time_suffix)

  private def printInfo = {
    println(
      s"""
         |Info: Time elapsed $timeElapsed - ~ $timeFor1Adv for 1 adv
         |target = $target
         |newLinks = ${newLinks.toList.length}
         |fetchedLinks = ${fetchedLinks.toList.length}
         |errorLinks = ${errorLinks.toList.length}
         |nextPage = ${nextPage.getOrElse("None")}
         |nextPageRetry = ${nextPageRetry}
         |visitedPages = ${visitedPages.toList.length}
         |jobPool = ${jobPool.filter(_.isFree).toList.length} free of ${jobPool.toList.length}
         |jobPoolURLs = ${jobPool.filter(_.isBusy).map(_.url.get).mkString(", ")}
         """.stripMargin)
  }
}
