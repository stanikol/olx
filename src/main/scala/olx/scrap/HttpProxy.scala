package olx.scrap

case class HttpProxy(host: String, port: Int) {
  override def toString: String = s"$host:$port"
}
