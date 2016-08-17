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
      .text("���ݿ�ip��������Ĭ��127.0.0.1")

    opt[Int]('o', "port")
      .valueName("<port:1521>")
      .action((x, c) => c.copy(port = x))
      .text("���ݿ�˿�,������Ĭ��1521")

    opt[String]('u', "user")
      .required().valueName("<user>")
      .action((x, c) => c.copy(user = x))
      .text("���ݿ��û�����")

    opt[String]('p', "password")
      .required().valueName("<password>")
      .action((x, c) => c.copy(passwd = x))
      .text("���ݿ�������")

    opt[String]('d', "dbName")
      .required().valueName("<db-name>")
      .action((x, c) => c.copy(dbName = x))
      .text("���ݿ��������")

    opt[String]('s', "schema")
      .valueName("<schema>")
      .action((x, c) => c.copy(schema = x))
      .text("��������ߣ�����˵��schema��Ĭ��UTAN")

    cmd("primary").action((_, c) => c.copy(mode = PrimaryCommand)).
      text("���������ߣ������ݿ������������.").children(
      opt[String]('c',"path").valueName("<utanTool's path>")
        .action((x, c) => c.copy(utPath = x))
        .text("utanTool����·��.").required())

    cmd("nop-learn").action((_, c) => c.copy(mode = NoPrimaryCmmand)).text("��������ѧϰ����������.")

    checkConfig(c => if (c.mode == NoCmmand) failure("����������") else success)
  }

  def main(args: Array[String]) {
    parser.parse(args, Config()) match {
      case Some(config) =>
        Worker(config).process("��ʼѧϰ������ķ���������")
      case None =>
        System.exit(-1)
    }
  }
}
