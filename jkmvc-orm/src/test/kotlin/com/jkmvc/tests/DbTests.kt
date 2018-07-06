package com.jkmvc.tests

import com.jkmvc.db.*
import org.junit.Test
import java.io.File


class DbTests{

    val db: Db = Db.instance()

    val id: Long by lazy {
        val (hasNext, minId) = db.queryCell("select id from user order by id limit 1" /*sql*/)
        println("随便选个id: " + minId)
        minId as Long;
    }

    @Test
    fun testDbType(){
        /*for(type in DbType.values())
            println(type)
        print("current: " + db.dbType)*/
        println(db.schema)
    }

    @Test
    fun testDbDate(){
        val (hasNext, date) = Db.instance().queryCell("SELECT  LAST_LOGIN_TIME FROM RC_ACCOUNTS WHERE ACCOUNT_CODE = 'tann771x@nng.gx.csg.cn'")
        if(hasNext)
            println(date)
    }

    @Test
    fun testColumn2Prop(){
        println(db.column2Prop("ZDZCBH_"))
    }

    @Test
    fun testConnection(){
        val file = "test." + db.dbType.toString().toLowerCase() + ".sql"
        val cld = Thread.currentThread().contextClassLoader
        val res = cld.getResource(file)
        val sql = res.readText();

        db.execute(sql);
        println("创建表")
    }

    @Test
    fun testInsert(){
        val id = db.execute("insert into user(name, age) values(?, ?)" /*sql*/, listOf("shi", 1)/*参数*/, "id"/*自增主键字段名，作为返回值*/) // 返回自增主键值
        println("插入user表：" + id)
    }

    @Test
    fun testFind(){
        val record = db.queryRow("select * from user limit 1" /*sql*/, null /*参数*/, Map::class.recordTranformer /*转换结果的函数*/) // 返回 Map 类型的一行数据
        println("查询user表：" + record)
    }

    @Test
    fun testFindAll(){
        val records = db.queryRows("select * from user limit 10" /*sql*/, null /*参数*/, Map::class.recordTranformer /*转换结果的函数*/) // 返回 Map 类型的多行数据
        println("查询user表：" + records)
    }

    @Test
    fun testCount(){
        val (hasNext, count) = db.queryCell("select count(1) from user" /*sql*/)
        println("统计user表：" + count)
    }

    @Test
    fun testUpdate(){
        val f = db.execute("update user set name = ?, age = ? where id =?" /*sql*/, listOf("shi", 1, id) /*参数*/) // 返回更新行数
        println("更新user表：" + f)
    }

    @Test
    fun testDelete(){
        val f = db.execute("delete from user where id =?" /*sql*/, listOf(id) /*参数*/) // 返回更新行数
        println("删除user表：" + f)
    }
}