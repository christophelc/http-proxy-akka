import $ivy.{
  `com.typesafe.akka::akka-actor:2.5.1`,
  `com.typesafe.akka::akka-stream:2.5.11`,
  `com.typesafe.akka::akka-http:10.1.1`
}
import $file.reader
import reader.hConf

import javax.net.ssl._
import java.security.cert.X509Certificate
object TrustAll extends X509TrustManager {
  val getAcceptedIssuers = null

  def checkClientTrusted(x509Certificates: Array[X509Certificate], s: String) = {}

  def checkServerTrusted(x509Certificates: Array[X509Certificate], s: String) = {}
}

// Verifies all host names by simply returning true.
object VerifiesAllHostNames extends HostnameVerifier {
  def verify(s: String, sslSession: SSLSession) = true
}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{HttpRequest, Uri}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}

// show entity content
import akka.util.ByteString
import scala.concurrent.Future
import scala.concurrent.duration._

// ssl with no check =>
// https://github.com/mthaler/akka-http-test
import akka.http.scaladsl.{ ConnectionContext, HttpsConnectionContext }
import com.typesafe.sslconfig.akka.AkkaSSLConfig
import com.typesafe.config.ConfigFactory

import javax.net.ssl.{SSLSession, HostnameVerifier}
object Security {
  class AllHostsValid extends HostnameVerifier {
    override def verify(s: String, sslSession: SSLSession): Boolean = true
  }
}

object HttpProxy extends App {
//        val configSsl = ConfigFactory.parseURL(getClass.getResource("httpsclient.conf"))
	//implicit val system = ActorSystem("Proxy", configSsl)
	implicit val system = ActorSystem("Proxy")
  	implicit val materializer = ActorMaterializer()
        implicit val ec = system.dispatcher
        val configSsl = AkkaSSLConfig().mapSettings { s =>
          s.withHostnameVerifierClass(classOf[Security.AllHostsValid])
          s
        }

        private def getToken()(implicit conf: reader.Conf) : String = conf.authentication("bearer")
        private def getUrl(url: String)(implicit conf: reader.Conf) : String = conf.getBaseurl + url

        private val trustfulSslContext: SSLContext = {
          object NoCheckX509TrustManager extends X509TrustManager {
            override def checkClientTrusted(chain: Array[X509Certificate], authType: String) = ()
            override def checkServerTrusted(chain: Array[X509Certificate], authType: String) = ()
            override def getAcceptedIssuers = Array[X509Certificate]()
          }
          val context = SSLContext.getInstance("TLS")
          context.init(Array[KeyManager](), Array(NoCheckX509TrustManager), null)
          context
        }
        val trustfulClientContext: HttpsConnectionContext =
          ConnectionContext.https(trustfulSslContext)
        val allHostsValid = new HostnameVerifier() {
          override def verify(s: String, sslSession: SSLSession): Boolean = true
        }
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid)

        private def readJsonOrPostDataFromRequest(request: HttpRequest) : Future[String] = {
          val timeout=100.millis
          val bs: Future[ByteString] = request.entity.toStrict(timeout).map { _.data }
          val fData: Future[String] = bs.map(_.utf8String)          
          fData
        }

        private def show(request: HttpRequest, protocole: String, host: String, port: Int) : Future[String] = {
          val rslt = request.method.name + " " +
          protocole + 
          //"://" + request.uri.authority.host.address + 
          "://" + host +
          ":" + port + 
          request.uri.path.toString + 
          " params:[" + (request.uri.query().toMap.map { case (k,v) => k+": "+v}).toList.mkString(",")+ "]" +
          " content-type: " + request.entity.getContentType + " - "

          request.entity.getContentType.toString match {
            case "application/json" | "application/text" =>
                readJsonOrPostDataFromRequest(request).map(data => rslt + " data:[" + data + "]")
              case _ =>
                Future { rslt + " data:..." }
          }
        }
        println("***********")
        println("Ready !")
        println("***********")

        val proxy = Route { context =>
          val profile = context.request.uri.query().toMap.getOrElse("profile", "default")
          implicit val config: reader.Conf = hConf(profile)
          //println(s"profile \t $profile")
          var req = HttpRequest(method = context.request.method,
           Uri.from(path = "/").withHost(config.host),
           entity = context.request.entity)
          req = HttpRequest(uri=Uri.from(path="/")).
              withHeaders(context.request.headers.filter(v => !List("Timeout-Access", "User-Agent", "Host").contains(v.name)))
          if (getToken.length > 0) {
            val auth = Authorization(OAuth2BearerToken(getToken))
            req = req.addHeader(auth)
          }
          config.authentication.filterKeys(_ != "bearer").map { case (k,v) => req = req.addHeader(RawHeader(k, v)) }
          config.header.map { case (k,v) => req = req.addHeader(RawHeader(k, v)) }
          // TODO: log to console
          show(req, config.getProtocole(), config.host, config.port).map(s => println(s))
          val flow = config.getProtocole() match {
            case "https" =>
              Http(system).outgoingConnectionHttps(config.host, config.port, connectionContext = trustfulClientContext)
            case _ =>
              Http(system).outgoingConnection(config.host, config.port)
          }
          val handler = Source.single(req)
            .via(flow)
            .runWith(Sink.head)
            .flatMap(context.complete(_))
            handler
        }
        val proxyTest = Route { context =>
          var req = HttpRequest(method = context.request.method, 
            uri = Uri.from(path = "/"),
            entity = context.request.entity,
            headers = context.request.headers.filter(v => !List("Timeout-Access", "User-Agent", "Host").contains(v.name)) ) 
          show(req, "http", "www.google.com", 80).map(s => println(s))
          println(req)
          val flow = Http().outgoingConnection("www.google.com")
          val handler = Source.single(HttpRequest(uri="/"))
            .via(flow)
            .runWith(Sink.head)
            .flatMap(context.complete(_))
            handler
        }
        val bindingHttp = Http(system).bindAndHandle(handler = proxy, interface = "localhost", port = 1080)
        val bindingHttpTest = Http(system).bindAndHandle(handler = proxyTest, interface = "localhost", port = 1081)
}
HttpProxy.main(Array[String]())
while (true) {
  Thread.sleep(100)
}
