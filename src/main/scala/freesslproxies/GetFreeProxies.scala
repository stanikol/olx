package freesslproxies:

  import cats.effect.IO
  import org.olx.parser.StrToCssSelector
  import org.openqa.selenium.remote.RemoteWebDriver
  import org.olx.parser.WD
  import wvlet.log.LogSupport

  trait GetFreeProxies extends LogSupport:
    val DEFAULT_PROXY_FILE = "free-proxies.tsv"
    def getFreeProxies: IO[List[FreeProxy]] =
      WD.makeWebDriver(proxy = None).use { (wd: RemoteWebDriver) =>
        IO.blocking {
            wd.get("https://sslproxies.org/")
            FreeProxy(wd.findElement(".fpl-list"))
          }.flatten
          .map(_.filter(p => p.https == "yes" &&
            p.anonymity == "elite proxy"))
      }
  end GetFreeProxies
end freesslproxies

