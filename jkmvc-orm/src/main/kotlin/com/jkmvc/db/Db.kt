package com.jkmvc.db

import com.jkmvc.common.*
import java.io.Closeable
import java.sql.Connection
import java.sql.ResultSet
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.reflect.KClass

/**
 * 封装db操作
 *   ThreadLocal保证的线程安全, 每个请求都创建新的db对象
 *
 * @author shijianhang
 * @date 2016-10-8 下午8:02:47
 */
class Db(public override val name:String /* 标识 */):IDb, IDbMeta by DbMeta.get(name) {

    companion object:Closeable {

        /**
         * 公共配置
         */
        public val commonConfig: Config = Config.instance("database._common", "yaml")

        /**
         * 是否调试
         */
        public val debug:Boolean = commonConfig.getBoolean("debug", false)!!;

        /**
         * 数据源工厂
         */
        public val dataSourceFactory:IDataSourceFactory by lazy{
            val clazz:String = commonConfig["dataSourceFactory"]!!
            Class.forName(clazz).newInstance() as IDataSourceFactory
        }

        /**
         * 线程安全的db缓存
         *    每个线程有多个db, 一个名称各一个db对象
         *    每个请求都创建新的db对象
         */
        protected val dbs:ThreadLocal<HashMap<String, Db>> = ThreadLocal.withInitial {
            HashMap<String, Db>();
        }

        /**
         * 获得db(线程安全)
         *    获得当前线程下的指定名字的db, 没有则创建新db
         *    每个请求都创建新的db对象
         * @param name
         * @return
         */
        public fun instance(name:String = "default"):Db{
            return dbs.get().getOrPut(name){
                Db(name);
            }
        }

        /**
         * 获得当前线程的所有db
         */
        public fun all(): Collection<Db> {
            return dbs.get().values
        }

        /**
         * 关闭当前线程的所有db
         *    每个请求结束后, 要关闭该请求创建的db对象
         *    谁使用，谁关闭
         */
        public override fun close():Unit{
            for((name, db) in dbs.get())
                db.close()
        }

        /**
         * 在操作之后关闭db
         * @param logging 是否打印异常日志，在定时任务中，不会自动打印异常，因此手动打印一下
         * @param statement db操作过程
         */
        public inline fun closeAfter(logging: Boolean = true, statement: () -> Unit):Unit{
            try {
                statement()
            }catch (e: Exception){
                if(logging)
                    dbLogger.error("db操作出错：${e.message}", e)
                throw e
            }finally{
                close()
            }
        }
    }

    /**
     * 是否强制使用主库
     */
    public var forceMaster: Boolean = false;

    /**
     * 数据库配置
     */
    protected val config: Config = Config.instance("database.$name", "yaml")

    /**
     * 从库数量
     */
    protected val slaveNum: Int by lazy{
        val slaves = config.getList("slaves")
        if(slaves == null) 0 else slaves.size
    }

    /**
     * 连接使用情况
     *   用2位bit来记录是否用到主从连接
     *   主库第0位, 从库第1位
     */
    protected var connUsed: Int = 0

    /**
     * 主库连接
     */
    protected val masterConn: Connection by lazy{
        //获得主库数据源
        val dataSource = dataSourceFactory.getDataSource("$name.master");
        // 记录用到主库
        connUsed = connUsed or 1
        // 新建连接
        dataSource.connection
    }

    /**
     * 随机一个从库连接
     */
    protected val slaveConn: Connection by lazy {
        if (slaveNum == 0) { // 无从库, 直接用主库
            masterConn
        } else{ // 随机选个从库
            val i = ThreadLocalRandom.current().nextInt(slaveNum)
            //获得从库数据源
            val dataSource = dataSourceFactory.getDataSource("$name.slaves.$i");
            // 记录用到从库
            connUsed = connUsed or 2
            // 新建连接
            dataSource.connection
        }
    }

    /**
     * 获得通用的连接
     *   如果有事务+强制, 就使用主库
     *   否则使用从库
     */
    public val conn: Connection
        get(){
            // 不能使用`if(isInTransaction()) masterConn else slaveConn`, 否则会创建2个连接
            if(isInTransaction() || forceMaster)
                return masterConn

            return slaveConn
        }

    /**
     * 当前事务的嵌套层级
     */
    protected var transDepth:Int = 0;

    /**
     * 标记当前事务是否回滚
     */
    protected var rollbacked = false;

    /**
     * 执行事务
     * @param statement db操作过程
     * @return
     */
    public override fun <T> transaction(statement: () -> T):T{
        try{
            begin(); // 开启事务
            val result:T = statement(); // 执行sql
            commit(); // 提交事务
            return result; // 返回结果
        }catch(e:Exception){
            rollback(); // 回顾
            throw e;
        }
    }

    /**
     * 是否在事务中
     * @return
     */
    public override fun isInTransaction(): Boolean {
        return transDepth > 0;
    }

    /**
     * 执行更新
     * @param sql
     * @param params
     * @param generatedColumn 返回的自动生成的主键名
     * @return
     */
    public override fun execute(sql: String, params: List<Any?>, generatedColumn:String?): Int {
        try{
            return masterConn.execute(sql, params, generatedColumn);
        }catch (e:Exception){
            dbLogger.error("出错[${e.message}] sql: " + previewSql(sql, params))
            throw  e
        }
    }

    /**
     * 批量更新：每次更新sql参数不一样
     *
     * @param sql
     * @param paramses 多次处理的参数的汇总，一次处理取 paramSize 个参数，必须保证他的大小是 paramSize 的整数倍
     * @param paramSize 一次处理的参数个数
     * @return
     */
    public override fun batchExecute(sql: String, paramses: List<Any?>, paramSize:Int): IntArray {
        try{
            return masterConn.batchExecute(sql, paramses, paramSize)
        }catch (e:Exception){
            dbLogger.error("出错[${e.message}], sql=$sql, params=$paramses ")
            throw  e
        }
    }

    /**
     * 查询多行
     * @param sql
     * @param params
     * @param action 转换结果的函数
     * @return
     */
    public override fun <T> queryResult(sql: String, params: List<Any?>, action: (ResultSet) -> T): T {
        try{
            return conn.queryResult(sql, params, action)
        }catch (e:Exception){
            dbLogger.error("出错[${e.message}] sql: " + previewSql(sql, params))
            throw  e
        }
    }

    /**
     * 查询多行
     * @param sql
     * @param params
     * @param transform 转换结果的函数
     * @return
     */
    public override fun <T> queryRows(sql: String, params: List<Any?>, transform: (MutableMap<String, Any?>) -> T): List<T> {
        try{
            return conn.queryRows(sql, params, transform);
        }catch (e:Exception){
            dbLogger.error("出错[${e.message}] sql: " + previewSql(sql, params))
            throw  e
        }
    }

    /**
     * 查询一行(多列)
     * @param sql
     * @param params
     * @param transform 转换结果的函数
     * @return
     */
    public override fun <T> queryRow(sql: String, params: List<Any?>, transform: (MutableMap<String, Any?>) -> T): T? {
        try{
            return conn.queryRow(sql, params, transform);
        }catch (e:Exception){
            dbLogger.error("出错[${e.message}] sql: " + previewSql(sql, params))
            throw  e
        }
    }

    /**
     * 查询一列(多行)
     * @param sql
     * @param params
     * @param clazz 值类型
     * @return
     */
    public override fun <T:Any> queryColumn(sql: String, params: List<Any?>, clazz: KClass<T>?): List<T?> {
        try{
            return conn.queryColumn(sql, params);
        }catch (e:Exception){
            dbLogger.error("出错[${e.message}] sql: " + previewSql(sql, params))
            throw  e
        }
    }

    /**
     * 查询一行一列
     * @param sql
     * @param params
     * @param clazz 值类型
     * @return
     */
    public override fun <T:Any> queryCell(sql: String, params: List<Any?>, clazz: KClass<T>?): Pair<Boolean, T?> {
        try{
            return conn.queryCell(sql, params, clazz);
        }catch (e:Exception){
            dbLogger.error("出错[${e.message}] sql: " + previewSql(sql, params))
            throw  e
        }
    }

    /**
     * 开启事务
     */
    public override fun begin():Unit{
        if(transDepth++ === 0)
            masterConn.autoCommit = false; // 禁止自动提交事务
    }

    /**
     * 提交事务
     */
    public override fun commit():Boolean{
        // 未开启事务
        if (transDepth <= 0)
            return false;

        // 无嵌套事务
        if (--transDepth === 0)
        {
            // 回滚 or 提交事务: 回滚的话,返回false
            if(rollbacked)
                masterConn.rollback();
            else
                masterConn.commit()
            val result = rollbacked;
            rollbacked = false; // 清空回滚标记
            return result;
        }

        // 有嵌套事务
        return true;
    }

    /**
     * 回滚事务
     */
    public override fun rollback():Boolean{
        // 未开启事务
        if (transDepth <= 0)
            return false;

        // 无嵌套事务
        if (--transDepth === 0)
        {
            rollbacked = false; // 清空回滚标记
            masterConn.rollback(); // 回滚事务
        }

        // 有嵌套事务
        rollbacked = true; // 标记回滚
        return true;
    }

    /**
     * 关闭
     */
    public override fun close():Unit{
        // 删除当前线程的引用
        if(dbs.get().remove(name) == null)
            throw DbException("当前线程并没有[${name}]连接，无法关闭")

        // 关闭连接
        if(connUsed and 1 > 0)
            masterConn.close()
        if(connUsed and 2 > 0)
            slaveConn.close()
    }

    /**
     * 转义多个表名
     *
     * @param tables 表名集合，其元素可以是String, 也可以是DbAlias
     * @param with_brackets 当拼接数组时, 是否用()包裹
     * @return
     */
    public override fun quoteTables(tables:Collection<CharSequence>, with_brackets:Boolean):String
    {
        // 遍历多个表转义
        return tables.joinToString(", ", if(with_brackets) "(" else "", if(with_brackets) ")" else ""){
            // 单个表转义
            quoteTable(it)
        }
    }

    /**
     * 转义表名
     *   mysql为`table`
     *   oracle为"table"
     *   sql server为"table" [table]
     *
     * @param table 表名或别名 DbAlias
     * @return
     */
    public override fun quoteTable(table:CharSequence):String
    {
        return if(table is DbExpr) // 表与别名之间不加 as，虽然mysql可识别，但oracle不能识别
                    table.quote(identifierQuoteString, " ")
                else
                    "$identifierQuoteString$table$identifierQuoteString";
    }

    /**
     * 转义多个字段名
     *
     * @param columns 表名集合，其元素可以是String, 也可以是DbAlias
     * @param with_brackets 当拼接数组时, 是否用()包裹
     * @return
     */
    public override fun quoteColumns(columns:Collection<CharSequence>, with_brackets:Boolean):String
    {
        // 遍历多个字段转义
        return columns.joinToString(", ", if(with_brackets) "(" else "", if(with_brackets) ")" else "") {
            // 单个字段转义
            quoteColumn(it)
        }
    }

    /**
     * 转义字段名
     *   mysql为`column`
     *   oracle为"column"
     *   sql server为"column" [column]
     *
     * @param column 字段名, 可能是别名 DbAlias
     * @return
     */
    public override fun quoteColumn(column:CharSequence):String
    {
        var table = "";
        var col: String; // 字段
        var alias:String? = null; // 别名
        var colQuoting = true // 是否转义字段
        if(column is DbExpr){
            col = column.exp.toString()
            alias = column.alias
            colQuoting = column.expQuoting
        }else{
            col = column.toString()
        }

        // 转义字段 + 非函数表达式
        if (colQuoting && "^\\w[\\w\\d_\\.\\*]*".toRegex().matches(column))
        {
            // 表名
            if(column.contains('.')){
                var arr = column.split('.');
                table = "$identifierQuoteString${arr[0]}$identifierQuoteString.";
                col = arr[1]
            }

            // 字段名
            if(col == "*" || (dbType == DbType.Oracle && col == "rownum")) { // * 或 oracle的rownum 字段不转义
                //...
            }else{ // 其他字段转义
                col = "$identifierQuoteString$col$identifierQuoteString";
            }
        }

        // 字段别名
        if(alias == null)
            return "$table$col";

        return "$table$col AS $identifierQuoteString$alias$identifierQuoteString"; // 转义
    }

    /**
     * 转义单个值
     *
     * @param value 字段值, 可以是值数组
     * @return
     */
    public override fun quoteSingleValue(value: Any?): String {
        // null => "NULL"
        if (value == null)
            return "NULL";

        // bool => int
        if (value is Boolean)
            return if (value) "1" else "0";

        // int/float
        if (value is Number)
            return value.toString();

        // string
        if (value is String)
            return "'$value'" // oracle字符串必须是''包含

        // date
        if (value is Date)
            return quoteDate(value)

        return value.toString()
    }

    /**
     * 转移日期值
     * @value value 参数
     * @return
     */
    public fun quoteDate(value: Date): String {
        val value = "'${value.format()}'"
        return if(dbType == DbType.Oracle)
                    "to_date($value,'yyyy-mm-dd hh24:mi:ss')"
                else
                    value
    }

    /**
     * 预览sql
     * @param sql
     * @param params sql参数
     * @return
     */
    public override fun previewSql(sql: String, params: List<Any?>): String {
        // 1 无参数
        if(params.isEmpty())
            return sql

        // 2 有参数：替换参数
        // 正则替换
        /*var i = 0 // 迭代索引
        return sql.replace("\\?".toRegex()) { matches: MatchResult ->
            quote(params[i++]) // 转义参数值
        }*/

        // 格式化字符串
        val ps = params.mapToArray { quote(it) }
        return sql.replace("?", "%s").format(*ps)
    }
}
