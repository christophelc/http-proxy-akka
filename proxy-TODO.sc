import $ivy.{
// `org.scalaj::scalaj-http:2.3.0`,
//  `com.twitter::finatra-http:18.3.0`,
  `ch.qos.logback:logback-classic:1.2.3`,
  `org.jboss.netty:netty:3.2.0.Final`,
  `com.twitter:finagle-http:18.3.0`
}
import $file.reader
import reader.conf

import com.twitter.finagle.Service
import org.jboss.netty.handler.codec.http._
import java.net.InetSocketAddress
import com.twitter.finagle.builder.{ClientBuilder, Server, ServerBuilder}
import com.twitter.finagle.http.Http

object App {

  class RequestReceiver extends Service[HttpRequest, HttpResponse] {
    def apply(request: HttpRequest) = {

      val client: Service[HttpRequest, HttpResponse] = ClientBuilder()
              .codec(Http())
              .hosts(request.getHeader("Host") + ":80")
              .hostConnectionLimit(1)
              .build()

      client.apply(request)

    }
  }

  def main(args: Array[String]) {
    val recv = new RequestReceiver

    val myService: Service[HttpRequest, HttpResponse] = recv

    val server: Server = ServerBuilder()
            .codec(Http())
            .bindTo(new InetSocketAddress(8080))
            .name("httpserver")
            .build(myService)
  }
}
