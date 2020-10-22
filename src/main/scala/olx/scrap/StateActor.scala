package olx.scrap

import java.io.PrintWriter

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, PostStop}
import olx.{Config, Order}

import scala.io.{BufferedSource, Source}
import scala.util.{Try, Using}

object StateActor {
  def apply(): Behavior[StateActor.Request] = {
    val proxies: Array[HttpProxy] = Config.proxies.map(proxiesFile =>
      Using(Source.fromFile(proxiesFile)) { source: BufferedSource =>
        val proxiesFromFile = source
          .getLines()
          .filterNot(_.startsWith("#"))
          .filterNot(_.startsWith("//"))
          .filterNot(_.isEmpty)
          .map(_.replace(":", "\t"))
          .map(_.split("\\t"))
          .map { l =>
            HttpProxy(l(0), l(1).toInt)
          }.toArray
        assert(proxiesFromFile.length > 0, s"Proxy list is empty. Check proxy file at ${Config.proxies}!")
        proxiesFromFile
      }.get).toList.flatten.toArray
    stateActor(proxies)
  }

  def stateActor(
    proxies: Array[HttpProxy],
    index: Int = 0,
    proxiesPerfomance: Map[HttpProxy, ProxyPerformance] = Map.empty,
    urls: Map[Order, Set[String]] = Map.empty
  ): Behavior[StateActor.Request] =
    Behaviors.setup { context =>
      Behaviors
        .receiveMessage[StateActor.Request] {

          case GetProxy(replyTo) =>
            val nextProxy: HttpProxy = proxies(index)
            replyTo ! nextProxy
            val nextProxyIndex: Int =
              if (index <= proxies.length - 1) index else 0
            stateActor(proxies, nextProxyIndex, proxiesPerfomance, urls)

          case ProxyUseResult(proxy: HttpProxy, Left(_)) =>
            val performance: ProxyPerformance =
              proxiesPerfomance.getOrElse(proxy, ProxyPerformance()).bad
            val proxyPerformanceUpdated: Map[HttpProxy, ProxyPerformance] =
              proxiesPerfomance.updated(proxy, performance)
            val proxiesPerformancesTable: String = proxyPerformanceUpdated
              .map {
                case (HttpProxy(host, port), ProxyPerformance(total, good)) =>
                  s"$host\t$port\t$total\t$good\t${(good
                    .asInstanceOf[Double] / total * 100).round / 100.0}"
              }
              .mkString("\n")
            context.log.warn(
              "Proxies performance:\n{}",
              proxiesPerformancesTable
            )
            val isLastIndex = index == proxies.length - 1
            val newIndex =
              if (proxies.indexOf(proxy) == index && isLastIndex) 0
              else if (proxies.indexOf(proxy) == index && !isLastIndex)
                index + 1
              else index
            stateActor(proxies, newIndex, proxyPerformanceUpdated, urls)

          case ProxyUseResult(proxy: HttpProxy, Right(_)) =>
            val performance: ProxyPerformance =
              proxiesPerfomance.getOrElse(proxy, ProxyPerformance()).good
            val proxiesPerformancesUpdated: Map[HttpProxy, ProxyPerformance] =
              proxiesPerfomance.updated(proxy, performance)
            stateActor(proxies, index, proxiesPerformancesUpdated, urls)

          case CheckAndStoreUrl(
                replyTo: ActorRef[Boolean],
                order: Order,
                url: String
              ) =>
            val orderUrls: Set[String] =
              urls.getOrElse(order, Set.empty[String])
            val contained: Boolean = orderUrls.contains(url)
            replyTo ! contained
            stateActor(
              proxies,
              index,
              proxiesPerfomance,
              urls.updated(order, orderUrls + url)
            )

          case DeleteUrl(order: Order, url: String) =>
            val orderUrls: Set[String] =
              urls.getOrElse(order, Set.empty[String])
            stateActor(
              proxies,
              index,
              proxiesPerfomance,
              urls.updated(order, orderUrls - url)
            )
        }
        .receiveSignal {
          case (context, PostStop) if Config.proxies.isDefined =>
            new PrintWriter(Config.proxiesChecked) {
              proxiesPerfomance.foreach {
                case (HttpProxy(host, port), ProxyPerformance(total, good)) =>
                  write(s"$host\t$port\t$total\t$good\t${(good
                    .asInstanceOf[Double] / total * 100).round / 100.0}\n")
              }
              close()
            }
            context.log
              .info("Checked proxies are saved to {}", Config.proxiesChecked)
            Behaviors.same
        }
    }

  sealed trait Request

  case class ProxyPerformance(total: Int = 0, ok: Int = 0) {
    def good: ProxyPerformance = this.copy(total = total + 1, ok = ok + 1)

    def bad: ProxyPerformance = this.copy(total = total + 1)
  }

  final case class GetProxy(replyTo: ActorRef[HttpProxy]) extends Request

  final case class ProxyUseResult(
    proxy: HttpProxy,
    result: Either[String, Long]
  ) extends Request

  final case class CheckAndStoreUrl(
    replyTo: ActorRef[Boolean],
    order: Order,
    url: String
  ) extends Request

  final case class DeleteUrl(order: Order, url: String) extends Request

}
