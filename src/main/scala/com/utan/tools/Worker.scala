package com.utan.tools

import java.io.{File, PrintWriter}

import scalikejdbc._

import scalikejdbc.{ConnectionPool, AutoSession}

import scala.collection.immutable.Iterable
import scala.io.{Codec, Source}
import scala.util.Random
import scala.xml.XML

/**
 * Created by yyboost on 8/16/16.
 */
trait Worker {
  def process(info: String): Unit

  def makeIndex(table: String, index: String, unique: Boolean, columns: List[String]): String = {
    val uniqueStr = if (unique) "unique" else ""
    s"create ${uniqueStr} index ${index} on ${table}${columns.mkString("(", ",", ")")};"
  }

  def makePrimaryKey(table: String, index: String, unique: Boolean, columns: List[String]): String = {
    s"alter table ${table} add constraint ${index} primary key ${columns.mkString("(", ",", ")")};"
  }

  def writeToFile(learnFileContent: Iterable[String], path: String): Unit = {
    val writer = new PrintWriter(new File(path))
    try {
      learnFileContent.foreach(writer.println(_))
    } finally {
      writer.close()
    }
  }

  def query(sql: SQLSyntax): Map[(String, String, Boolean), List[String]] = {
    implicit val session = AutoSession
    val entities: List[IndexDesc] =
      sql"${sql}".map(IndexDesc(_)).list.apply()

    val m = entities.groupBy(e => (e.tableName, e.indexName, e.uniqueness)).map {
      case (k, l) => (k, l.map(_.columnName))
    }

    val tableCount = entities.map(_.tableName).toSet.size

    println(
      s"""
         |-----------------------------
         |�������ı�һ����${tableCount}��.
         |һ����${m.size}������.
         |-----------------------------
        """.stripMargin)
    m
  }
}

class learnWorker(config: Config) extends Worker {
  //��ǰ�����з���������.
  val notPrimaryKeyIndex =
    sqls"""select b.UNIQUENESS,
            a.index_name,
            a.table_name,
            a.column_name,
            a.COLUMN_POSITION
      from all_ind_columns a, all_indexes b
      where a.index_name = b.index_name
      and a.table_name = b.table_name
      and b.TABLE_OWNER = ${config.schema}
      and a.TABLE_OWNER = ${config.schema}
      and a.index_name not in (select c.CONSTRAINT_NAME
                              from all_constraints c
                             where c.OWNER = ${config.schema})
      order by a.table_name, a.index_name, a.column_position"""

  override def process(info: String): Unit = {
    println(info)
    val m = query(notPrimaryKeyIndex)
    val learnFileContent = for (e <- m) yield {
      e match {
        case ((t, i, u), l) =>
          makeIndex(t, i, u, l)
      }
    }
    writeToFile(learnFileContent, "./learn.sql")
  }
}

class PrimaryWorker(config: Config) extends Worker {
  //��������������Ϣ.
  val select =
    sqls"""select b.uniqueness as uniqueness,
            a.index_name as index_name,
            a.table_name as table_name,
            a.column_name as column_name,
            a.column_position as column_position
      from all_ind_columns a, all_indexes b
      where a.index_name = b.index_name
      and a.table_name = b.table_name
      and b.TABLE_OWNER = ${config.schema}
      and a.TABLE_OWNER = ${config.schema}
      order by a.table_name, a.index_name, a.column_position"""

  val existTables =
    sqls"""select table_name from all_tables a where a.OWNER = ${config.schema}"""

  //��ǰ���Ѿ��е�������������.
  val existsPrimaryKeyIndex =
    sqls"""select 'UNIQUE'as uniqueness,
       c.index_name,
       c.table_name,
       a.column_name,
       a.column_position
  from all_constraints c, all_ind_columns a
  where c.OWNER = ${config.schema}
    and c.TABLE_NAME = a.TABLE_NAME
    and c.INDEX_NAME = a.INDEX_NAME
    and c.CONSTRAINT_NAME = c.INDEX_NAME
  order by a.table_name, a.index_name, a.column_position"""

  def parseData(file: File): (String, List[String]) = {
    val x = XML.load(Source.fromFile(file)(Codec("GBK")).bufferedReader())
    ((x \ "tableName").text, (x \ "pKey").text.split(",").toList.filter(!_.isEmpty))
  }

  def allUtanToolDatasPrimaryKey: Map[String, List[String]] = {
    val f = new File(config.utPath)
    def findDataFiles(f: File, acc: Array[File]): Array[File] = {
      val files = f.listFiles
      files.filter(_.isDirectory)
        .aggregate(acc ++ files.filter(f => f.isFile && f.getName.endsWith(".data")))((l, f) => findDataFiles(f, l), _ ++ _)
    }

    val files = findDataFiles(f, Array[File]())

    files.map(parseData(_)).toMap
  }


  def findExistsIndex(table: String, columns: List[String],
                      allIndex: Map[(String, String, Boolean), List[String]]): Option[((String, String, Boolean), List[String])] = {
    allIndex.find({ case ((t, i, u), l) => t == table && l.toSet == columns.toSet })
  }

  def makeName(allIndex: Set[String]): String = {
    //SQL1363917330750
    val num = Random.nextInt(999999999)
    val name = s"SQL${num}"
    if (allIndex.contains(name)) {
      makeName(allIndex)
    } else {
      name
    }
  }

  def findExistsPrimaryIndex(table: String, v: List[String],
                             existsPrimaryIndex: Map[(String, String, Boolean), List[String]]): Boolean = {
    existsPrimaryIndex.exists({ case ((t, i, u), l) => table == t })
  }

  override def process(info: String): Unit = {
    val existsPrimaryIndex = query(existsPrimaryKeyIndex)
    val allIndex = query(select)
    val allIndexNames = allIndex.keys.map({ case (_, i, _) => i }).toSet

    implicit val session = AutoSession
    //����table�Ƿ��е��ж�.
    val allTables = sql"${existTables}".map(_.string(1)).list.apply().toSet

    val before =
      s"""
         |-----------------------
         |--����data�ļ���${allUtanToolDatasPrimaryKey.size}��
         |-----------------------
       """.stripMargin
    val contents = for (m <- allUtanToolDatasPrimaryKey) yield {
      m match {
        case (k, v) if (v.size > 0) =>
          if (!findExistsPrimaryIndex(k, v, existsPrimaryIndex)) {
            findExistsIndex(k, v, allIndex) match {
              case Some(((_, i, _), _)) =>
                s"--${k}��${v.mkString("(", ",", ")")}�Ѿ����ڳ�ͻ������${i}��ͻ"
              case None =>
                if (allTables(k))
                  makePrimaryKey(k, makeName(allIndexNames), true, v)
                else
                  s"--${k}�������ڣ�����"
            }
          } else {
            s"--${k}�Ѿ���������--"
          }
        case (k, v) => s"--${k}data�ļ�����δ����"
      }
    }
    writeToFile(before :: contents.toList, "./primary.sql")
  }
}

object Worker {
  def apply(config: Config): Worker = {
    implicit val session = AutoSession
    Class.forName("oracle.jdbc.driver.OracleDriver")
    ConnectionPool.singleton(
      s"jdbc:oracle:thin:@${config.ip}:${config.port}:${config.dbName}",
      config.user, config.passwd)

    config.mode match {
      case PrimaryCommand =>
        new PrimaryWorker(config)
      case NoPrimaryCmmand =>
        new learnWorker(config)
      case NoCmmand =>
        throw new IllegalArgumentException("noCommand")
    }
  }
}

