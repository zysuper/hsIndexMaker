package com.utan.tools

import java.io.{File, PrintWriter}

import scalikejdbc._
import scalikejdbc.interpolation.SQLSyntax

import scala.collection.immutable.Iterable
import scala.io.Source


/**
 * Created by yyboost on 8/16/16.
 */
abstract sealed class Command

case object PrimaryCommand extends Command

case object NoPrimaryCmmand extends Command

case object NoCmmand extends Command


case class IndexDesc(uniqueness: Boolean, indexName: String, tableName: String, columnName: String, columnPosition: Int)

object IndexDesc extends SQLSyntaxSupport[IndexDesc] {
  override val tableName = "members"

  def apply(rs: WrappedResultSet) = new IndexDesc(
    rs.string("uniqueness") == "UNIQUE", rs.string("index_name"),
    rs.string("table_name"), rs.string("column_name"),
    rs.int("column_position"))
}

case class Config(ip: String = "127.0.0.1",
                  port: Int = 1521,
                  user: String = "",
                  passwd: String = "",
                  dbName: String = "",
                  schema: String = "UTAN",
                  mode: Command = NoCmmand,
                  utPath: String = "")

object Main {
  val parser = new scopt.OptionParser[Config]("java -jar indexMaker-1.0.jar") {
    head("indexMaker", "1.0")

    opt[String]('i', "ip")
      .valueName("<ip:127.0.0.1>")
      .action((x, c) => c.copy(ip = x))
      .text("数据库ip，不输入默认127.0.0.1")

    opt[Int]('o', "port")
      .valueName("<port:1521>")
      .action((x, c) => c.copy(port = x))
      .text("数据库端口,不输入默认1521")

    opt[String]('u', "user")
      .required().valueName("<user>")
      .action((x, c) => c.copy(user = x))
      .text("数据库用户名罗")

    opt[String]('p', "password")
      .required().valueName("<password>")
      .action((x, c) => c.copy(passwd = x))
      .text("数据库密码罗")

    opt[String]('d', "dbName")
      .required().valueName("<db-name>")
      .action((x, c) => c.copy(dbName = x))
      .text("数据库的名称罗")

    opt[String]('s', "schema")
      .valueName("<schema>")
      .action((x, c) => c.copy(schema = x))
      .text("表的所有者，或者说是schema，默认UTAN")

    cmd("primary").action((_, c) => c.copy(mode = PrimaryCommand)).
      text("检查参数工具，查数据库主键建立情况.").children(
      opt[String]('c',"path").valueName("<utanTool's path>")
        .action((x, c) => c.copy(utPath = x))
        .text("utanTool参数路径.").required())

    cmd("nop-learn").action((_, c) => c.copy(mode = NoPrimaryCmmand)).text("从其他库学习非主键索引.")

    checkConfig(c => if (c.mode == NoCmmand) failure("请输入命令") else success)
  }

  def main(args: Array[String]) {
    parser.parse(args, Config()) match {
      case Some(config) =>
        Worker(config).process("开始学习其他库的非主键索引")
      case None =>
        System.exit(-1)
    }
  }
}
