import $ivy.{
  `com.fasterxml.jackson.module::jackson-module-scala:2.9.4`,
  `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.9.4`,
  `com.fasterxml.jackson.core:jackson-databind:2.9.4`,
  `com.fasterxml.jackson.core:jackson-core:2.9.4`
}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import scala.collection.immutable.HashMap
import scala.collection.JavaConverters._
import java.io.File
import scala.io.Source

// uses Jackson YAML to parsing, relies on SnakeYAML for low level handling
val mapper: ObjectMapper = new ObjectMapper(new YAMLFactory())

// provides all of the Scala goodiness
mapper.registerModule(DefaultScalaModule)

/*
 * In the conf.yaml file, the name should be set to the name of the directory (change this later)
 * example: conf/dev/conf.yaml 
 *  name: dev
 */
object Conf {
  def apply(name: String,
    host: String, 
    port: Int,
    protocole: String,
    baseurl: String, 
    _authentication: Map[String, String], 
    header: Map[String, String],
    params: Map[String, String]): Conf = {
 
    val authentication = _authentication.map { 
      case (k,v) => k -> {
        k match {
          case "bearer" => resolve(name, v) 
          case _  => v
        }
      }
    }.toMap

    val baseurl2 =  if (baseurl.length > 0 && baseurl(0) == '/')
        baseurl.substring(1)
      else
        baseurl

    new Conf(name, host, port, protocole, baseurl, authentication, header, params)
  }

  def apply(conf: Conf): Conf = {
    apply(conf.name, conf.host, conf.port, conf.protocole, conf.baseurl, conf.authentication, conf.header, conf.params)
  }

 /*
  * @param data
  * @return if data is like $name, return the content of the file under conf with the filename 'name'
  *         else data itself.
  */
  private def resolve(name: String, data: String): String = {
   if (data == null || data.length == 0) {
     return ""
   }
   if (data(0) == '$') 
     Source.fromFile("conf/" + name + "/" + data.substring(1)).getLines.toList.mkString("\r\n")
   else
     data
  }
}

class Conf(val name: String,
  val host: String,
  val port: Int,
  private val protocole: String, 
  private val baseurl: String, 
  val authentication: Map[String, String], 
  val header: Map[String, String],
  val params: Map[String, String]) {

  // override def protocole...
  def getProtocole(): String = if (protocole == null) "http" else protocole
  private def flatten(l: List[Any]) : List[Any] = l.flatMap {
    case ls: List[_] => flatten(ls)
    case el => List(el)
  }

  // suppress first /
  def getBaseurl: String = {
    if (this.baseurl == null || this.baseurl.length ==0) return ""
    if (this.baseurl(0) != "/") baseurl
    else
      this.baseurl.substring(1)
  }

  override def toString = flatten(List(
    "*** version ***",
    s"name \t $name",
    "*** baseurl ***",
    s"protocole \t" + getProtocole(),
    s"host \t $host",
    s"port \t $port",
    s"baseurl \t $baseurl",
    "*** authentication ***",
    authentication.map { case (k, v) => k + "\t" + v }.toList,
    "*** header ***",
    header.map { case (k, v) => k + "\t" + v }.toList,
    " ***********"
  )).mkString("\r\n")

}

import ammonite.ops._
val dirs = ls! pwd/"conf" |? (_.fileType == FileType.Dir)
val root = pwd
var hConf: Map[String, Conf] = dirs.map( dir => {
  val name = dir.name
  Map(name -> Conf(mapper.readValue(new File(s"conf/$name/conf.yaml"), classOf[Conf])))
}).flatten.toMap
//println(hConf)
